## 41. Gabung Sekte (Bergabung dengan Sekte Lain)

Permintaan pengguna (2026-10-23, bahasa Indonesia, mengikat): "Nanti ada sekte lain, kan? Bisakah aku juga BERGABUNG ke
sekte mereka? Kedengarannya bagus, rencanakan dengan detail. Jadi aku bisa mendirikan sekte sendiri ATAU bergabung ke
sekte, dan sekte-sekte itu mengikuti pengaturan, dengan berbagai tingkat/kelas sekte."

Bagian ini adalah kontrak implementasi lengkap untuk **tujuh modul** (41A, 41B, 41C, 41D, 41W, 41U, 41V; 41.14). Semua
nama kelas, field, metode, id, kunci lang, perintah dan angka di bawah adalah nama yang **harus** dipakai. Yang ditandai
"(verifikasi)" sudah dicek polanya di kode tetapi nama persisnya wajib dicocokkan lagi dengan kode mod atau
`butterbot-fabric/reference/mcsrc` sebelum dipakai. Semua angka adalah desain game; baris Sumber hanya menjelaskan ilham.
Bagian ini berdiri sendiri di `docs/rancangan/41-gabung-sekte.md`; `docs/DESIGN.md` dan `docs/PANDUAN.md` hanya disunting
oleh agen finish 41 (satu baris rujukan + bab panduan).

Ringkasnya: sekte NPC yang sudah ada (DESIGN 36.2.9, satu daftar T12, kompleks 38.C.3, istana awan 37.7.6) menjadi
**perguruan yang bisa dimasuki pemain**. Pemain menemukan sekte, melamar, menempuh ujian masuk sesuai corak sekte,
bersumpah, mendapat Shifu bernama, naik pangkat dari Murid Pelayan sampai Ketua, mengumpulkan Poin Kontribusi pribadi,
belanja di Paviliun Jasa, tinggal di gua kediaman, berperang untuk sektenya, lalu bisa pamit, diusir atau membelot, dan
bisa menyatukan jalannya dengan sekte sendiri (cabang, tetua tamu, suksesi, peleburan). Kelas sekte (Kecil sampai Abadi)
mengikuti tingkat 1-9 yang sudah ada dan pengaturan dunia.

---------------------------------------------------------------------------------------------------------------------

### 41.0 Ruang lingkup dan aturan mengikat (tambahan atas DESIGN 0, 36.0, 37.0, 38.0, 39.0, 40.0)

1. **Blok.** Saat permainan berjalan blok hanya berubah lewat `kehancuran/WorldBreaker` dan
   `sect/build/engine/BuildPlacer`. 41 **tidak menambah jalur ketiga** dan tidak memakai keduanya untuk kompleks NPC.
   Semua bangunan baru (Paviliun Jasa, deret gua kediaman, Balai Hukum, Gua Renungan, altar sumpah, papan misi) lahir
   **hanya dari world generation di chunk baru** lewat generator yang sudah ada (`tools/gen_tempat_kompleks.py`,
   `tools/gen_sekte_gerbang.py`, keduanya memakai `tools/nbt.py` + `tools/structgen.py` + `tools/tempat_lib.py`). Pintu
   Gua Renungan, dinding labirin ujian formasi, tiang ujian, boneka perunggu, panji kelas, kubah formasi sekte: **entitas
   atau gambar klien** (pola `reruntuhan/ColliderEntity`, `KotaPintuEntity`, `DisplayKey`). Tidak pernah `setBlock`,
   `FallingBlockEntity`, `Explosion`, `LightningBolt`, `FireworkRocketEntity`, cuaca, waktu atau kecepatan tick.
2. **Hanya chunk baru; dunia baru.** Perintah uji mencari (locate) lalu teleport; tidak ada `/place`. Pengguna selalu
   membuat dunia baru (memori proyek "No save compat"): **tidak ada** kode kompatibilitas simpanan, migrasi atau penjaga
   dunia lama. Semua data 41 lahir bersih di kunci baru; desain memilih penyatuan sistem, bukan shim. NBT kompleks dan
   gerbang boleh diubah (dibuat ulang generator) karena dunia lama tidak dipertahankan.
3. **Fitur lama tidak dikunci.** Mendirikan sekte sendiri (DESIGN 20/21/36/37) tetap jalan persis seperti sekarang bagi
   pemain yang tidak pernah bergabung. 41 hanya menambah jalan kedua dan aturan di antara keduanya (41.3, 41.8).
4. **Ekonomi tunggal (38.M.0, PANDUAN 67).** Satu kurs: Batu Roh Rendah/Menengah/Tinggi/Agung = 1/9/81/729 NB
   (`NilaiBatu.VALUE`), **1 PK = 2 NB** (`Harga.NB_PER_PK`). Poin Kontribusi pribadi di sekte NPC **adalah PK yang sama**
   (satuan sama; harga Paviliun memakai harga yang sudah ada: `Contribution.Ware`, `TechniqueGrade.pavilionPrice`). PK
   tidak pernah ditukar langsung dengan NB atau batu roh (dua arah); batu roh tidak bisa disumbangkan untuk PK pribadi
   (hanya untuk kas sekte, 41.6.2). Tidak ada cetak uang: jatah mingguan berupa PK + pil, bukan batu roh.
5. **Tidak ada yang mati karena sistem baru (36.0.3).** Tokoh bernama sekte NPC (Shifu, tetua, senior, saingan) tidak
   pernah mati: kalah = "Terluka", tidak muncul selama `luka` hari. Duel ujian, turnamen, duel juara dan pemburu pembelot
   memakai aturan Duel Pedang 35 (berhenti 10 % nyawa) bila lawannya pemain atau tokoh. Murid pemain tidak pernah mati karena
   41. Pemain bisa mati hanya oleh musuh nyata (gelombang serbuan, binatang), seperti biasa.
6. **Satu pintu sibuk (T3).** Setiap peristiwa 41 yang menimpa pemain (ujian masuk, panggilan perang, serbuan kompleks,
   pemburu pembelot, kurungan, upacara) memanggil `dev.kultivator.util.Sibuk.why(player)` dan menunda `Sibuk.RETRY_TICKS`
   (200) bila sibuk. Keadaan 41 didaftarkan `Sibuk.register`: `gabung.ujian`, `gabung.sumpah`, `gabung.kurungan`,
   `gabung.perang`, `gabung.suksesi`; musuh 41 lewat `Sibuk.registerEnemy("gabung.pemburu", ...)` dan
   `Sibuk.enemyTaken(player, "gabung")` (ikut batas `DAILY_ENEMY_CAP` 3).
7. **Laptop lemah.** Server: tidak ada tick per anggota/per tokoh; semua diperbarui malas per hari dunia atau saat pemain
   berinteraksi; tubuh hanya bila pemain ≤ 64 blok (41.12). Klien: semua efek patuh `EffectQuality`
   (Minimal/Normal/Maksimal), `EpicBudget`, LOD jarak, tanpa post chain, aman Sodium/Iris (tipe render vanilla, tidak
   menggambar saat `ShaderCompat.shadowPass()`).
8. **Klien/server terpisah; registri penuh (K1).** Kode server tidak menyentuh kelas klien; ItemStack malas. Item, entitas,
   payload, komponen data, klip, advancement 41 **selalu** didaftarkan; saklar config hanya mematikan perilaku.
9. **Lang dan akhir baris.** Teks di `lang-src/sekte_gabung*.id_id.json` + `.en_us.json`, kunci identik, Indonesia utama;
   tiap kunci tepat di satu fragmen (`mergeLang` gagal bila ganda). Berkas baru LF; berkas lama mempertahankan akhir
   barisnya (`KultivatorClient.java`, `musuh/SectRaids.java`, `docs/PANDUAN.md` CRLF).
10. **Gradle hanya agen finish** (`gradlew compileJava compileClientJava jar --no-daemon`); agen modul tidak menjalankan
    Gradle, tes, `runClient` atau `gradlew build`. Tiap modul menjaga berkas progres `docs/progres/gabung-<label>.md`
    (daftar centang, "Langkah berikutnya", "Butuh dari modul lain") agar agen yang terputus melanjutkan, tidak mengulang.
11. **Prasyarat:** 41 dibangun **setelah** 38+38.T, 39+39.F dan 40 selesai sampai F3 dan di-commit (41.14.4). Sebelum itu
    semua berkas 38/39/40 hanya dibaca.
12. **Sumber.** Setiap fitur punya baris Sumber di lang (`kultivator.gabung.<fitur>.source`, "Sumber: ...") atau
    `.inspired` ("Terinspirasi dari ..."). Yang tidak terverifikasi tidak dikutip dengan nomor bab. Mod Rimuru (All Rights
    Reserved) hanya ilham ide umum (41.2, baris terakhir); tidak ada aset, kode, nama atau teks yang disalin.

---------------------------------------------------------------------------------------------------------------------

### 41.1 Fakta dari kode (audit 2026-10-24; kode adalah kebenaran)

**Sekte NPC (DESIGN 36.2.9, T12 satu daftar)**
* `sekte/diplomasi/GerbangPlan` (murni dari seed): `Entry(index, blockX, blockZ, chunkX, chunkZ, band, aliran, pola,
  kataA, kataB, tier, raiderSect, agresi, kehormatan, lambangSeed, ketuaMarga, ketuaNama)`, `id()` = `"s" + index`,
  `rivalId()` = `"g" + index`, `name()`, `master()` ("Ketua Mo Tianhe"), `emblem()`, `warna()`, `column()`;
  `Plan.entry(i|id)`, `Plan.nearest(x, z, aliran)`; `MIN_APART 700`, `RING 1400`; band `BAND_KECIL/SEDANG/BESAR`
  (45/35/20 %); aliran 50 % Lurus / 25 % Netral / 25 % Iblis; **tier awal `max(1, min(5, 1 + band + rand(3)))` = 1-5**.
* `sekte/diplomasi/SekteNpc`: `LURUS 0, NETRAL 1, IBLIS 2`, `MAX_TIER 9`; field paket-privat `tier`, `sumber`,
  `grownDay`, `gateState`, `@Nullable UUID tuan` (vasal aula pemain), `ketuaKalah`, `musuhNpc`, `musuhSampai`,
  `pembelot` (≤ `MAX_PEMBELOT 6`); `static SekteNpc founded(GerbangPlan.Entry e)` menyalin `tier = e.tier()`,
  `sumber = 20 × tier`; `strength() = tier × 100 + sumber`. Pertumbuhan harian `+2..+6` sumber, naik tingkat tiap
  `150 × tier` sumber (`Simulasi`, `Peristiwa.TUMBUH`), perang NPC lawan NPC (`Peristiwa.PERANG_NPC`).
* `sekte/diplomasi/GeneratorNama`: pola 6 (`Sekte/Lembah/Paviliun/Istana/Gunung/Perguruan %s %s`); kata A 20 (0-9 lurus:
  Pedang, Teratai, Bangau, Giok, Kitab, Lonceng, Naga, Burung Hong, Kipas, Bulan; 10-14 netral: Harimau, Tombak, Petir,
  Tungku, Angin; 15-19 iblis: Darah, Racun, Tengkorak, Hantu, Bayangan); kata B 20; `MARGA` 16, `NAMA` 16;
  `member(seed, slot)`. Kata A unik per rencana (T12).
* `sekte/api/DiplomasiService` (`SekteApi.diplomasi()`): `sects`, `name`, `emblem`, `tier`, `standing(UUID owner, id)`,
  `stance`, `adjust(owner, id, delta, "<modul>.<apa>")` (dijepit ±20, sekali per modul/sekte/hari), `gate`, `meet`,
  `met`, `plannedGate`, `master`, `aliran`, `nearest`, `rivalGroup`, `sectOfRival`, `color`, `atWar`. **Standing disimpan
  per UUID** (`DiplomasiData.peek(owner, sect)`; tanpa catatan = `Standing.awal(sect)`: Lurus +10, Netral 0, Iblis −40), jadi
  pemain tanpa aula sudah punya standing dengan kunci UUID-nya sendiri. 41 memakai **standing yang sama** (satu angka per
  pemain per sekte, entah ia punya aula atau tidak).
* `sekte/api/Sikap`: PERANG < −70, BERMUSUHAN < −30, DINGIN < −10, NETRAL < 20, RAMAH, SEKUTU (pakta + ≥ 60).
* `DiplomasiConfig`: `jumlahSekte 8` (4-12), `jarakMin 1500`, `peristiwaPerHari 2`, `perangAktif`, `strukturAktif`,
  `anggotaMaks 6`, `simulasiDetik 30`, `penjagaSekutu 2`, `mulaiAulaLevel 3`.
* Pola jembatan 39F: berkas baru `sekte/diplomasi/DiplomasiMandiri.java` di paket yang sama memanggil metode paket-privat D.
  41 memakai pola yang sama: **`sekte/diplomasi/DiplomasiGabung.java`** (baru, 41D).
* `sekte/langit/npc/LangitNpcPlan`: istana awan (maks `langit.sekteNpcMaks` 2) diberikan ke entri **terkuat** menurut
  `tier` turun, lalu `band` turun, lalu hash seed (karena rencana hanya memberi tier 1-5); jangkar
  `sektelangit/gerbang|murid|ketua|dermaga`.

**Kompleks sekte NPC (38DS, 38.C.3)**
* `dunia/tempat/kompleks/{Kompleks, KompleksAnchors, KompleksApi, KompleksData, KompleksSikap, PasarSekte, RencanaKompleks,
  RotasiGerbang, TandingTempat, Kartu, KompleksCommands, KompleksConfig}`. `KompleksApi.sekteDi(situsId)`,
  `kekuatan(situsId)` (= tier − 1), `kompleksSitus`, `kompleksDi(level, pos)`, `spot(sekteId, titik)` untuk
  `KompleksAnchors.TITIK = {gerbang, aula, arena, pasar, dalam, puncak, tetua, dermaga_awan}`. Kompleks **bisa tidak lahir**
  bila medan tidak layak (`RencanaKompleks.akhir(...).layak()`); gerbang tetap ada.
* Distrik: pita kecil `halaman`, sedang `halaman + puncak`, besar `halaman + dalam + puncak`; lot `aula_utama`,
  `lapangan_latihan` (titik `latihan` ×12, boneka `armor_stand`), `paviliun_kitab`, `paviliun_alkimia`, `asrama_luar/dalam`,
  `kolam_roh` (titik `meditasi` ×8), `gua_meditasi` (titik `meditasi` ×6), `kebun_obat`, `aula_puncak`, `menara_lonceng`,
  `makam_leluhur`, `kolam_darah`, `altar_iblis`, `pasar_sekte`, `gudang_pelayan`, `dapur`; generator
  `tools/gen_tempat_kompleks.py` (fungsi per lot, `k.titik(jenis, x, y, z, hadap)`, `halaman()`, `dalam()`, `puncak()`,
  `tangga(naik)`, `tebing(naik, tengah)`), NBT `structure/kompleks_sekte/{halaman,dalam,puncak}/*.nbt`, tangga
  `tangga_8/10/18.nbt`, tebing `tebing_<n>_sisi|tengah.nbt`. Gerbang: `tools/gen_sekte_gerbang.py`.
* Peran NPC (`dunia/kota/NpcRole`): `MURID_SEKTE_LUAR`, `MURID_SEKTE_DALAM`, `TETUA_SEKTE`, `PELAYAN_SEKTE`,
  `PENJAGA_GERBANG_SEKTE`, `JUARA_SEKTE`; tubuh lewat `dunia/tempat/hidup/HidupApi.tubuh(level, situsId, peran, pos, yaw)`,
  `klip`, `tatap`, `timpaJadwal`, `saksi(level, pos, pelaku)`; rupa lewat
  `SosokApi.setRupa(npc, "murid.<pangkat>|sekte=npc:<sekte>")` (40.7.4).

**Kota Besar, ekonomi, cerita, UI (38)**
* `kotabesar/HariGerbang` (`d % 10 == 4`, 6000-12000): 8 lapak perekrut sekte NPC terkuat (tubuh `MURID_SEKTE_DALAM`,
  suara `kotabesar.recruit_call`). `kotabesar/PertemuanSekte` (`d % 30 == 15`): utusan 4-8 sekte. `kotabesar/TurnamenAgung`
  (14-harian). `kotabesar/Hukum`: `kejahatan(player, town, jenis, poin, at, perluSaksi)`, `poin`, `status`, `buronan`,
  `DICURIGAI 10`, `BURONAN 25`, `BURONAN_BERAT 60`.
* `ekonomi/EkonomiApi`: `bayar(player, nb, alasan)`, `beri`, `harga(stack)`, `kas(pemilikAula, sumber, pk, jumlah, catatan)`,
  `lencana`, `poinGilda`, `daftarJenisMisi(JenisMisi)`; `BukuKas.catat(server, owner, sumber, pk, nb, kunci, isian)`.
* `cerita/{Syarat.daftar(id, Penilai), Akibat.daftar(id, Pelaksana), QuestTracker.daftarTujuan(jenis, Tujuan),
  QuestTracker.mulai/maju/lompat/peristiwa, CeritaApi.daftarJasa(TempatDialog.PenyediaJasa), CeritaApi.kabar(level,
  situsId, templat, isian...), CeritaApi.tandaiTokoh, Isian}`; data `data/kultivator/tempat/{dialog,quest,kabar}/*.json`.
* `dunia/tempat/tema/TemaApi.infami(player)` (0-100, global per pemain) dan `tambahInfami(player, delta, alasan)` (38TK).
  41 **tidak membuat skor karma baru**: kecenderungan Lurus/Iblis pemain = **Infami** yang sama (41.4.4).
* UI kit 38.U: server `dunia/tempat/ui/TempatLayar.buka(player, layar, npcId, lembar)`, `daftar(layar, Penangan[,
  Pembangun])`, `JANGKAU 8`, `LEMBAR_ANGGARAN 32 KB`, `AKSI_PER_DETIK 10`, `LembarKunci`; klien
  `client/tempat/ui/UiTempatClient.daftar(layar, Pembuat)`, `kirim`, `lihatDariHub`; kit `client/tempat/ui/kit/{PanelTempat,
  TabLentera, DaftarGulir, KartuBarang, Pratinjau3D, BandingStat, LabelHarga, PemilihJumlah, LencanaPangkat, CincinWaktu,
  PapanSkor, KeadaanKosong, KeadaanTerkunci, TombolAksi, GelembungDialog, Potret, KotakCari, AngkaGulir, TempatWarna,
  LayarTempatDasar, Lembar, Lukis, TeksTempat, IkonTempat}` (dipakai baca-saja, pola 38TV-U).

**Aula, jenjang, kontribusi (DESIGN 20, 36)**
* `sect/hall/Halls`: `hallOf(UUID)`, `level`, `inTerritory`, `addRenown`, `addPool`, `pool`, `spendPool`, `log`,
  `section(owner, key)`, `addCandidates(owner, List<CandidateSpec>)`, `storeAway`, `addReturnHandler`,
  `addStatusLineProvider`, `addSteleMoveListener`, `addRecruitPauseProvider`, `relocateHall`. **Tidak ada** kait veto
  pendirian aula (dibuat 41A, 41.3.3).
* `sect/hall/SectRanks` (murid pemain): `TERCATAT 0 … TETUA_AGUNG 5`, `PK_COST {0, 30, 150, 600, 1500, 0}`,
  `REALM_NEEDED`, `TRIM`. 41 **tidak** mengubah jenjang murid pemain; pangkat anggota pemain di sekte NPC adalah skala
  sendiri (41.6.1) yang memakai nama dan warna tepi yang sama agar terbaca satu keluarga.
* `sect/hall/Contribution.Ware` (harga PK): Pil Penyembuh 6, Pengumpul Qi 6, 8 ametis 40, Undangan 150, Ceramah 150,
  Fondasi 60, Utusan 300, Giok Teknik 200+ (menurut mutu), Penangkal Tribulasi 1000 (lv 3), Inti Emas 300 (lv 4), Pembersih
  Sumsum Sempurna 400 (lv 6), Kodeks 1200 (lv 3); `OFFER_DAILY_CAP 300`, `PACT_DISCOUNT 0.10`.
* `data/TechniqueGrade`: `HUANG/XUAN/DI/TIAN/ABADI/DEWA` dengan `pavilionPrice` 200/400/900/2000/4000/0 PK dan
  `pavilionHallLevel` 1/3/5/7/9/0. `technique/source/JadeSlips.stack(specId)` membuat giok teknik.
* `data/Realm` (`MORTAL … TRUE_IMMORTAL`), `data/RootGrade` (`TRASH, LOW, MID, HIGH, HEAVENLY`), `data/Dao` (`SWORD, FIRE,
  ICE, LIGHTNING, BODY, LIFE, SHADOW, SPACE`).
* Pemain-ke-pemain: `sect/Sects`/`SectData` (`/sekte`, kelompok pemain, bonus meditasi sesama anggota). Tidak disentuh;
  41 adalah keanggotaan sekte **NPC**.
* `cultivation/CaveAbode.setUnchecked(player)` / `clear(player)` (satu kursi kediaman per pemain, target Jimat Pulang, zona
  aman Kehancuran 16 blok); `cultivation/SpiritSites.addSiteProvider(Function<ServerPlayer, ExtraSite>)`, `CAP 4.0`,
  `Meditation.MULTIPLIER_CAP 3.5`.
* Alam Rahasia: `alam/AlamItems.TOKEN_DARAH_API` (`RealmSpecs.BLOOD_FIRE`), `HEAVENVOID_MAP`, `INHERITANCE_CRYSTAL`
  (`ANCIENT_GOD`), `TOMB_SIGIL` (`HEAVENLY_TOMB`).

**Murid mandiri dan seteru (39, 39.F)**: `sekte/mandiri/seteru/Roster` + `SeteruData` (SavedData `kultivator:mandiri_seteru`):
`6 + tier` (maks 10) murid bernama per sekte NPC (`<sectId>#<n>`, peran Luar/Dalam/Inti/Kakak Tertua/Tetua Muda, ranah,
`Sifat`, Dao, `sikap.<soul>`, `luka`, `status`). 41 **memakai roster ini** sebagai senior dan saingan pemain (satu daftar
tokoh, 41.6.12) dan hanya menambah tetua.

**Penampilan dan gerak (40)**: `sosok/SosokApi.{setRupa, gerak(e, klip, varian, tick[, propKanan, propKiri, ...]), tatap,
bicara, setBusanaKhusus}`; `GerakKatalog.daftar` menerima id 200-399 (39E 200-249, 39F 250-299) → **41 memakai klip
300-349 dan prop 150-169.** Palet busana 40.3.8 butir 1: NPC sekte memakai `LambangCache.get("npc:<sekte>")`.

**Panduan, suara, hub**: `guide/GuideChapters.CHAPTER_COUNT = 20` (38W bab 18-20), 39E menambah bab 21 → **41 = bab 22**
(indeks simpan 21, kunci `chapter.22`, langkah `c22.*`). `tools/gen_voices.py` daftar `WORLD` tuple `(kunci, suara, ja, en,
id, emosi)`, suara `m1 m2 m3 f1 f2 f3 elder boss demon heaven`. `client/hub/HubNav.SEKTE = 3`, `DUNIA = 4`;
`HubCards.register(section, id, glyph, lock, action)`; uji `dunia/DuniaCommands.test(literal, ...)`, pemain
`DuniaCommands.player(literal, ...)`.

---------------------------------------------------------------------------------------------------------------------

### 41.2 Lore dan Sumber

Semua sumber di bawah adalah karya/kisah nyata yang dikenal luas; rincian yang tidak terverifikasi ditulis tanpa nomor bab
dan hanya sebagai ringkasan peristiwa umum.

| Ilham | Isi yang dipakai | Sumber | Dipakai di |
|---|---|---|---|
| Ujian masuk anak-anak | Han Li kecil ikut ujian masuk Sekte Tujuh Misteri (pendakian bertahap), lalu menjadi murid tercatat Tabib Mo | *A Record of a Mortal's Journey to Immortality* (Wangyu), bab-bab awal (sudah dikutip `Candidates`) | Ujian tiga tahap 41.5.4, Usia Tulang 41.4.4, Shifu 41.5.7 |
| Token naik sekte | Han Li masuk Lembah Maple Kuning dengan Token Kenaikan Abadi (升仙令) | *A Record of a Mortal's Journey* | Pintu "Token" 41.5.2, item `token_naik` |
| Kebun dan Tanah Terlarang | Han Li merawat kebun obat; tiap sekte mengirim kuota murid ke Tanah Terlarang Berwarna Darah | *A Record of a Mortal's Journey* (dikutip `Duties`, DESIGN 20.3.11) | Tugas kebun 41.6.4, Kuota Alam 41.6.8 |
| Tetua terkuat | Gelar tetua agung "milik kultivator terkuat", ditawarkan kepada Han Li | *A Record of a Mortal's Journey* bab 871 (dikutip DESIGN 20.3.6) | Jalur Tetua 41.6.1, Tetua Tamu 41.8.3 |
| Murid rendahan | Wang Lin diterima Sekte Heng Yue sebagai murid rendahan yang bekerja kasar sebelum menemukan jalannya | *Renegade Immortal* (Er Gen) | Pintu Pelayan 41.5.2, tugas pikul air 41.6.4 |
| Pelayan, sekte luar dan dalam | Meng Hao dibawa ke Sekte Penyokong (Reliance Sect) sebagai pelayan; Sekte Luar dan Dalam dengan pelatihan promosi | *I Shall Seal the Heavens* (Er Gen) bab 27-31 (dikutip DESIGN 20.3.6) | Pangkat Pelayan → Luar → Dalam 41.6.1 |
| Dapur api, dua tepi, penyusupan | Bai Xiaochun mulai di dapur Sekte Aliran Roh (Spirit Stream Sect) yang terbagi Tepi Selatan dan Utara; ia menyusup ke Sekte Aliran Darah dengan nama lain | *A Will Eternal* (Er Gen); Kantor Misi bab 9 dan 27 (dikutip DESIGN 20.3.7) | Tugas tungku 41.6.4, Papan Misi 41.6.3, misi "Dua Topeng" 41.6.13 |
| Puncak memilih murid | Su Ming diterima Puncak Kesembilan Sekte Langit Dingin (Heaven's Cold Sect) di bawah Tian Xie Zi, dengan sedikit kakak seperguruan | *Pursuit of the Truth* (Er Gen) | Upacara "Tetua Memilih Murid" 41.5.7 |
| Masuk lewat kompetisi | Lin Dong masuk Sekte Dao lewat Perang Seratus Dinasti | *Martial Universe* (Tian Can Tu Dou) (dikutip `HundredDynasties`) | Pintu "Undangan Turnamen" 41.5.2 |
| Sekte, perjanjian, aliansi | Sekte Awan Berkabut (Misty Cloud Sect) dan perjanjian tiga tahun Xiao Yan; Akademi Jia Nan luar/dalam; Aliansi Yan yang didirikan Xiao Yan menghimpun banyak kekuatan | *Battle Through the Heavens* (Tian Can Tu Dou) | Ujian Pewaris 41.8.5, peleburan 41.8.6 |
| Murid berkhianat | Han Feng mengkhianati gurunya Yao Lao | *Battle Through the Heavens* (dikutip 36.2.1) | Membelot 41.7.3 |
| Jalan iblis yang diburu | Fang Yuan menempuh jalan iblis dengan licik dan diburu kalangan lurus | *Reverend Insanity* (Gu Zhen Ren) | Pemburu pembelot, Infami 41.7.3 |
| Murid yang diusir | Patriark Subhuti mengajar Sun Wukong diam-diam pada jaga ketiga, lalu mengusirnya dan melarang menyebut nama gurunya | *Journey to the West* bab 1-2 | Diusir 41.7.2, larangan mengajarkan Teknik Warisan |
| Sekte yang bangkit | Chung Myung membangkitkan Sekte Gunung Hua yang runtuh; "Sembilan Sekte Besar" sebagai susunan peringkat | *Return of the Mount Hua Sect* (Biga) | Naik/turun kelas, Merosot dan Bangkit 41.4.7 |
| Kuil induk dan cabang | Biara Buddha Tiongkok mempunyai kuil bawahan (下院, *xiayuan*) di bawah kuil induk | tradisi biara Tiongkok | Sekte Cabang 41.8.2 |
| Pejabat tamu | *Keqing* (客卿): orang luar yang melayani negara Qin sebagai pejabat tamu; surat Li Si "Menentang Pengusiran Pejabat Tamu" (諫逐客書, ±237 SM) | *Shiji* (Sima Qian), biografi Li Si | Tetua Tamu 41.8.3 |
| Aturan biara | *Kitab Aturan Murni Baizhang* (dinasti Tang): jabatan biara dan hukuman pelanggar sampai diusir | Baizhang Qinggui (dikutip 36.2.6) | Balai Hukum 41.6.11 |
| Jubah dan mangkuk | Patriark Kelima Hongren menyerahkan jubah dan mangkuk kepada Huineng | *Sutra Altar* (dikutip 36.2.6) | Suksesi "Jubah dan Mangkuk" 41.8.5 |
| Lorong Manusia Perunggu | Ujian murid Shaolin dan latihan 36 ruang | legenda Shaolin; film *The 36th Chamber of Shaolin* (Shaw Brothers, 1978) | Ujian corak BUDDHA dan TUBUH 41.5.4 |
| Labirin batu | Lu Xun tersesat di Labirin Batu Delapan Formasi buatan Zhuge Liang | *Romance of the Three Kingdoms* bab 84 | Ujian corak FORMASI |
| Pendekar pedang | Para pendekar pedang Gunung Shu terbang di atas pedang | *Shushan Jianxia Zhuan* (Huanzhulouzhu, 1932) (dikutip 37.2) | Ujian corak PEDANG |
| Sekte Lima Racun | Sekte Lima Racun (五毒教) dan Lan Fenghuang | *The Smiling, Proud Wanderer* (Jin Yong) | Ujian corak RACUN |
| Binatang roh | Para master roh memburu binatang roh untuk cincin roh | *Soul Land* (Tang Jia San Shao) | Ujian corak BINATANG |
| Kompetisi alkemis | Penilaian dan kompetisi alkemis | *Battle Through the Heavens* | Ujian corak ALKIMIA |
| Faksi berperingkat (ilham saja) | gagasan umum faksi yang naik kelas dan anggota berpangkat | mod Rimuru (All Rights Reserved): **hanya ilham**, tanpa aset/nama/teks | 41.4 secara umum |

Narasi dalam game (Kitab Panduan bab 22): "Tidak semua kultivator membangun gunungnya sendiri. Kebanyakan mengetuk gerbang
sekte yang sudah berdiri, memikul air, menyapu halaman, dan naik setapak demi setapak. Gerbang yang kau ketuk menentukan
jalan yang kau tempuh: pedang, pil, formasi, atau darah."

---------------------------------------------------------------------------------------------------------------------

### 41.3 Dua jalan: mendirikan atau bergabung

#### 41.3.1 Gambaran

```
                     Pemain baru (tanpa aula, tanpa sekte NPC)
                    /                                         \
  Jalan Pendiri (DESIGN 20/21/36/37)                Jalan Anggota (41)
  taruh Prasasti Aula -> aula sendiri               temukan sekte -> lamar -> ujian -> sumpah -> Shifu
                    \                                         /
                     +---- jembatan 41.8: Cabang, Tetua Tamu, Restu Mendirikan,
                           Suksesi menjadi Ketua, Melebur / Menyerap ----+
```

Kedua jalan setara: jalan pendiri memberi kebebasan dan murid sendiri; jalan anggota memberi teknik, pil, tempat tinggal,
perlindungan dan cerita sejak awal permainan tanpa harus membangun. Pemain boleh berpindah jalan dengan aturan di bawah.

#### 41.3.2 Aturan bawaan (dipilih untuk cerita dan keseimbangan)

| # | Aturan | Bawaan | Alasan / Sumber |
|---|---|---|---|
| R1 | Keanggotaan sekte NPC | **satu** sekte sekaligus (`Keanggotaan.sekte`), kecuali status MENYAMAR sementara dalam misi "Dua Topeng" | Bai Xiaochun menyusup sebagai orang lain, bukan anggota sah dua sekte |
| R2 | Anggota berpangkat Pelayan-Dalam menaruh Prasasti Aula baru | aula **dorman** + dialog: `[Pamit dulu]` `[Ajukan Cabang]` (terkunci bila pangkat < Inti) `[Dirikan tanpa izin]` `[Batal]` | mendirikan sekte saat masih murid = meninggalkan perguruan |
| R3 | "Dirikan tanpa izin" | = **Pamit Tanpa Izin** (41.7.1): pangkat dicabut, standing −30, tidak diburu | pelanggaran, bukan pengkhianatan |
| R4 | Anggota Inti ke atas | boleh punya aula **sebagai Cabang** (41.8.2) setelah misi "Restu Gunung" | kuil induk dan cabang (*xiayuan*) |
| R5 | Pemilik aula yang ingin masuk sekte NPC | **Tetua Tamu** (aula tetap merdeka; 41.8.3) atau **Bergabung Penuh** (aula menjadi Cabang; pangkat awal 41.5.5 + 1 tingkat bila level aula ≥ 5, maks Dalam) | *keqing* Qin |
| R6 | Aula pemain berperang dengan sekte tempat ia anggota | Diplomasi menolak `PERANG` (veto 41.8.4); Tetua Tamu yang menyatakan perang otomatis "Lepas Tamu" (standing −20) | kesetiaan |
| R7 | Standing | satu angka per pemain per sekte (`DiplomasiService.standing(uuid, sekte)`), dipakai aula dan keanggotaan | penyatuan |
| R8 | Murid pemain | tetap milik pemain; boleh **didaftarkan** sebagai murid titipan (41.8.7) | — |
| R9 | Sekte pemain-ke-pemain (`/sekte`) | tidak terpengaruh; teman `/sekte` tidak otomatis anggota sekte NPC | sistem terpisah |
| R10 | Menjadi Ketua sekte NPC | lewat suksesi (41.8.5); lalu boleh melebur/menyerap dengan aula sendiri (41.8.6) | jubah dan mangkuk Huineng |
| R11 | Melamar sekte yang sedang PERANG dengan aula pemain | ditolak; harus damai dulu | — |
| R12 | Pemain yang membelot dari sekte X lalu melamar sekte musuh X | **diterima lebih mudah** (+10 Kesan) di sekte BERMUSUHAN/PERANG dengan X | pembelot disambut musuh |

#### 41.3.3 Kait veto pendirian (41A, satu suntingan aditif di `sect/hall/Halls.java`)

```java
/** Asked before a placed stele founds a NEW hall (not a move). Non-null = the hall is created dormant with this reason. */
public interface FoundVeto { @Nullable Component veto(ServerPlayer founder, ServerLevel level, BlockPos pos); }
public static void addFoundVeto(FoundVeto veto)
```
Dipanggil di jalur pendirian aula baru (verifikasi titik tepatnya di `Halls`, tempat aula baru dibuat dari prasasti yang
ditaruh). Veto = aula dibuat **dorman** (mekanisme dorman yang ada) dan 41A membuka dialog R2; pilihan pemain memanggil
aktivasi yang ada, atau prasasti tetap dorman sampai diputuskan (tanpa batas waktu; mencabut prasasti membatalkan). Tidak ada
perilaku lain di `Halls` yang berubah.

---------------------------------------------------------------------------------------------------------------------

### 41.4 Kelas sekte, corak dan tingkat (mengikuti pengaturan)

#### 41.4.1 Lima kelas (`sekte/gabung/api/KelasSekte`, data murni)

Kelas diturunkan dari **tingkat 1-9** sekte NPC yang sudah ada (`DiplomasiService.tier`), jadi tidak ada angka kedua.

| Kelas | `id` | Tingkat | Nama id_id / en_us | Gelar dunia | Ranah Ketua | Kursi Tetua | Penduduk data (38) | Faktor `kf` |
|---|---|---|---|---|---|---|---|---|
| 1 | `kecil` | 1-2 | Perguruan Kecil / Minor School | "Sekte Kelas Tiga" | Fondasi | 3 | 40 | 0.6 |
| 2 | `menengah` | 3-4 | Sekte Menengah / Middle Sect | "Sekte Kelas Dua" | Inti Emas | 4 | 80 | 0.8 |
| 3 | `besar` | 5-6 | Sekte Besar / Great Sect | "Sekte Kelas Satu" | Jiwa Baru Lahir | 5 | 120 | 1.0 |
| 4 | `agung` | 7-8 | Sekte Agung / Grand Sect | "Tanah Suci" | Transformasi Dewa | 6 | 120 (+30 di istana awan) | 1.4 |
| 5 | `abadi` | 9 | Sekte Abadi / Immortal Sect | "Sekte Abadi" | Penyatuan Dao | 7 | 120 (+30) | 2.0 |

* `KelasSekte.of(int tier)`: `tier ≤ 2 → 1`, `≤ 4 → 2`, `≤ 6 → 3`, `≤ 8 → 4`, selain itu 5.
* `ranahKetua(t) = Realm.values()[2 + (t − 1) / 2]` (t1-2 Fondasi … t9 Penyatuan Dao). Ranah tetua = `ranahKetua(t)` − 1
  ordinal (min Pemurnian Qi); Kakak Tertua roster 39F tetap `tier − 1` ordinal.
* `kf` mengalikan biaya PK promosi (41.6.1), jatah mingguan (41.6.7) dan hadiah PK (41.6.2); harga barang Paviliun **tidak**
  dikalikan (harga barang satu sumber, 41.0.4).

#### 41.4.2 Sebaran tingkat awal (config `gabungSekte.sebaran`)

Rencana T12 memberi tier 1-5; tanpa perubahan, Sekte Agung/Abadi hanya lahir dari pertumbuhan panjang. Agar "sekte
mengikuti pengaturan", tingkat awal **dipetakan ulang** saat `SekteNpc.founded` (satu baris milik 41D:
`s.tier = DiplomasiGabung.tingkatAwal(e)`; `sumber = 20 × tier` tetap). Pemetaan murni dari rencana + config, jadi worldgen
(band gerbang, kompleks, istana awan) dan simulasi tetap sepakat.

* Urutan kekuatan: `KelasSekte.urutan(plan)` = entri diurutkan **persis seperti `LangitNpcPlan`** (tier rencana turun, band
  turun, hash seed), sehingga dua sekte yang mendapat istana awan selalu yang teratas (verifikasi pembanding dengan membaca
  `LangitNpcPlan`; 41 tidak menyuntingnya; bila pembandingnya privat, salin rumusnya dan uji `uji gabung sebaran` membuktikan
  hasilnya sama). Peringkat `r = 0 … n−1`, `q = r / n`; acak dari `new Random(seed ^ 0x6A_B0_41L ^ index)`.

| Preset | Isi | Contoh 8 sekte (r 0-7) |
|---|---|---|
| `seimbang` (**bawaan**) | r0 → 8, r1 → 7, q < 0.40 → 5-6, q < 0.70 → 3-4, sisanya 1-2 | 8, 7, 6, 5, 4, 3, 2, 1 |
| `alami` | tier rencana T12 apa adanya (1-5) | 1-5 |
| `agung` | `seimbang` + 1 (r0 maks 9) | 9, 8, 7, 6, 5, 4, 3, 2 |
| `kecil` | `alami` − 1 (min 1), r0 maks 4 | 1-4 |
| `datar` | semua = `gabungSekte.tingkatDatar` (1-9, bawaan 3) | semua sama |

* `gabungSekte.tingkatMaks` (1-9, bawaan 9) membatasi pertumbuhan Simulasi lewat `DiplomasiGabung.batasTumbuh` (tumbuh
  berhenti di batas; sumber tetap disimpan).
* Jumlah sekte tetap `diplomasi.jumlahSekte` (4-12). Bila `diplomasi.strukturAktif = false`, sekte tetap ada sebagai data dan
  bisa dimasuki lewat perekrut kota (41.4.6 baris "tanpa tempat").
* Band (ukuran gerbang) tidak berubah: Sekte Agung bergerbang kecil mungkin terjadi pada `alami`/`datar`; visual kelas
  (41.4.6) membuat bedanya terlihat tanpa blok. Pada `seimbang` band dan tier berkorelasi karena urutan memakai band.

#### 41.4.3 Corak sekte (10), dari kata A nama sekte

`Corak.of(kataA)` (murni; kata A unik per rencana sehingga corak konsisten dengan nama):

| Corak | Kata A (indeks) | Dao (diskon 20 % Paviliun) | Rasa | Ujian corak (41.5.4) | Gaya jubah (40) | Teknik Warisan contoh |
|---|---|---|---|---|---|---|
| `PEDANG` | Pedang (0) | `SWORD` | disiplin, pedang terbang | Seratus Tebasan | `HANFU_L` ringkas, sabuk pedang | "Pedang Awan Seribu Lapis" |
| `BUDDHA` | Teratai (1), Lonceng (5) | `LIFE`, `BODY` | welas asih, sumpah tak membunuh | Lorong Manusia Perunggu | `TAO` abu-kunyit, tasbih | "Telapak Lonceng Emas" |
| `BINATANG` | Bangau (2), Naga (6) | `BODY` | menjinakkan, menunggang | Taklukkan Binatang | `TEMPUR` + kerah bulu | "Raungan Naga Hijau" |
| `FORMASI` | Giok (3), Kitab (4), Kipas (8) | `SPACE` | cendekia, jimat | Labirin Batu | `HANFU_L` + topi sarjana | "Delapan Pintu Giok" |
| `UNSUR` | Bulan (9), Petir (12), Angin (14) | `ICE`, `LIGHTNING`, `SHADOW` | kendali Qi | Lima Pilar Qi | `HANFU_P` berselendang | "Petir Sembilan Langit" |
| `ALKIMIA` | Burung Hong (7), Tungku (13) | `FIRE` | pil, tungku | Satu Tungku | celemek tungku | "Api Hong Penyuling" |
| `TUBUH` | Harimau (10), Tombak (11) | `BODY` | tubuh baja, tombak | Pikul Gunung | `TEMPUR` lengan pendek | "Tombak Harimau Menerjang" |
| `DARAH` | Darah (15), Tengkorak (17) | `BODY`, `FIRE` | korban darah | Kolam Darah | hitam-merah, rantai | "Bakar Darah Seribu Jiwa" |
| `RACUN` | Racun (16) | `LIFE` | ramuan, licik | Tiga Cawan | ungu-hijau, kantong racun | "Kabut Lima Racun" |
| `BAYANGAN` | Hantu (18), Bayangan (19) | `SHADOW` | senyap, menyusup | Ujian Senyap | hitam berkerudung | "Langkah Bayangan Hantu" |

Corak menentukan ujian tahap 2, rasa misi papan, tugas harian khas, Teknik Warisan Sekte (41.6.5), gaya jubah (lambang
tetap dari 40.3.8 butir 1; corak hanya memilih gaya/potongan/hiasan) dan baris suara. **Teknik Warisan** = satu
`TechniqueSpec` nyata yang sudah ada dengan Dao corak dan mutu = mutu maks kelas (41.4.5), dipilih deterministik dari
`TechniqueRegistry` dengan benih sekte; nama tampilan "Warisan <nama sekte>: <nama teknik>"; **bukan** teknik baru.
Nama contoh di tabel hanya untuk teks lore/kabar.

#### 41.4.4 Syarat masuk per kelas (Ujian Gerbang ke Tercatat/Luar)

| Kelas | Ranah min | Akar min | Usia Tulang maks (hari) | Standing min | Infami: Lurus / Netral / Iblis | Catatan Kejahatan ditolak |
|---|---|---|---|---|---|---|
| Kecil | Fana | TRASH | — | −30 | ≤ 40 / ≤ 70 / bebas | Lurus: Buronan Berat |
| Menengah | Pemurnian Qi | LOW | 120 + 60 × kelebihan | −10 | ≤ 30 / ≤ 60 / ≥ 5 | Lurus: Buronan |
| Besar | Pemurnian Qi Akhir | MID | 80 + 50 × kelebihan | 0 | ≤ 20 / ≤ 50 / ≥ 10 | Lurus, Netral: Buronan |
| Agung | Fondasi (Awal butuh HIGH) | MID | 60 + 40 × kelebihan | +10 | ≤ 15 / ≤ 40 / ≥ 20 | Lurus: Dicurigai |
| Abadi | Inti Emas (atau Fondasi + HEAVENLY) | HIGH | 45 + 30 × kelebihan | +20 | ≤ 10 / ≤ 30 / ≥ 30 | Lurus: Dicurigai |

* **Usia Tulang** = hari dunia pemain hidup di dunia ini (`Stats.PLAY_TIME / 24000`, angka yang sama dengan Cermin Karma
  `Courts.karma`). "Kelebihan" = ordinal ranah pemain − ordinal ranah min. Kultivator yang "tua" harus lebih kuat.
* **Infami** = `TemaApi.infami(player)` (satu nilai, 38.T.7.8 butir 4). Pemain bersih yang melamar sekte Iblis boleh
  menempuh **Ujian Darah**: tahap 2 corak DARAH/RACUN/BAYANGAN memberi Infami +10 saat lulus, dan syarat Infami dianggap
  terpenuhi bila hasilnya mencapai minimum.
* **Catatan Kejahatan** = status `Hukum` terburuk dari ibu kota yang diketahui. Sekte Iblis tidak peduli; Buronan Berat
  malah memberi Kesan +5 di sekte Iblis.
* Pintu **Pelayan** (41.5.2) mengabaikan akar dan Usia Tulang; Kelas Agung/Abadi tetap butuh Pemurnian Qi dan Infami Lurus
  ≤ 30. Pintu **Token** menghapus syarat Usia Tulang. Pintu **Undangan Turnamen** menghapus akar dan Usia Tulang.
* Satu sumber pemeriksaan: `SyaratMasuk.periksa(ServerPlayer, String sekte, Pintu pintu) → List<SyaratMasuk.Baris(boolean
  ok, String kunci, Object[] isian)>` dipakai layar Lamaran, Direktori dan perintah.

#### 41.4.5 Yang ditawarkan per kelas

| Kelas | Teknik maks | Pil maks | Kediaman terbaik | Kuota Alam / 30 hari (sekte) | Perlindungan nama | Formasi / akses | Pangkat misi |
|---|---|---|---|---|---|---|---|
| Kecil | `HUANG` | Fondasi | Bilik, Gua Kecil | 1 Token Darah Api | sergapan ×0.90 ≤ 512 blok | — | E-C |
| Menengah | `XUAN` | Inti Emas | Gua Urat | 2 Token Darah Api | ×0.85 ≤ 768 | Cakram Pulang ke kompleks 1×/hari | E-B |
| Besar | `DI` | Penangkal Tribulasi | Puncak Pribadi | 2 Token + 1 Peta Heavenvoid | ×0.75 ≤ 1024, penjaga datang | + teleport gratis ke Stasiun ibu kota | E-A |
| Agung | `TIAN` | Pembersih Sumsum Sempurna | Paviliun Awan (istana awan) | 2 Token, 1 Peta, 1 Kristal Warisan | ×0.65 ≤ 1536, regu penolong | + Pindah Awan ke istana (bila ada) | E-S |
| Abadi | `ABADI` | semua | semua | 3 Token, 2 Peta, 1 Kristal, 1 Segel Makam | ×0.55 ≤ 2048 | semua | E-S + misi Langit |

* **Perlindungan nama** (`NamaSekte.faktorSergap(player)`): pengali peluang penyergapan/begal M2 bagi anggota pangkat ≥ Luar
  dalam radius dari gerbang. Dibaca lewat titik kait tekanan harian M2 (verifikasi; bila tidak ada kait publik, 41A menambah
  satu penyedia aditif pada pemilik berkas `musuh/Ambushes`). "Penjaga datang": anggota yang diserang ≤ 256 blok dari gerbang
  sekte Besar+ dibantu 2 tubuh `MURID_SEKTE_DALAM` (Agung+: 3 + 1 `TETUA_SEKTE`) selama 60 s, sekali per hari.
* Bonus meditasi anggota di wilayah kompleks: `SpiritSites` penyedia `gabung_kompleks` ×1.10/1.15/1.20/1.25/1.30 per kelas;
  kediaman/kolam memakai angka 41.6.6 (yang terbesar berlaku; tetap di bawah `CAP 4.0` dan `MULTIPLIER_CAP 3.5`).

#### 41.4.6 Letak per kelas (tempat nyata)

| Kelas | Gerbang (36 D) | Kompleks (38DS) | Istana awan (37) | Visual kelas (entitas/klien, 41V) |
|---|---|---|---|---|
| Kecil | biasanya `kecil` 25×20×33 | `halaman` | — | 2 panji lambang di gerbang |
| Menengah | `kecil`/`sedang` 37×26×45 | `halaman` + `puncak` | — | 4 panji, lentera roh di tangga |
| Besar | `sedang`/`besar` 47×38×48 | 3 distrik | — | 6 panji, kubah formasi tipis di atas aula utama (Normal+) |
| Agung | `besar` | 3 distrik | 2 teratas bila `langit.strukturNpc` | 8 panji, kubah + bangau terbang, pilar cahaya senja |
| Abadi | `besar` | 3 distrik | bila ada | semua + mahkota awan emas di puncak (Maksimal) |
| tanpa tempat | gerbang tidak lahir / kompleks tidak layak / struktur mati | — | — | layanan lewat **Kantor Sekte** kota (`kota/kantor_sekte`) dan perekrut Hari Gerbang |

* Bila kompleks tidak lahir, titik 41 jatuh ke titik gerbang (`sektenpc/gerbang|murid|ketua`, 36 D) dan titik gerbang baru
  41W (41.11.2); kediaman menjadi Bilik di menara gerbang; Papan Misi dan Paviliun Jasa dilayani NPC gerbang.
* Panji, lentera, kubah: `client/sekte/gabung/fx/VisualKelas` menggambar di jangkar yang sudah ada (`kompleks/gerbang`,
  `kompleks/aula`, `kompleks/puncak`) dengan `LambangCache.get("npc:<sekte>")`; maks 8 panji per sekte terlihat; LOD ≤ 48
  beranimasi, ≤ 128 statis, > 128 tidak digambar; Minimal = panji statis tanpa kubah.

#### 41.4.7 Naik dan turun kelas sepanjang waktu (`DiplomasiGabung`, 41D)

Mekanisme lama tetap (pertumbuhan harian, naik tiap `150 × tier` sumber, Ketua dikalahkan aula pemain → −2 tingkat).
41 menambah faktor berbatas:

| Peristiwa | Efek | Batas |
|---|---|---|
| Sumbangan anggota ke kas sekte (barang × nilai Kantor Misi; batu roh × NB/2) | `+1 sumber per 50 PK nilai` | ≤ 20 sumber/hari/sekte dari semua pemain |
| Anggota menang Turnamen Agung atas nama sekte | +10 (juara +25) | per turnamen |
| Anggota menyelesaikan misi papan A/S | +2 / +4 | ≤ 10/hari |
| Perang NPC menang (41.6.10) | +40; lawan −40 | per perang |
| Serbuan ke kompleks dipukul mundur / berhasil | +5 / −15 | per serbuan |
| `sumber < 0` | `tier − 1`, `sumber = 75 × tier` | min tier 1 |
| tier 1 dan kalah dua perang dalam 30 hari | status **MEROSOT**: penduduk data ½, Paviliun hanya HUANG, misi E-D | sampai Bangkit |
| **Bangkit** (rantai "Gunung yang Bangkit", 41.6.13) | MEROSOT hilang, `sumber += 150` | sekali per 60 hari |
| melewati batas kelas | kabar dunia `kabar.gabung.naik_kelas` / `turun_kelas`, papan peringkat | — |

* **Papan Peringkat Sekte** (`PeringkatSekte`, dihitung malas sekali per hari dunia): urut `strength()`; tampil di
  Direktori (41.10.1), papan kabar ibu kota (`kabar.gabung.peringkat`) dan baris pembuka Pertemuan Sekte 30-harian.
* Event bus sendiri `GabungBus.KELAS_BERUBAH(String sekte, int lama, int baru)` (bukan `SekteBus.RIVAL_SECT_ACTION`, agar
  D tidak menerima aksi palsu).
* Bangunan tidak pernah berubah saat runtime; kenaikan kelas terlihat dari komposisi tubuh (Dalam/Tetua bertambah dalam
  `kompleksSekte.tubuhMaks` 12 yang sama), visual kelas 41.4.6, stok Paviliun, kursi tetua, papan misi dan kartu Direktori.

---------------------------------------------------------------------------------------------------------------------

### 41.5 Alur bergabung (modul 41B)

```
Kabar / peta / perekrut / gerbang  ->  Direktori (sekte "dikenal")  ->  Lamaran (pilih pintu, centang syarat)
  ->  status CALON (tanda giok "Calon", berlaku 3 hari dunia)  ->  Hari Ujian di gerbang sekte
  ->  Tahap 1 Tangga Ujian  ->  Tahap 2 Ujian Corak  ->  Tahap 3 Ujian Hati (wawancara)  ->  Putusan
  ->  Upacara Sumpah di altar  ->  Lencana Anggota + jubah + bekal  ->  Tetua Memilih Murid (Shifu)
```

#### 41.5.1 Menemukan sekte

Sekte masuk Direktori pemain (`Keanggotaan.dikenal`, bitset per indeks sekte) dari salah satu:

| Cara | Pemicu | Isi yang terbuka |
|---|---|---|
| Kunjungan | `DiplomasiService.meet` (gerbang, kompleks, istana awan dalam 96 blok) | semua: kelas, corak, syarat, letak |
| Kabar | Kedai Teh / papan kabar: templat `kabar.gabung.penerimaan` ("Sekte X membuka penerimaan pada hari ke-n") | nama, kelas, arah kasar (sektor 8 mata angin), hari ujian |
| Perekrut kota | Hari Gerbang ibu kota (8 lapak), **Kantor Sekte** kota biasa (`kota/kantor_sekte`, satu perekrut sekte terdekat bergilir harian) | nama, kelas, corak, syarat, tombol `[Lamar]` |
| Peta | domain sekte `sekte:s<i>` (PetaDomains) yang sudah terlihat | nama, batas wilayah |
| Peringkat | Papan Peringkat ibu kota | nama, kelas, peringkat (tanpa letak) |
| Utusan sekte | sekte dengan standing ≥ 40 ke pemain bertalenta (akar HIGH+) mengirim **surat undangan** (item `surat_undangan`) 1×/20 hari | semua + pintu Undangan (Kesan +15) |

Perekrut memakai `CeritaApi.daftarJasa(PenyediaJasa)`: NPC berperan `MURID_SEKTE_DALAM`/`JUARA_SEKTE` dengan data sekte di
lapak Hari Gerbang atau Kantor Sekte menambah tablet "Jasa" → `[Tanya tentang sekte]` (membuka kartu Direktori) dan
`[Lamar]` (membuka Lamaran). Tidak ada suntingan `HariGerbang`.

#### 41.5.2 Lima pintu masuk (`enum Pintu`)

| Pintu | Syarat | Tahap yang dijalani | Pangkat maks hasil | Sumber |
|---|---|---|---|---|
| `GERBANG` Ujian Gerbang | 41.4.4 penuh; datang pada **Hari Ujian** sekte: `(hariDunia + indeks × 3) % hariUjian == 0` (`hariUjian` bawaan 10), 6000-12000; Kelas Kecil menerima kapan saja siang | 1-2-3 | Luar (Calon Istimewa: Luar + 100 PK) | Sekte Tujuh Misteri |
| `TOKEN` Token Naik Sekte | item `kultivator:token_naik` milik sekte itu (komponen `kultivator:sekte_token = s<i>`) atau token umum; tanpa syarat Usia Tulang | 2-3 (tahap 1 dilewati) | Luar | Han Li, 升仙令 |
| `UNDANGAN` Undangan Turnamen | 4 besar Turnamen Agung, atau juara Turnamen aula sendiri, atau surat undangan utusan | 3 saja | Luar (Dalam bila Fondasi+ dan juara Turnamen Agung) | Lin Dong, Perang Seratus Dinasti |
| `PELAYAN` Pintu Pelayan | Fana+ (Agung/Abadi: Pemurnian Qi), Infami Lurus ≤ 30, tanpa ujian | — (wawancara singkat 1 pertanyaan) | Pelayan | Wang Lin, Meng Hao, Bai Xiaochun |
| `TAMU` Tetua Tamu | pemilik aula level ≥ 3 **atau** ranah ≥ `ranahKetua(t)` − 1; standing ≥ 20; diundang lewat utusan atau melamar di aula utama | Ujian Tamu (41.8.3) | Tetua Tamu | *keqing* |

* **Token Naik Sekte**: sumber barang = rampasan peti Reruntuhan (2 %), lot Lelang Agung (1 per lelang, 38.M.2.3 tabel
  `agung`, didaftarkan 41B lewat data tabel lot, verifikasi API data lot), hadiah rantai quest kota 38.S (Akibat
  `gabung.beri_token`), dan murid sekte NPC yang bersahabat (roster `sikap ≥ 80` memberi token sektenya sendiri, sekali).
  Token umum (tanpa komponen) berlaku untuk sekte mana pun berkelas ≤ 3. Token habis dipakai.
* Lamaran gagal → jeda 3 hari dunia per sekte (`Keanggotaan.jedaLamar.<s>`).

#### 41.5.3 Lamaran dan Calon

* Layar **Lamaran** (41.10.2) menampilkan pintu yang mungkin dan centang syarat dari `SyaratMasuk.periksa`. `[Ajukan]` →
  status `CALON` (sekte, pintu, hari ujian berikut, `calonSampai = hari + 3`), item `kultivator:tanda_calon` (giok kecil
  "Calon <sekte>", tidak bisa dijual, hilang saat status berakhir). Satu lamaran aktif per pemain.
* Pada Hari Ujian, `PENJAGA_GERBANG_SEKTE` di `kompleks/gerbang` (atau `sektenpc/gerbang`) memanggil calon: baris suara
  `gabung.gerbang_panggil` saat pemain ≤ 32 blok; pemain klik penjaga → `[Mulai ujian]` (Sibuk diperiksa). Calon NPC data (2-6,
  anak-anak dari kampung terdekat, nama dari `TempatNames`) ikut tampil sebagai tubuh `ANAK`/`PETANI` (≤ 4) agar ujian
  terasa ramai; mereka hanya berjalan di tangga dan berhenti di langkah acak (klip `lelah_berlutut` 40).

#### 41.5.4 Ujian Masuk tiga tahap

Semua tahap: Sibuk `gabung.ujian` aktif, murid pemain diveto ikut (`Disciples.addFollowVeto`), terbang dan teleport
ditolak (pesan), maks 1 ujian per sekte sekaligus (pemain lain menunggu 60 s). Mati/keluar wilayah = tahap gagal (tidak ada
kerugian selain jeda 3 hari).

**Tahap 1 - Tangga Ujian** (`masuk/TanggaUjian`): mendaki tangga gunung kompleks (lot `tangga_8/10/18` + tebing; tanpa
kompleks: tangga gerbang) dari `kompleks/ujian_bawah` ke `kompleks/ujian_atas` (titik baru 41W) dalam `waktuTangga` 300 s.
* **Tekanan Gunung** 0-100 (HUD batang merah giok): `+0.6/s` diam di tangga, `+0.25/s` saat naik, `+8` tiap "ilusi"
  (tiap 25 % ketinggian: kilasan wajah iblis hati + angin, klien), `−1.5/s` saat jongkok-meditasi (sneak diam ≥ 2 s).
  Tekanan memberi Slowness I di 40, II di 70; 100 = jatuh terduduk (klip 40), tahap gagal.
* Skor tahap 1 `S1 = clamp(1 − waktu/300, 0, 1) × 0.5 + (1 − tekananPuncak/100) × 0.5`, dikali `0.85 + 0.05 × akar`
  (TRASH 0 … HEAVENLY 4).
* Visual: anak tangga ilusi di atas tangga nyata (pola tangga ilusi 36 G, quad glow; Minimal 120 anak tangga, Normal 400,
  Maksimal 1000; hanya ≤ 96 blok), kabut aditif di kaki.

**Tahap 2 - Ujian Corak** (`masuk/UjianCorak` + satu kelas per corak di `masuk/corak/*`), di lapangan latihan
(`kompleks/arena`) atau titik ujian; batas 180 s kecuali disebut. `S2` 0-1.

| Corak | Ujian | Pelaksanaan (tanpa blok) | Lulus / skor |
|---|---|---|---|
| PEDANG | **Seratus Tebasan** | 6 boneka latihan (titik `latihan`; entitas `BonekaUjianEntity` hitbox, bukan `armor_stand` templat) menyala bergiliran 2 s; tebas yang menyala dengan senjata kelas PEDANG/TOMBAK (`WeaponClass.of`) | `S2 = kena/100` (100 nyala); Fondasi+ boleh pilih duel Duel Pedang ramah vs Murid Luar roster (menang = 1.0, kalah dengan ≥ 40 % HP lawan hilang = 0.6) |
| BUDDHA | **Lorong Manusia Perunggu** | 3 gelombang × 2 tubuh `BiksuPerungguEntity` (KultivatorNpc `TETUA_SEKTE` rupa perunggu, tier `kelas`, aturan duel: tidak bisa mati, roboh di 10 %) di lorong `kompleks/lorong`; **membunuh makhluk apa pun selama ujian = gagal** (sumpah) | `S2 = gelombang lolos / 3`, −0.2 bila HP pemain < 30 % di akhir |
| BINATANG | **Taklukkan Binatang** | binatang roh terkurung (`beast/SpiritBeasts` jenis lokal, tier kelas, entitas `noSave` di kandang `kompleks/kandang`): turunkan ke ≤ 20 % HP **tanpa membunuh**, lalu beri makanan kesukaannya (klik) | 1.0 tanpa membunuh; 0.5 bila pemain membawa binatang roh miliknya sendiri ber-tier ≥ kelas (alternatif) |
| FORMASI | **Labirin Batu** | labirin 9 × 9 dari `DindingIlusiEntity` (kolider 1×3×1 tak terlihat + kabut klien, pola `ColliderEntity`, maks 48 entitas, hanya selama ujian) di halaman; 3 "mata formasi" harus diinjak berurutan; petunjuk dari 8 trigram di dinding | `S2 = 1 − waktu/180` bila keluar; 0 bila waktu habis |
| UNSUR | **Lima Pilar Qi** | 5 pilar titik `pilar_<n>` (entitas penanda); berdiri di tiap pilar dan jaga bar Qi dalam pita sempit (HUD jarum bergerak acak; klik kanan = tahan, lepas = turun) 15 s | rata-rata ketepatan 0-1 |
| ALKIMIA | **Satu Tungku** | kuali sekte di `paviliun_alkimia` (`kerja:kuali`, `AlchemyFurnace` yang sudah ada): buat pil apa pun dalam 600 s dengan bahan dari "kotak ujian" (diberi, dikembalikan bila tidak dipakai) | mutu pil Low 0.5, Mid 0.75, High 0.9, Perfect 1.0; gagal 0 |
| TUBUH | **Pikul Gunung** | item `batu_pikul` (Slowness II + Mining Fatigue saat di tangan utama, tidak bisa disimpan/dibuang di luar ujian) dibawa dari kaki ke puncak tangga | `S2 = 1 − waktu/240` |
| DARAH | **Kolam Darah** | berdiri di `kolam_darah`: HP terkuras 1/2 s; 3 gelombang `RogueCultivators` VARIANT_DEMONIC tier kelas (`markRealmFoe`); boleh "korbankan darah" (klik) = −20 % HP untuk Kekuatan II 15 s | `S2 = gelombang/3`; Infami +10 saat lulus |
| RACUN | **Tiga Cawan** | 3 cawan (entitas meja): tiap cawan memberi racun (Poison II 10 s + petunjuk aroma di chat); pemain memilih penawar dari 5 herba di meja dalam 20 s | benar/3; minum tanpa penawar dan bertahan memberi +0.1 |
| BAYANGAN | **Ujian Senyap** | malam (atau Gua Renungan gelap bila siang): ambil "giok tugas" dari meja `kompleks/tetua` tanpa terlihat 3 penjaga (`PENJAGA_GERBANG_SEKTE`, kerucut pandang 60°/12 blok, LOS) | 1.0 tanpa terlihat; tiap kali terlihat −0.34 (3 = gagal) |

**Tahap 3 - Ujian Hati** (`masuk/UjianHati`, wawancara di `kompleks/aula`): Tetua Penerimaan (tokoh, 41.6.12) bertanya 3
soal dari kolam 12 per aliran (`data/kultivator/tempat/dialog/gabung_wawancara_<aliran>.json`, layar `dialog` 38.U.3.1).
Tiap jawaban punya bobot `kesan` −10..+15 dan bisa membuka **sifat calon** (catatan di Kartu Anggota: "Teguh", "Licik",
"Welas", "Haus Kuasa"). Contoh Lurus: "Seorang kakak seperguruan mencuri herba kebun dan menyalahkan pelayan. Apa yang
kau lakukan?" (lapor ke Balai Hukum +10 / hadapi sendiri +5 / diam −5 / ikut mencuri −10). Contoh Iblis: "Sahabatmu
menghalangi jalanmu menuju harta. Apa yang kau lakukan?" (singkirkan +15 / tawar +5 / mengalah −5). Kesan juga dari:
standing `/10`, Nama Baik ibu kota `/20`, Infami sesuai aliran (Lurus `−Infami/5`, Iblis `+Infami/5`), Buronan Berat di
Iblis +5, pintu UNDANGAN +15, R12 +10. `S3 = clamp((kesan + 20) / 60, 0, 1)`.

#### 41.5.5 Putusan

`Skor = 35 × S1 + 35 × S2 + 20 × S3 + 5 × akar + 5 × min(2, kelebihanRanah) − 10 × (UsiaTulang melebihi batas ? 1 : 0)`
(tahap yang dilewati pintu dihitung 0.8).

| Skor | Putusan | Hasil |
|---|---|---|
| < 35 | **Ditolak** | jeda 3 hari; tawaran Pintu Pelayan bila kelas ≤ 3 |
| 35-54 | **Murid Tercatat** | pangkat 1 |
| 55-79 | **Murid Luar** | pangkat 2 |
| ≥ 80 | **Calon Istimewa** bila akar ≥ HIGH atau S2 = 1.0 | Luar + 100 PK × `kf` + 2-3 tetua berebut menjadi Shifu (41.5.7); kabar sekte "Bakat baru di Gerbang X" |
| ≥ 80 tanpa syarat istimewa | Murid Luar | + 40 PK |

Pengumuman: kartu judul klien 2 s ("DITERIMA - Murid Luar Sekte Pedang Awan Ungu"), suara `gabung.diterima` (tetua),
`Advancements.grant(player, "sekte/gabung_diterima")`.

#### 41.5.6 Upacara Sumpah

* Di `kompleks/altar_sumpah` (titik baru di `aula_utama`; tanpa kompleks: `sektenpc/ketua`), 60 s, Sibuk `gabung.sumpah`,
  kamera 7b `SekteScenes`-pola pendek (≤ 4 s gerak, bisa dilewati, patuh Penuh/Singkat/Mati, `ShotGuard`).
* Urutan: pemain berdiri di alas → klip `sumpah_berlutut` (300) + `dupa_tiga` (301: tiga batang dupa, prop 150) →
  Tetua/Ketua membacakan aturan (3 baris suara `gabung.sumpah_aturan_1..3`) → pemain menjawab lewat tombol chat
  `[Aku bersumpah]` / `[Aku belum siap]` → `kowtow` (302: tiga kali sujud) → lencana diserahkan (klip `terima_lencana` 303,
  prop 151). Murid NPC sekte (≤ 6 tubuh) berbaris dengan `salam_sekte` (304).
* Hasil: status `ANGGOTA`, item **Lencana Anggota** `kultivator:lencana_anggota` (komponen `kultivator:anggota` =
  `{sekte, uuid pemilik, pangkat}`; tidak bisa dijual, terikat pemilik, bisa diambil lagi di Balai Hukum bila hilang 20 PK),
  **Jubah Sekte** kosmetik (41.11.5), **bekal** per kelas (Kecil: 4 Pil Penyembuh; Menengah: + 2 Pengumpul Qi; Besar: + 1
  Pil Fondasi; Agung: + 1 giok teknik HUANG corak; Abadi: + 1 giok XUAN corak), `DiplomasiService.adjust(uuid, sekte, +10,
  "gabung.sumpah")`, advancement `sekte/gabung_sumpah`.
* `[Aku belum siap]` membatalkan tanpa hukuman (status kembali `CALON` sampai `calonSampai`).

#### 41.5.7 Shifu: Tetua Memilih Murid

* Setelah sumpah (atau pada Hari Ujian berikut bila tetua sibuk), upacara singkat di `kompleks/aula`: tetua yang **mau**
  maju dan memberi tawaran. Minat tetua `m = 30 + 10 × akar + 20 × (Dao/unsur pemain cocok dengan Dao tetua) + 15 × (Calon
  Istimewa) + sikap/5 + acak(0..20)`; tetua dengan `m ≥ 50` menawar (maks 3; bila tidak ada, Tetua Penerimaan menjadi Shifu
  "karena kasihan"; Sumber: Su Ming dan puncak yang memilih murid).
* Pemain memilih satu tawaran di layar `gabung_shifu` (potret, nama, jabatan, ranah, Dao, watak, janji: "Aku akan
  mengajarimu pedang" / "Aku punya resep pil"). Tetua yang ditolak: sikap −5 (Sombong −10, ingat).
* **Ikatan Guru** 0-100 (`Keanggotaan.ikatanShifu`), awal 20: +3 per pelajaran mingguan dihadiri, +5 per misi pribadi Shifu,
  +2 per hadiah (maks 1/hari), +10 saat promosi; −10 bila pemain dihukum Balai Hukum, −20 saat pamit tanpa izin.
* **Pelajaran mingguan** (`hidup/Pelajaran`): sekali per 7 hari dunia, pemain menemui Shifu di titik tetua (jadwal
  06:00-18:00) → 120 s duduk mendengar (klip `dengar_ceramah` 305): Penguasaan Jurus +`30 × kelas` pada satu teknik
  ber-Dao Shifu yang dikuasai pemain (bila ada), atau pemahaman +2 (tunduk batas yang ada), atau resep alkimia acak dari
  corak ALKIMIA. Ikatan ≥ 60: pelajaran dua kali seminggu.
* **Misi pribadi Shifu**: 3 bab per Shifu menurut watak dan jabatan (41.6.13 "Permintaan Guru").
* **Shifu melindungi**: ikatan ≥ 40 dan pemain jatuh < 20 % HP di wilayah kompleks → Shifu muncul (bila tubuh termuat atau
  ≤ 64 blok) dan menahan satu musuh 10 s, sekali per hari (baris `gabung.shifu_lindungi`).
* **Melampaui guru**: ranah pemain > ranah Shifu → adegan singkat (`gabung.shifu_lampaui`, Shifu tertawa bangga atau
  cemburu menurut watak), advancement `sekte/gabung_melampaui`; Shifu tetap Shifu, pelajaran berganti "saling bertukar Dao".
* Ganti Shifu: sekali per 30 hari, 100 PK × `kf`, Shifu lama sikap −15.

---------------------------------------------------------------------------------------------------------------------

### 41.6 Hidup sebagai anggota (modul 41C kecuali disebut lain)

#### 41.6.1 Pangkat dan Pohon Pangkat (`api/Pangkat`, ordinal disimpan; 41A)

Dua jalur dari Inti: **Jalur Murid** (muda, menuju Pewaris) dan **Jalur Tetua** (menuju Wakil). Ketua dicapai dari
keduanya. Warna tepi memakai `SectRanks.TRIM` agar satu keluarga dengan jenjang murid pemain.

```
Pelayan -> Tercatat -> Luar -> Dalam -> Inti -+-> Pewaris ----------+
                                              |                     +--> Ketua
                                              +-> Tetua -> Wakil ---+
                          (Tetua Tamu: jalur samping, setara Tetua tanpa naik)
```

| # | Pangkat (`id`) | Syarat naik ke pangkat ini | Biaya PK (× `kf`) | Hak utama | Kursi |
|---|---|---|---|---|---|
| 0 | Murid Pelayan (`pelayan`) | pintu PELAYAN | — | tugas harian, makan asrama, misi E | ∞ |
| 1 | Murid Tercatat (`tercatat`) | Pemurnian Qi **atau** 50 PK tugas, 3 hari dunia sejak masuk | 0 | Paviliun lantai 1 (pil dasar), Bilik Asrama | ∞ |
| 2 | Murid Luar (`luar`) | Pemurnian Qi, 3 hari sejak Tercatat ("Uji Ulang" = ulang tahap 2 dengan ambang 0.5) | 60 | teknik HUANG, misi E-D, Turnamen Murid | ∞ |
| 3 | Murid Dalam (`dalam`) | Fondasi, **Ujian Murid Dalam** (duel vs roster Dalam atau Alam Dewa Hampa lantai 5), 7 hari sejak Luar | 300 | teknik ≤ XUAN, Gua Kecil, misi ≤ B, kuota undian | ∞ |
| 4 | Murid Inti (`inti`) | Inti Emas, 3 besar Turnamen Murid **atau** satu misi A, 10 hari sejak Dalam | 1200 | teknik ≤ DI, Gua Urat, misi ≤ A, boleh Cabang | Kecil 3 / Mng 4 / Bsr 6 / Agung 8 / Abadi 10 |
| 5 | Pewaris (`pewaris`, "Putra/Putri Sekte") | Jiwa Baru Lahir, menang **Pemilihan Pewaris** (41.8.5 tahap 1), umur keanggotaan ≥ 20 hari | 4000 | teknik ≤ TIAN, Puncak Pribadi, kuota tetap 1/bulan, wakil sekte di Turnamen Agung | 1 |
| 6 | Tetua (`tetua`) | Jiwa Baru Lahir, **Ujian Tetua** (corak: menjaga gerbang 3 gelombang / pil mutu High / formasi), 30 hari keanggotaan | 3000 | teknik ≤ TIAN, Puncak Pribadi, jabatan (41.6.11), suara dalam suksesi, mengajar | kursi kelas (41.4.1) |
| 7 | Wakil Ketua (`wakil`) | Transformasi Dewa, Tetua ≥ 15 hari, dukungan ≥ ½ tetua (41.8.5) | 8000 | semua teknik kelas, memimpin perang | 1 |
| 8 | Ketua (`ketua`) | suksesi "Jubah dan Mangkuk" (41.8.5) | 15000 | 41.8.5-41.8.6 | 1 |
| — | Tetua Tamu (`tamu`) | pintu TAMU (41.8.3) | — | harga Tetua di Paviliun, tanpa jabatan dan tanpa suara | maks 2 per sekte |

* Ranah min untuk Kelas Agung/Abadi naik **satu ordinal** untuk Dalam ke atas (Agung Dalam = Inti Emas, dst.).
* Promosi = layar Pohon Pangkat (41.10.6) → `[Ajukan Kenaikan]` → upacara 10 s di aula (klip `salam_sekte`, kilat jubah dengan
  warna tepi baru, lonceng `BELL_RESONATE`) → advancement `sekte/gabung_pangkat_<id>`.
* Kursi penuh: pangkat menunggu kursi; tokoh NPC pemegang kursi bisa **ditantang** (Duel Pedang ramah, sekali per 7 hari);
  menang = kursi berpindah, tokoh turun satu pangkat (sikap −10, bisa menjadi saingan).
* Penurunan: oleh Balai Hukum (41.6.11) atau tidak hadir 30 hari dunia tanpa izin ("Lalai": turun 1, min Tercatat).

#### 41.6.2 Poin Kontribusi pribadi (PK) — sumber

PK pribadi `Keanggotaan.pk` (bisa dibelanjakan) dan `pkTotal` (tidak pernah turun; untuk peringkat). Sasaran: ≈ ½ pendapatan
NB per hari pita pemain (38.M.1) dalam PK, sehingga kerja untuk sekte seimbang dengan kerja untuk gilda.

| Sumber | PK (× `kf` kecuali disebut) | Batas |
|---|---|---|
| Misi papan (41.6.3) | `dasarPK(pita) × pengali pangkat misi` | 3/hari (+1 Dalam, +1 Inti) |
| Tugas harian (41.6.4) | 5-15 | 3/hari |
| Pesanan sekte (pil/pusaka/herba diminta Paviliun) | nilai Kantor Misi `Contribution` × 1.1 (tanpa `kf`) | bersama donasi ≤ `OFFER_DAILY_CAP` 300/hari |
| Donasi barang | nilai Kantor Misi × 1.0 (tanpa `kf`) | ≤ 300/hari (bersama pesanan) |
| Donasi batu roh | **0 PK pribadi**; masuk kas sekte (sumber 41.4.7), standing +1 per 200 NB (maks +5/minggu) | — |
| Jaga gerbang (shift 5 menit nyata di `kompleks/gerbang`) | 10-30 (+ musuh yang dikalahkan) | 1/hari |
| Turnamen Murid (41.6.9) | juara 300, final 150, semifinal 80, ikut 20 | per turnamen |
| Bela sekte (serbuan, 41.6.10) | 50 × gelombang | per serbuan |
| Perang sekte (misi perang) | 100-400 | per misi |
| Kuota Alam (laporan, 41.6.8) | `10 × tier alam` + 20 % nilai rampasan yang disetor | per kuota |
| Mengajar (Tetua+, ceramah di aula 120 s) | 30 | 1/hari |
| Jatah mingguan (41.6.7) | tabel | 1/minggu |

`dasarPK(pita)` = setengah dasar NB M3: pita ≤ 1: 3-6, 2: 8-15, 3: 20-40, 4: 40-80, 5: 80-160, 6+: 160-320 (dibatasi pita 6).
Pengali pangkat misi E 0.6, D 0.8, C 1.0, B 1.3, A 1.6, S 2.0 (sama 38.M.3). Semua penambahan lewat
`Keanggotaan.tambahPk(player, n, "gabung.<sebab>")` (satu pintu, dicatat di riwayat 20 baris).

#### 41.6.3 Papan Misi Sekte (`hidup/PapanMisiSekte`)

* Satu papan per sekte di `kompleks/papan_misi` (titik baru; `PapanHidupEntity` 38 untuk kertas dunia), diundi malas per hari
  dunia dari benih `(seed, sekte, hari)`: `4 + kelas` misi, pangkat E sampai pangkat maks kelas. Dibagi bersama semua anggota
  (multipemain: misi yang diambil hilang dari papan untuk yang lain, kecuali jenis `kelompok`).
* Jenis misi = **jenis 38 yang sudah ada** (`EkonomiApi.jenisMisi()`: `buru`, `kemah`, `herba`, `kawal`, `binatang`,
  `sarang`, `begal`, `kafilah`, `gua`, `kurir`, `tawanan`) dengan imbalan PK (bukan NB) dan rasa corak, ditambah jenis baru
  yang didaftarkan 41C lewat `EkonomiApi.daftarJenisMisi`:

| Jenis baru | Tujuan nyata | Pangkat | Catatan |
|---|---|---|---|
| `gabung_kebun` | panen petak kebun obat kompleks (titik `kerja:cangkul`) 3× dalam 2 hari | E | Han Li |
| `gabung_antar` | antar barang ke kompleks sekte sekutu / Kantor Sekte kota | E-D | standing +1 sekte tujuan |
| `gabung_pesanan` | buat pil/pusaka mutu ≥ n untuk Paviliun | D-B | ALKIMIA/TUBUH ×1.3 |
| `gabung_buru_iblis` | kalahkan regu Kultivator Iblis di kemah nyata dekat wilayah | C-A | Lurus saja |
| `gabung_culik` | rebut tawanan dari kampung untuk sekte (diganti: "jemput 'tamu' dari kemah lawan") | C-A | Iblis saja; tidak pernah warga kampung nyata (aturan 38.T.7.6) |
| `gabung_mata_mata` | dekati kompleks sekte bermusuhan ≤ 48 blok tanpa terlihat, kembali | B-A | BAYANGAN ×1.3 |
| `gabung_kelompok` | bos sarang binatang / sarang iblis bersama 2-4 anggota | B-S | bagi rata + 10 % |
| `gabung_langit` | tugas di istana awan sekte (bila ada) | A-S | Agung+ |

* Misi diterima di layar Papan (41.10.5); jejak dan penunjuk memakai `QuestTracker` 38 (jenis `misi_sekte`).
  Gagal/kedaluwarsa: PK 0, Catatan Pelanggaran +2 (bukan bila misi kelompok gagal bersama).

#### 41.6.4 Tugas harian (Pelayan/Tercatat wajib 1, lainnya pilihan)

| Tugas | Tempat (titik) | Mekanik | PK | Corak khas | Sumber |
|---|---|---|---|---|---|
| Pikul Air | `kerja:sumur` → `kerja:tong` (2 titik baru) | bawa `ember_pikul` (prop 152, klip `pikul_air` 306) bolak-balik 8× | 8 | semua | Wang Lin |
| Sapu Halaman | `kerja:sapu` (ada) | jalan menyapu 6 titik dalam 90 s | 5 | semua | Martial Peak (penyapu, dikutip 20.3.6) |
| Rawat Kebun | `kerja:cangkul` (ada) | sirami/cabut gulma 4 petak (klik) | 8 | ALKIMIA ×1.5 | Han Li |
| Kipas Tungku | `kerja:kuali` (ada) | jaga nyala tungku dengan kipas (klik ritmis 60 s, klip `kipas_tungku` 307) | 10 | ALKIMIA, DARAH | Bai Xiaochun |
| Salin Kitab | `meja_tulis` (paviliun kitab) | teka-teki salin 5 aksara (pilih urutan) | 10 | FORMASI ×1.5 | — |
| Beri Makan Binatang | `kompleks/kandang` | beri makan 3 binatang | 8 | BINATANG ×1.5 | — |
| Latihan Pagi | `latihan` (ada), 06:00-08:00 | ikuti 3 gerakan senam (klip penuntun NPC) | 6 | PEDANG, TUBUH ×1.5 | — |
| Jaga Malam | `jaga` (ada), 18:00-24:00 | berdiri di pos 120 s, kalahkan pengintai bila muncul | 12 | BAYANGAN ×1.5 | — |

Tugas memberi Ikatan Guru +1 dan sikap tetua jabatan terkait +1. Tidak ada blok berubah: ember, petak kebun, tungku adalah
klip/prop/entitas penanda yang sudah ada.

#### 41.6.5 Paviliun Jasa (`hidup/PaviliunJasa`, layar 41.10.4)

Di `kompleks/jasa` (lot baru `paviliun_jasa`, 41W; tanpa lot: NPC di `aula_utama`). Penjaga = tokoh Tetua Paviliun.
Harga = **harga yang sudah ada** (satu sumber) × pengali pangkat (Pelayan-Luar 1.0, Dalam 0.95, Inti 0.9, Pewaris/Tetua/Tamu
0.85, Wakil 0.8, Ketua 0.7) × 0.8 untuk barang ber-Dao corak sekte × 0.9 bila aula pemain SEKUTU dengan sekte (tidak
bertumpuk dengan diskon pangkat > 0.85).

| Tab | Isi | Harga dasar (PK) | Gerbang |
|---|---|---|---|
| Teknik | giok teknik `JadeSlips.stack(spec)` dari kolam Dao sekte + 2 Dao lain; stok 6 + kelas per minggu | `TechniqueGrade.pavilionPrice` (HUANG 200, XUAN 400, DI 900, TIAN 2000, ABADI 4000) | mutu ≤ maks kelas dan ≤ hak pangkat (41.6.1) |
| Teknik Warisan | satu giok Warisan Sekte | `3 × pavilionPrice` mutu itu | Inti+ (Kecil/Menengah: Dalam+) |
| Pil | pil `Contribution.Ware`: Penyembuh 6, Pengumpul Qi 6, Fondasi 60, Inti Emas 300, Penangkal Tribulasi 1000, Pembersih Sumsum Sempurna 400 | harga `Ware` | maks kelas (41.4.5) |
| Pusaka | senjata kota 38EK (`SenjataKota`) dan pusaka tempa mutu 0-3 (`Forging.buatStok`) | harga NB / 2 (jadi PK = NB/2 = nilai sama) | mutu ≤ kelas − 1 |
| Bahan | herba berumur, inti binatang, bahan tempa dari Lumbung data sekte (corak) | `Harga.value / 2` | Luar+ |
| Kediaman | 41.6.6 | tabel | pangkat |
| Kuota | 41.6.8 | tabel | pangkat |
| Ceramah | Ceramah Kitab untuk satu murid pemain (`SERMON`, +5 pemahaman) | 150 | murid terdaftar 41.8.7 |

* Barang beli diberi komponen `kultivator:asal_sekte` (hari beli) sehingga toko 38 menolak menjual-ulangnya 3 hari dan
  beli-kembali tetap ≤ 0.5 (aturan 38.M.5). Teknik Warisan memberi komponen `kultivator:warisan_sekte = s<i>` pada giok dan
  catatan pada data pemain (`Keanggotaan.warisan`) untuk aturan 41.7.4.
* Pesanan: tab "Pesanan" menampilkan 3 permintaan Paviliun per hari (pil/pusaka/herba tertentu) yang memberi PK × 1.1.

#### 41.6.6 Gua Kediaman dan tempat meditasi

| Kediaman | Titik | Pangkat | Sewa (PK / 7 hari, × `kf`) | Faktor situs `SpiritSites` (`gabung_kediaman`) | Lain |
|---|---|---|---|---|---|
| Bilik Asrama | `kasur` (ada) di `asrama_luar` | Pelayan+ | 0 | ×1.05 | tempat tidur bersama (tidur memulihkan seperti kasur) |
| Gua Kecil | `kompleks/kediaman_<0..5>` (deret gua baru) | Dalam+ | 60 | ×1.20 | `CaveAbode.setUnchecked` (target Jimat Pulang), peti titip 9 slot (data) |
| Gua Urat | `kediaman_<0..3>` di distrik `dalam` | Inti+ | 200 | ×1.40 | + kultivasi tertutup tak terganggu Qi Liar |
| Puncak Pribadi | `kompleks/kediaman_puncak_<0..1>` | Pewaris/Tetua+ | 500 | ×1.70 | + tamu (sesama anggota) boleh meditasi ×1.2 |
| Paviliun Awan | `sektelangit/murid` (istana awan 37, baca titik) | Inti+ Agung/Abadi | 800 | ×1.80 (tidak bertumpuk dengan `sekte_langit` 37; yang terbesar) | Pindah Awan gratis |

* Jumlah gua terbatas oleh titik (Gua Kecil 6, Gua Urat 4, Puncak 2 per kompleks); multipemain berebut; NPC tokoh menempati
  sisa (data). Sewa tidak dibayar → diusir ke Bilik (tidak ada denda lain); `CaveAbode.clear` bila kursinya milik 41.
* **Kolam Roh / Gua Meditasi** (titik `meditasi` yang ada): sesi 30 menit nyata = 20 PK (Inti+ gratis 1 sesi/hari), faktor
  ×1.30 di titik itu (`SpiritSites` penyedia `gabung_kolam`), maks pemakai = jumlah titik.
* Semua faktor tetap di bawah `SpiritSites.CAP 4.0` dan dikalikan ke faktor lama (38.0 butir 4).

#### 41.6.7 Jatah mingguan (`hidup/Jatah`)

Sekali per 7 hari dunia, diambil di Balai Jasa (klik penjaga; tidak menumpuk lebih dari 2 minggu):

| Pangkat | PK (× `kf`) | Barang |
|---|---|---|
| Pelayan | 0 | 2 Pil Penyembuh, makanan asrama |
| Tercatat | 5 | 2 Pil Penyembuh |
| Luar | 10 | 2 Pil Pengumpul Qi |
| Dalam | 30 | 3 Pil Pengumpul Qi |
| Inti | 80 | 1 Pil Fondasi (Kecil/Menengah) atau 1 pil kelas di bawah maks |
| Pewaris / Tetua | 200 | 1 pil maks kelas − 1 |
| Wakil | 300 | 1 pil maks kelas |
| Ketua | 500 | 41.8.5 |
| Tetua Tamu | 100 (tanpa `kf`) | — |

Pil jatah adalah item alkimia yang sudah ada (`kultivator:pill_quality` 1), keran yang sama dengan Paviliun; tidak ada batu roh.

#### 41.6.8 Kuota Alam Rahasia (`hidup/KuotaAlam`)

* Pada hari `d % 30 == (indeks × 7) % 30` sekte membagi kuota kelas (41.4.5). Pembagian: (1) Pewaris 1 tetap; (2) 3 besar
  `pk` bulan itu (pemain dan roster data bersaing); (3) juara Turnamen Murid terakhir; (4) sisanya dijual di Paviliun tab Kuota
  (Token Darah Api 300 PK, Peta Heavenvoid 800, Kristal Warisan 1500, Segel Makam 3000; × pengali pangkat).
* Kuota = item kunci alam yang sudah ada (`AlamItems.TOKEN_DARAH_API`, `HEAVENVOID_MAP`, `INHERITANCE_CRYSTAL`,
  `TOMB_SIGIL`) dengan komponen `kultivator:kuota_sekte = s<i>`; dipakai seperti biasa (`alam/Alam`).
* **Laporan Alam**: dalam 3 hari setelah kembali, setor rampasan ke Paviliun (opsional): PK 41.6.2 + standing +2. Tidak
  menyetor tidak dihukum (kuota tetap sah). Tokoh roster yang ikut kuota bulan itu bisa muncul di alam sebagai **pesaing**
  (tubuh RIVAL bila alam mendukung NPC; verifikasi `alam/RealmRun`; bila tidak, hanya baris cerita "Kakak Lin keluar membawa
  dua herba").

#### 41.6.9 Turnamen Murid (`hidup/TurnamenMurid`)

* Tiap 15 hari dunia (hari `(indeks × 5) % 15`), 12000 siang, di `kompleks/arena` (lei tai). Bracket 8: semua anggota pemain
  pangkat Luar-Inti yang hadir ≤ 64 blok + roster 39F (Luar/Dalam/Inti/Kakak Tertua) mengisi sisa menurut ranah. Babak =
  Duel Pedang (`DuelChallenges.startDirect(null, a, b, PAIR_NPC|pemain, aturan sekte 90 s, berhenti 10 %)`); NPC vs NPC tanpa
  pemain dekat = undian data `0.5 + 0.12 × (P a − P b)`.
* Penonton: murid NPC kompleks (≤ 8, `HidupApi.timpaJadwal(situs, "tonton:<x,z>", sampai)`), tetua di tribun.
* Hadiah 41.6.2, gelar "Juara Turnamen Murid <sekte>" 15 hari (prefix nama di kartu), syarat Inti; kekalahan dari roster
  menaikkan seteru pribadi 39F bila tokoh itu SOMBONG/PENDENDAM (pemicu `TOURNAMENT_ENDED` yang sama).
* Pemain absen: tidak dihukum.

#### 41.6.10 Perang sekte dan membela gerbang (41D)

* **Perang NPC** (`musuhNpc` D) memicu **Panggilan Perang** (`politik/PanggilanPerang`) bagi semua anggota pemain sekte itu:
  pesan emas + `[Lihat]`, durasi perang 10 hari dunia (atau sampai skor ±100). Wajib bagi Dalam+ untuk ikut minimal 1 misi
  dalam 3 hari; lalai = Catatan Pelanggaran +10. Luar ke bawah boleh ikut.
* **Misi perang** (di Papan, tab Perang):

| Misi | Tempat | Isi | Skor perang | PK |
|---|---|---|---|---|
| Bela Gerbang | `kompleks/gerbang` sendiri | 3 gelombang regu lawan (tubuh RIVAL ber-lambang lawan pola `Tubuh`, tier lawan, `markRealmFoe`; maks 6 hidup) | +15 menang / −10 kalah | 50 × gelombang |
| Serbu Pos Depan | titik data di garis antara dua gerbang (40 % jarak dari gerbang lawan), kemah sementara = entitas tenda + 4 penjaga | kalahkan penjaga dan cabut panji (entitas) | +20 | 200 |
| Duel Juara | Arena Agung ibu kota atau lapangan netral | Duel Pedang vs Kakak Tertua/Tetua Muda lawan (roster 39F) | +25 / −15 | 300 |
| Jemput Tawanan | kemah lawan | bebaskan 1-2 murid NPC sekte sendiri yang tertawan (data, tubuh mengikuti) | +10 | 150 |
| Kawal Bekal | jalan 38 antara kompleks dan kota | kawal gerobak (`GerobakEntity` 38EK) | +10 | 100 |

* Tanpa pemain: misi perang diselesaikan data oleh Simulasi seperti biasa. Akhir perang: skor ≥ +100 atau unggul saat 10 hari
  habis → menang (41.4.7); hadiah anggota yang ikut ≥ 2 misi: 1 giok teknik mutu kelas − 1 (Dao corak) atau 300 PK.
* **Serbuan ke kompleks** (`politik/SerbuanKompleks`): sekte Lurus/Netral diserang Kultus/Iblis (nama dari `musuh.sect.*` atau
  sekte Iblis D bermusuhan) rata-rata tiap 12 hari bila ada anggota pemain; hanya **live** bila anggota ≤ 256 blok dari
  gerbang saat pertanda (60 s, adegan `EXTRA_RAID_OMEN` yang ada) dan `Sibuk.why` kosong; regu
  `RogueCultivators.spawnBand(...)` 2-3 gelombang di jangkar gerbang/halaman (tier = kelas + 1). Selain itu data (`P =
  clamp(0.5 + 0.1 × (tier − tierPenyerbu), 0.2, 0.9)`). Tidak ada blok, tidak ada tokoh mati.
* **Aula pemain vs sekte keanggotaan**: lihat R6 dan 41.8.4.

#### 41.6.11 Aturan sekte, Balai Hukum, hukuman (41A)

Aturan per aliran (`data/kultivator/tempat/dialog/gabung_aturan_<aliran>.json`, dibacakan saat sumpah):

| # | Aturan Lurus / Netral | Aturan Iblis | Deteksi | Poin |
|---|---|---|---|---|
| A1 | Dilarang melukai sesama anggota di luar lei tai | Duel di luar lei tai boleh bila menantang dulu (`[Tantang]`); menyerang tanpa tantangan dilarang | serangan ke tubuh/pemain anggota sekte yang sama di wilayah kompleks | 15 |
| A2 | Dilarang membunuh sesama anggota | sama (membunuh di luar Duel Darah) | kematian pemain anggota oleh anggota | 60 → Diusir/Membelot |
| A3 | Dilarang mencuri dari Paviliun, kebun, gudang | "Ketahuan" mencuri dilarang | mengambil dari peti titik `peti_milik` (ada) atau memanen kebun tanpa tugas; `HidupApi.saksi` | 10 (Iblis: 10 hanya bila disaksikan) |
| A4 | Patuhi Panggilan Perang | sama | 41.6.10 | 10 |
| A5 | Dilarang berkomplot dengan musuh sekte | sama | pakta/hadiah aula pemain ke sekte yang berperang dengan sekte ini; misi untuk sekte musuh | 20 |
| A6 | Dilarang membocorkan Teknik Warisan | sama | mengajarkan Warisan ke murid tanpa Restu (41.7.4) | 25 |
| A7 | Hormati tetua (tidak menyerang tetua/ketua) | "Yang kuat dihormati" | serangan ke tokoh tetua | 20 |
| A8 | Dilarang memakai seni iblis di wilayah sekte (Lurus) | — | pakai Gulungan Terlarang 38.T.7.7 di wilayah kompleks | 5 |
| A9 | Kehadiran: tidak hilang > 30 hari tanpa izin | sama | hari sejak terakhir di wilayah | turun pangkat 1 |

* **Catatan Pelanggaran** `Keanggotaan.pelanggaran` 0-100, susut 1 per 2 hari dunia. Ambang: ≥ 10 teguran (Tetua Hukum
  memanggil saat pemain berikutnya di kompleks, dialog), ≥ 20 **denda** `20 × kelas` PK, ≥ 35 **kurungan** Gua Renungan,
  ≥ 50 turun pangkat 1, ≥ 60 **Diusir** (41.7.2); A2 langsung sidang: Diusir, atau Membelot bila pemain kabur.
* **Gua Renungan** (`kompleks/gua_renungan`, lot baru 41W): `durasi = 4 × poin` detik nyata (maks 20 menit), pintu =
  `KotaPintuEntity`-pola terkunci; pemain bermeditasi di sana (meditasi tetap jalan; ×0.8), Sibuk `gabung.kurungan`,
  keluar paksa (teleport/ender pearl/jimat) ditolak dan pemain dikembalikan (`teleportTo` + `resetFallDistance`) maks 3×,
  setelah itu = kabur = **Membelot**. Bisa ditebus `5 PK per 10 s` sisa.
* Sidang Balai Hukum (layar `gabung_hukum`, bagian Kartu Anggota): menampilkan catatan, saksi (nama tokoh/pemain), pilihan
  `[Terima hukuman]` `[Bela diri]` (Ujian Hati satu soal; benar = poin −10) `[Minta Shifu membela]` (ikatan ≥ 50: poin −15,
  ikatan −10).
* Sumber: Baizhang Qinggui (hukuman pelanggar, pengusiran).

#### 41.6.12 Tokoh: senior, saingan, tetua (`dunia/TokohSekte`, 41W; roster 39F)

* **Senior dan saingan** = roster 39F (`Roster`, `6 + tier` per sekte). 41A menambah pembaca publik
  `Roster.anggota(MinecraftServer, String sekteId) → List<Roster.Tokoh>` dan `Roster.sikap/ubahSikap(server, tokohId, UUID,
  delta)` (aditif; pemain memakai UUID sebagai kunci `sikap.<uuid>` di samping jiwa murid). Peran yang dipakai: **Kakak
  Tertua** (Da Shixiong/Shijie: penuntun, Inti), satu **Saingan Utama** (tokoh Luar/Dalam dengan sifat SOMBONG/AMBISIUS
  paling tinggi; dipilih saat pemain masuk, ditandai `Keanggotaan.saingan`), dan sisanya kakak/adik seperguruan.
* **Tetua** (baru, `TokohSekte`, SavedData `kultivator:gabung_tokoh`): kursi kelas (3-7) per sekte, jabatan dari
  `api/Jabatan` yang ada + `PENERIMAAN`: Tetua Agung (`agung`), Hukum (`hukum`), Paviliun (`ajar` → Paviliun Jasa), Pusaka
  (`pusaka` → kas & kediaman), Luar (`luar` → papan misi & perang), Obat (`obat` → corak ALKIMIA/kebun), Penerimaan (jabatan
  41 `penerimaan`, dirangkap Tetua Luar di sekte kecil). Data: `id <s>#t<n>`, nama `GeneratorNama.member(lambangSeed, 100+n)`
  + gelar ("Tetua Hukum Qin Zhenyuan"), `Sosok` (kelamin/usia tua 50-90, 40.2), ranah `ranahKetua − 1 ± 0`, Dao (Dao corak 60 %),
  2 `Sifat`, `sikap.<uuid>`, `minat` (41.5.7), `luka`, `status` (AKTIF, PENSIUN, DIGANTI_PEMAIN).
* **Ketua** = `GerbangPlan.Entry.master()` (nama tetap); tokoh `<s>#k`; ranah `ranahKetua(t)`; diganti hanya lewat suksesi
  41.8.5 atau kekalahan perang D (nama baru dari `GeneratorNama.member(seed, 200 + generasi)`).
* **Tubuh**: tokoh yang dijadwalkan di titiknya (`kompleks/tetua`, `kompleks/jasa`, `kompleks/hukum`, `kompleks/puncak`) memakai
  slot `kompleksSekte.tubuhMaks` (12) lewat `HidupApi.tubuh` dengan `CeritaApi.tandaiTokoh(npc, tokohId)` dan
  `SosokApi.setRupa(npc, "tetua_sekte|sekte=npc:<s>")` (Ketua: `"ketua_sekte|sekte=..."`); muncul bila pemain ≤ 64 blok.
* Contoh tokoh nyata (sekte contoh seed uji `41`; nama dihasilkan, ini hanya ilustrasi untuk pemeriksa): Ketua Mo Tianhe,
  Tetua Agung Bai Haoran (tenang), Tetua Hukum Qin Zhenyuan (keras, "aturan adalah aturan"), Tetua Paviliun Su Mingyue
  (pelit tapi adil), Tetua Obat Gu Lingfeng (pelupa, ramah), Kakak Tertua Lin Shuangjian (setia, penuntun), Saingan Utama Ye
  Changkong (sombong, anak keluarga kaya).

#### 41.6.13 Alur cerita sekte (rantai misi, mesin cerita 38.S)

Data quest `data/kultivator/tempat/quest/gabung_*.json` dengan isian (`Isian`) `{sekte}`, `{shifu}`, `{saingan}`,
`{kakak}`, `{tetuaHukum}`, `{musuh}` (sekte bermusuhan terdekat); syarat/akibat baru didaftarkan 41A:
`Syarat.daftar("gabung.pangkat", ...)` (`gabung.pangkat >= dalam`), `gabung.sekte`, `gabung.kelas`, `gabung.corak`,
`gabung.ikatan`; `Akibat.daftar("gabung.pk", ...)`, `gabung.pelanggaran`, `gabung.sikap`, `gabung.beri_token`,
`gabung.naik_kursi`; tujuan `QuestTracker.daftarTujuan("gabung_titik", ...)` (tiba di titik kompleks).

| Rantai | Bab | Pemicu | Isi singkat | Akhir bercabang |
|---|---|---|---|---|
| **Air dan Kayu Bakar** | 3 | masuk (Pelayan/Tercatat/Luar) | tugas pertama, Kakak Tertua menuntun, Saingan mempermalukan di dapur, latihan pagi | membalas di lei tai (sikap saingan −10, kehormatan) / mengalah (Kakak Tertua sikap +10) |
| **Kebun Seratus Obat** | 4 | Luar, corak bukan DARAH | herba hilang dari kebun; pelayan dituduh; jejak menuju Saingan Utama atau mata-mata sekte musuh | lapor Balai Hukum / peras saingan (Infami +3) / lindungi pelayan |
| **Tanah Terlarang Berdarah** | 3 | kuota alam pertama | masuk alam bersama Kakak dan Saingan; Saingan berkhianat di bab 2 (dua pilihan) | selamatkan saingan (sikap +40, jadi sahabat) / tinggalkan (Infami +2, PK +100) |
| **Permintaan Guru** | 3 per Shifu | ikatan ≥ 30 | menurut watak Shifu: cari herba langka di gua 38; antar surat ke kawan lama di kota; balas dendam lama ke sekte lain (Iblis) | ikatan +15 / resep atau teknik Shifu |
| **Perang Gerbang** | 4 | Panggilan Perang pertama | pertanda, bela gerbang, serbu pos, duel juara | menang: gelar "Pahlawan Gerbang" / kalah: sekte −1 kelas tapi bangkit |
| **Dua Topeng** | 4 | Lurus, Dalam+, BAYANGAN atau Tetua Luar meminta, sekte Iblis bermusuhan ada | menyusup ke sekte Iblis sebagai calon (status MENYAMAR: keanggotaan semu kedua 10 hari; nama samaran tampil) | bocorkan rencana serbuan (sekte menang perang berikut) / ketahuan (diburu sekte Iblis 10 hari) / berpaling (pindah sekte sungguhan = Membelot dari Lurus) |
| **Gunung yang Bangkit** | 3 | sekte MEROSOT | kumpulkan murid tercerai (roster PERGI kembali), pulihkan Paviliun, kalahkan penghina | Bangkit (41.4.7) |
| **Jubah dan Mangkuk** | 3 | Pewaris/Wakil siap | 41.8.5 | Ketua |

Semua teks cerita (2-4 kalimat per tahap) di `lang-src/sekte_gabung_cerita.*`, ditulis per corak bila disebut (PEDANG,
ALKIMIA, DARAH, BUDDHA punya varian kalimat; corak lain memakai varian aliran).

#### 41.6.14 Hidup sehari-hari yang terlihat

* Jadwal kompleks (38NL `Jadwal`, data `tempat/jadwal/kompleks_sekte.json` ditambah 41W baris tokoh): 06:00 latihan pagi di
  lapangan (Kakak Tertua memimpin), 08:00-12:00 tugas, 12:00 makan di dapur, 13:00-17:00 Paviliun/kolam meditasi, 18:00
  jaga malam, 21:00 tidur. Pemain anggota disapa dengan pangkatnya ("Adik seperguruan!", "Kakak senior!") lewat baris suara
  `gabung.sapa_*`; murid NPC berpangkat lebih rendah membungkuk (`salam_sekte` 304) saat pemain Inti+ lewat (maks 1 per 10 s).
* Pemain non-anggota di kompleks mengikuti `KompleksSikap` 38 apa adanya.

---------------------------------------------------------------------------------------------------------------------

### 41.7 Keluar dari sekte (41A, pemburu 41D)

#### 41.7.1 Pamit (keluar terhormat)

* Syarat: status ANGGOTA, pangkat ≥ Tercatat, keanggotaan ≥ 5 hari, Catatan Pelanggaran < 30, tidak ada Panggilan Perang
  aktif yang mewajibkan, sewa kediaman lunas, bukan Ketua (Ketua harus menunjuk pengganti dulu, 41.8.5).
* Alur: layar Kartu Anggota `[Pamit]` → dialog Shifu (bila ada) → upacara singkat di aula (klip `pamit_bungkuk` 308):
  lencana dikembalikan, jubah tetap (kosmetik, lambang berubah menjadi "bekas murid" abu), kediaman dilepas
  (`CaveAbode.clear` bila milik 41).
* **Restu Guru**: ikatan Shifu ≥ 50 → item `kultivator:surat_restu` (komponen `sekte`, `pangkat`, `hari`): standing +10 di
  sekte se-aliran saat melamar (sekali), dan izin mengajarkan Teknik Warisan kepada murid sendiri (41.7.4). Pangkat Inti+
  dengan Restu juga mendapat **Restu Mendirikan**: aula yang didirikan dalam 30 hari dimulai dengan standing +40 dengan
  sekte lama dan tawaran pakta non-agresi (D `PAKTA`) atau langsung Cabang (41.8.2).
* Akibat: `DiplomasiService.adjust(uuid, sekte, +5, "gabung.pamit")`, `Keanggotaan.riwayat` mencatat, PK tersisa
  dibekukan (`pkBeku = pk / 2`), status `PAMIT` dengan hari.
* **Pamit Tanpa Izin** (syarat tidak terpenuhi tetapi pemain memaksa, atau R3): standing −30, pangkat hilang, PK hilang,
  Restu tidak ada, tidak diburu; Kelas Lurus se-aliran melihat catatan "Meninggalkan perguruan" (Kesan −10 30 hari).

#### 41.7.2 Diusir

* Pemicu: Catatan Pelanggaran ≥ 60, sidang A2, keputusan Ketua pemain (41.8.5) atas anggota pemain lain, atau rantai cerita.
* Upacara pengusiran (Baizhang Qinggui; Subhuti dan Sun Wukong): di gerbang, Tetua Hukum membacakan, klip pemain
  `diusir_tunduk` (309), lencana disita, baris `gabung.usir`. Bila pemain tidak di kompleks: surat pemberitahuan (chat) dan
  lencana kehilangan komponen (menjadi giok retak, item hias).
* Akibat: pangkat dan PK nol, standing −30, Infami +5 bila sekte Lurus (diusir karena kejahatan), status `DIUSIR` + hari;
  teknik yang sudah dipelajari **tetap** (seperti Sun Wukong) tetapi Teknik Warisan tidak boleh diajarkan selamanya
  (kecuali kembali bergabung); sekte se-aliran: syarat naik satu kelas (dianggap kelas + 1) selama 30 hari.

#### 41.7.3 Membelot

* Pemicu: kabur dari Gua Renungan (41.6.11), keluar paksa saat Panggilan Perang dengan pangkat ≥ Dalam
  (`[Tinggalkan sekte]` saat Pamit ditolak), membunuh anggota pemain/memukul roboh tokoh tetua di luar lei tai lalu lari dari
  sidang, mencuri dari Paviliun (A3) senilai ≥ 300 PK, atau berpaling di rantai "Dua Topeng".
* Akibat (Han Feng; Fang Yuan): status `PEMBELOT` untuk sekte itu; standing −60 (dijepit oleh `adjust`: diterapkan sebagai
  tiga perubahan −20 pada tiga hari berturut, atau langsung lewat `DiplomasiGabung.setStanding` paket-privat; pakai yang
  kedua); Infami +10 (Lurus) atau +5 (Netral/Iblis); **buronan sekte**: poster di Papan Buronan 38 (gerbang kompleks se-aliran,
  Gilda Kultivator ibu kota) dengan hadiah `30 × tier × (pangkat + 1)` NB (dibayar dari Kas Kota oleh pemburu NPC; pemain lain
  yang mengalahkan pembelot dalam duel mendapat hadiah ini dari kas sekte data, bukan cetak: kas sekte = sumber ×2 NB, dikurangi);
  Teknik Warisan yang dipegang = "warisan curian" (41.7.4).
* **Pemburu Sekte** (`politik/PemburuSekte`, 41D): selama 30 hari dunia, maks sekali per 5 hari, di luar tempat aman
  (`DuniaApi.safeGround`), lewat tekanan harian M2 dan `Sibuk.why`/`enemyTaken`: regu 2-4 tokoh/tubuh sekte itu (tier
  pemain ± 1, `markRealmFoe`), dipimpin Tetua Hukum bila tier ≥ 5. Dialog dulu (layar `dialog`): **Menyerah** (didenda `50 ×
  (pita + 1)` NB "denda tobat", Teknik Warisan dihapus, status `DIUSIR` menggantikan `PEMBELOT`), **Lawan**, atau **Lari**.
  Kalah melawan pemburu = "ditangkap" (tidak mati: HP ≤ 10 % menghentikan pertarungan, aturan duel pada pemain berlaku di
  pertarungan ini), dibawa ke Gua Renungan sekte itu 10 menit nyata, lalu sama dengan Menyerah. Menang 3 kali → sekte
  berhenti memburu ("terlalu kuat untuk dikejar"), status tetap `PEMBELOT`.
* Pembelot yang melamar sekte BERMUSUHAN/PERANG dengan sekte lamanya mendapat Kesan +10 (R12) dan cerita "Dia membawa
  rahasia musuh" (standing +5 dengan sekte baru).

#### 41.7.4 Membawa murid dan teknik

| Keluar | Teknik biasa | Teknik Warisan | Mengajarkan Warisan ke murid sendiri | Murid NPC yang ikut |
|---|---|---|---|---|
| Pamit + Restu | tetap | tetap | **boleh** | sahabat roster `sikap ≥ 80` boleh ikut sebagai calon (`Halls.addCandidates`, 1 orang, status roster `MEMBELOT` 39F = "ikut kakaknya"); standing −5 per orang |
| Pamit tanpa Restu | tetap | tetap | tidak (A6 dianggap pelanggaran → standing −10 per kali) | tidak |
| Diusir | tetap | tetap | tidak | tidak |
| Membelot | tetap | tetap sampai tertangkap/menyerah (lalu dihapus dari data pemain) | boleh, tetapi tiap kali standing −10 dan pemburu +1 regu | sahabat `sikap ≥ 90` boleh ikut (standing −15) |

* Penghapusan teknik memakai API pelupaan teknik pemain yang ada bila ada (verifikasi `CultivationData`/`Techniques`; bila
  tidak ada API publik, 41A menambah satu metode aditif `forget(String specId)` pada pemilik berkas teknik) dan hanya untuk
  spec yang tercatat `Keanggotaan.warisan`.
* Murid pemain yang terdaftar di sekte (41.8.7) otomatis keluar daftar saat gurunya keluar; mereka tetap murid pemain.

#### 41.7.5 Kembali bergabung

| Status lama | Boleh melamar lagi | Pangkat saat kembali | PK |
|---|---|---|---|
| PAMIT (dengan/ tanpa Restu) | setelah 7 hari | pangkat lama − 1 (min Tercatat), tanpa ujian bila dengan Restu (cukup sumpah) | `pkBeku` dikembalikan |
| PAMIT TANPA IZIN | setelah 15 hari | ujian penuh, maks Luar | 0 |
| DIUSIR | setelah 30 hari + rantai "Tebus Dosa" (2 bab: setor barang `100 × kelas` PK nilai ke kas, menang duel vs Tetua Hukum aturan ramah) | Tercatat | 0 |
| PEMBELOT | tidak, selama Ketua yang sama memimpin; setelah suksesi: seperti DIUSIR. **Pengampunan**: bila ranah pemain ≥ `ranahKetua + 2`, sekte mengirim utusan damai (15 % per 10 hari): terima = status DIUSIR, pemburu berhenti | — | 0 |

---------------------------------------------------------------------------------------------------------------------

### 41.8 Bersama sekte sendiri (41D)

#### 41.8.1 Matriks status

| Pemain | Boleh punya aula? | Bentuk aula | Standing aula ↔ sekte | Catatan |
|---|---|---|---|---|
| Bukan anggota | ya | merdeka | bebas | DESIGN 20/36 apa adanya |
| Anggota Pelayan-Dalam | tidak (R2) | aula baru dorman sampai diputuskan | — | Pamit atau Dirikan tanpa izin |
| Anggota Inti+ | ya, **Cabang** | Cabang sekte X | dikunci ≥ 60 (SEKUTU dengan pakta otomatis) | 41.8.2 |
| Tetua Tamu | ya | merdeka | ≥ 20 dijaga; perang = Lepas Tamu | 41.8.3 |
| Ketua sekte NPC | ya | merdeka atau dilebur | dikunci SEKUTU | 41.8.6 |
| Pembelot / Diusir | ya | merdeka | sesuai standing (bisa PERANG) | sekte lama bisa menyerbu aula (D) |

#### 41.8.2 Sekte Cabang

* Syarat: anggota Inti+, rantai "Restu Gunung" (2 bab: persembahan `200 × kelas` PK ke kas + memimpin 1 misi B+), aula level
  ≥ 1 (baru atau yang sudah ada, R5).
* Aula Cabang: nama tampil "<nama aula> · Cabang <nama sekte>", panji aula memakai lambang sekte induk di sudut (decal kecil
  klien, `LambangService` baca); pakta SEKUTU otomatis (`DiplomasiGabung.paktaCabang(owner, sekte)` memakai jalur pakta D).
* Hak: penjaga sekutu saat serbuan aula 2 → 4 tubuh (`DiplomasiConfig.penjagaSekutu` dibaca + 2 lewat `DiplomasiGabung`);
  murid aula boleh membaca di Paviliun Jasa induk (Ceramah 150 PK, giok teknik dengan harga anggota pangkat pemilik); aula
  mendapat 1 calon per 10 hari dari induk (`Halls.addCandidates`, sumber `gabung_cabang`); misi papan induk bisa dikerjakan
  murid aula (36 X misi cerita "utusan" ke induk +5 %).
* Kewajiban: **upeti** 10 % PK kas aula harian dari tambang/kebun/upeti (`Halls.spendPool`, maks `50 × kelas` per hari) ke
  kas induk (sumber +1 per 50 PK), ikut Panggilan Perang induk (aula mengirim 1 murid "away" 2 hari atau pemilik ikut misi).
* Lepas Cabang: pemilik Pamit (Cabang lepas, standing −10) atau menyatakan merdeka (standing −40, Infami +0, menjadi
  merdeka; tidak dihitung membelot bila tanpa Panggilan Perang aktif).
* Sumber: kuil induk dan kuil cabang (*xiayuan*).

#### 41.8.3 Tetua Tamu (Keqing)

* Pintu TAMU (41.5.2). **Ujian Tamu**: Duel Pedang ramah vs Tetua Agung/Ketua (menang tidak wajib: bertahan 60 s atau
  menurunkan HP lawan ≥ 50 % = lulus) **atau** persembahan karya (pil mutu High / pusaka mutu 3 / formasi tingkat 5 aktif di
  aula sendiri, satu per corak).
* Hak: harga Paviliun pangkat Tetua, kediaman Puncak Pribadi (sewa ×1.5), kuota alam dibeli (tidak dibagi), jatah 100 PK,
  akses Formasi/teleport sekte; tidak punya jabatan, tidak memberi suara, tidak bisa naik ke Ketua (harus Bergabung Penuh: sumpah
  → Tetua langsung bila syarat Tetua terpenuhi).
* Kewajiban: satu **Tugas Tamu** per 30 hari (misi papan B+ atau 1 misi perang saat perang); lalai dua kali → status dicabut
  ("Lepas Tamu", standing −10).
* Aula merdeka; menyatakan perang ke sekte itu = Lepas Tamu dan standing −20 (R6). Maks 2 Tetua Tamu per sekte.
* Sumber: *keqing* Qin, surat Li Si.

#### 41.8.4 Diplomasi aula dan keanggotaan (veto, 41D)

* `DiplomasiGabung.veto(UUID owner, String sekte, String aksi) → @Nullable Component` dipanggil di `Aksi.run` (satu baris
  aditif milik 41D, `sekte/diplomasi/Aksi.java`): menolak `perang` ke sekte keanggotaan (anggota/Cabang/Ketua), menolak
  `tuntut` upeti ke sekte induk; Tetua Tamu: tidak ditolak, tetapi menjalankan Lepas Tamu.
* Perang aula pemain dengan **sekte musuh** sekte keanggotaan: diizinkan dan memberi skor perang NPC (+10 per serbuan aula yang
  ditahan) kepada sekte keanggotaan; standing anggota dengan sekte musuh turun seperti biasa.
* Turnamen antar-sekte (36 T) yang diadakan aula Cabang melawan sekte induk: dianggap persahabatan (standing ±0).

#### 41.8.5 Menjadi Ketua (suksesi)

1. **Pemilihan Pewaris** (Jalur Murid, saat kursi Pewaris kosong atau ditantang tiap 30 hari): calon = semua Inti pemain
   yang memenuhi syarat + roster Kakak Tertua. Tahap: (a) **Ujian Pewaris**: Duel Pedang aturan sekte berurutan antara
   calon (bracket); (b) penilaian tetua: tiap tetua memberi suara ke calon dengan `sikap` tertinggi (pemain bisa menaikkan sikap
   tetua lewat misi pribadi). Pemenang = menang duel final **dan** suara ≥ ½, selain itu suara terbanyak di antara 2 finalis.
   Sumber: Xiao Yan dan pertarungan yang dijanjikan; Pemilihan Murid Terpilih *A Will Eternal* (dikutip `Tournament`).
2. **Dukungan Tetua** (Jalur Tetua → Wakil): dukungan ≥ ½ kursi tetua (`sikap ≥ 50` atau pemain-tetua yang memilih).
3. **Jubah dan Mangkuk** (rantai 3 bab, mulai saat Pewaris/Wakil memenuhi ranah ≥ `ranahKetua(t)` dan PK 15000 × `kf`):
   * Bab 1 "Ujian Terakhir Ketua": Ketua NPC menantang Duel Pedang di `kompleks/puncak` (aturan Ketua Sekte 36 D: berhenti 10 %,
     ≤ 3 menit); kalah boleh diulang tiap 7 hari.
   * Bab 2 "Suara Puncak": semua tetua (NPC + pemain) memberi suara di aula (layar Politik); NPC memilih menurut sikap; pemain
     lain yang juga Pewaris/Wakil boleh menentang → duel penentu.
   * Bab 3 "Penyerahan": upacara 90 s di aula utama (murid berbaris, `kowtow`, Ketua lama menyerahkan "jubah dan mangkuk":
     prop 153 mangkuk + kilat jubah emas), kamera 7b pendek, musik `M_GLORY` yang ada, advancement `sekte/gabung_ketua`.
4. **Hasil**: pemain = Ketua sekte NPC. Ketua lama menjadi tokoh `PENSIUN` ("Leluhur Hidup", memberi berkah ×1.05 meditasi
   di kompleks, mengunjungi pemain sekali per 30 hari). Hak Ketua:
   * **Arah sekte** (layar Politik): sikap diplomasi sekte NPC terhadap sekte NPC lain (Netral/Damai/Agresif → bobot
     peristiwa Simulasi; aliran tidak berubah), penunjukan tetua (pemain atau tokoh), Pewaris berikut.
   * **Kas sekte**: tiap 7 hari `kasKetua = min(5 × sumber, 300 × kelas)` PK pribadi; tidak pernah NB.
   * **Aksi D atas nama sekte**: kirim utusan/hadiah/pakta ke sekte NPC lain lewat jalur `Simulasi.apply` (jembatan
     `DiplomasiGabung`), maks 1 per hari.
   * Memimpin perang: memilih sasaran Perang NPC (memulai `PERANG_NPC` sekali per 30 hari, bila `perangAktif`).
   * Kewajiban: hadir di kompleks ≥ 1 kali per 10 hari, kalau tidak sekte "goyah" (tetua Wakil mengurus, sumber −2/hari).
   * Turun takhta: tunjuk pengganti (pemain anggota atau tokoh); tanpa pengganti, Wakil/Tetua Agung NPC mengambil alih.
5. Multipemain: satu Ketua per sekte; Ketua pemain yang luring > 30 hari dunia = Wakil memimpin sementara (tidak dilengserkan).

#### 41.8.6 Melebur dan menyerap (penyatuan)

Hanya Ketua (atau Cabang + Ketua) yang punya aula sendiri. Dua arah, keduanya memakai **satu mekanisme D yang sudah ada**:
`SekteNpc.tuan` (vasal aula pemain).

| Arah | Hasil | Data |
|---|---|---|
| **Serap** (sekte NPC masuk ke aula pemain) | aula pemain = Puncak Utama; kompleks sekte NPC = "Gunung Cabang <nama aula>"; nama tampil sekte NPC = "<nama aula> · Gunung <kata B>"; lambang kompleks = lambang aula pemain (`LambangCache` "npc:<s>" diarahkan ke lambang aula selama `lebur`) | `tuan = owner`, `DiplomasiGabung.lebur(s) = true`; sekte tetap ada di daftar T12 (diplomasi, peta, Saingan tetap sepakat) |
| **Lebur** (aula pemain masuk ke sekte NPC) | aula pemain menjadi Cabang permanen (41.8.2 tanpa upeti) dan pemain tetap Ketua sekte NPC; murid aula boleh tinggal di kompleks | Cabang + Ketua |

Efek Serap (per hari dunia, dibatasi): pool aula `+min(2 × tier, 30)` PK dari sekte; renown aula +`tier` per hari **hanya**
bila murid pemain bertugas di kompleks (aturan 20.3.1: renown dari murid sendiri); 1 calon per 5 hari dari populasi data
sekte (`Halls.addCandidates`, sumber `gabung_serap`); sekte tidak lagi menyerbu siapa pun atas kemauan sendiri; anggota pemain
lain sekte itu tetap anggota (Ketua mereka adalah pemain ini). Pembatalan: hanya dengan turun takhta (Serap lepas, `tuan =
null`, standing tetap SEKUTU 60). Sumber: Aliansi Yan Xiao Yan.

#### 41.8.7 Murid pemain di sekte NPC

* Pemain anggota pangkat ≥ Luar boleh **mendaftarkan** maks `1 + pangkat/2` muridnya sebagai murid titipan (layar Kartu,
  `[Daftarkan murid]`, 50 PK × `kf` per murid): murid mendapat akses kompleks (tidak diserang, boleh meditasi di kolam
  ×1.15, jalan mandiri 39 menganggap kompleks sebagai "rumah kedua" untuk perjalanan `SEKTE_TAMU` tanpa biaya), boleh ikut Turnamen
  Murid (Luar-Dalam) dan Ceramah. Murid tetap milik pemain (`Disciples`, Bond, `SectRanks` tidak berubah).
* Seteru 39F antara murid pemain dan roster sekte keanggotaan dibatasi T2 (tetua melerai, baris `elder.seteru_lerai`).

---------------------------------------------------------------------------------------------------------------------

### 41.9 Multipemain (LAN/server)

* Setiap pemain punya `Keanggotaan` sendiri; satu sekte bisa berisi banyak pemain.
* Kursi terbatas (Inti, Pewaris 1, Tetua per kelas, Wakil 1, Ketua 1, Tamu 2) dibagi pemain + tokoh; pemain menggantikan tokoh
  lewat tantangan (41.6.1). Dua pemain berebut kursi = Duel Pedang aturan sekte di lei tai (penantang memilih jadwal ≤ 3 hari,
  yang ditantang luring > 3 hari = kalah tanpa tanding? **Tidak**: kursi dibekukan sampai ia daring, maks 10 hari).
* **Papan Jasa** (Direktori tab Sekteku): peringkat `pkTotal` bulan ini semua anggota (pemain dan roster), 3 besar pemain
  mendapat kuota (41.6.8).
* **Misi kelompok** (`gabung_kelompok`): 2-4 anggota pemain; satu yang menerima, lainnya bergabung dari layar Papan ≤ 32 blok.
* **PvP**: hanya di lei tai lewat `[Tantang]` (DuelChallenges antarpemain, aturan sekte); serangan antar-anggota di wilayah
  kompleks di luar lei tai = A1 (saksi: tokoh/pemain lain). Sekte Iblis: tantangan resmi boleh di mana saja di wilayah.
* **Sekte musuh berisi pemain lain**: anggota sekte X dan Y yang berperang saling mendapat misi Duel Juara satu sama lain bila
  keduanya daring (opt-in di layar Perang); tidak ada PvP paksa.
* **Politik**: suara pemain-tetua di suksesi dan penunjukan; layar Politik menampilkan siapa memilih siapa (rahasia sampai
  hasil diumumkan).
* Kas sekte bersama: sumbangan semua pemain tercatat (nama + jumlah) di layar Politik tab Kas.
* Sinkron ringkas: payload `kultivator:gabung_lencana` (UUID pemain → sekte, pangkat, warna) dikirim ke pemain dalam 64 blok agar
  nametag/jubah orang lain benar (41.12).

---------------------------------------------------------------------------------------------------------------------

### 41.10 Antarmuka (modul 41U; kit "Pasar Giok" 38.U, 320 × 240; ⟦ ⟧ = tablet kiri, [ ] = tombol)

Server membangun lembar (`TempatLayar.buka`, ≤ 32 KB) dan mendaftar penangan (`TempatLayar.daftar`); klien menggambar
(`UiTempatClient.daftar(layar, Pembuat)`), kelas di `client/sekte/gabung/ui/layar/**`, kit dipakai baca-saja. Kunci lembar
di `sekte/gabung/ui/GabungLembar.java` (javadoc per layar, pola `LembarKunci`). Panel: `giok` untuk sekte Lurus, `kayu` Netral,
`batu` gelap-merah (`TempatWarna` aliran Iblis `#6A1420`) untuk Iblis; warna judul = `DiplomasiService.color(sekte)`.
Keyboard penuh (Tab, Enter, Esc, 1-9 tablet, `/` cari), skala GUI 2-4 (< 320 satu kolom).

**Hub**: `HubCards.register(HubNav.SEKTE, "gabung_sekteku", Glyphs.SEAL, lock, …)` "Perguruanku" (terkunci "Belum bergabung:
buka Direktori" bila bukan anggota, tetap membuka Direktori); `HubCards.register(HubNav.DUNIA, "gabung_direktori",
Glyphs.PATH, null, …)` "Direktori Sekte". Topik Ensiklopedia `gabung_sekte`, `kelas_sekte`, `corak_sekte`.

#### 41.10.1 Direktori Sekte (`gabung_direktori`)

```
+----------------------------------------------------------------------+
|⟦Semua ⟧| Cari: [/ ______]  Urut: (peringkat)(jarak)(kelas)          |
|⟦Lurus ⟧|-----------------------------+--------------------------------|
|⟦Netral⟧| #1 ✦ Sekte Pedang Awan Ungu |  [lambang 48]  Sekte Pedang    |
|⟦Iblis ⟧|    Agung · Pedang · ±1.900 b|  Awan Ungu · Tanah Suci (t8)   |
|⟦Peringkat| #2 Istana Bulan Salju ☁   |  Lurus · Corak Pedang          |
|⟦Sekteku⟧| #5 Lembah Racun Seribu Jiwa|  Ketua Mo Tianhe (T. Dewa)     |
|        | #7 ? (belum dikenal)        |  Sikap: Ramah (+24)            |
|        |                             |  Syarat: ✓Fondasi ✓MID ✗Usia   |
|        |  ▼ 8 sekte · 6 dikenal      |  [Lamar] [Tunjuk di peta]      |
+----------------------------------------------------------------------+
```
Kartu kanan: lambang (`Lambang` dari `LambangService`), kelas + tingkat, aliran, corak, Ketua dan ranah, jarak/arah, sikap,
istana awan (ikon ☁), status MEROSOT, centang syarat `SyaratMasuk` (pintu terbaik), hari ujian berikut. Sekte belum dikenal:
"?" dengan petunjuk "Dengar kabar di Kedai Teh". Tablet **Peringkat**: `PapanSkor` urut kekuatan dengan panah tren 7 hari.
Tablet **Sekteku**: ringkas Kartu Anggota + Papan Jasa.

#### 41.10.2 Lamaran (`gabung_lamaran`)

```
+----------------------------------------------------------------------+
|⟦Gerbang⟧| Melamar: Sekte Pedang Awan Ungu (Agung)                     |
|⟦Token  ⟧| Pintu: Ujian Gerbang     Hari ujian: hari 44 (2 hari lagi) |
|⟦Undangan| ✓ Ranah Fondasi (punya: Fondasi Tengah)                     |
|⟦Pelayan⟧| ✓ Akar MID (punya: HIGH)                                    |
|⟦Tamu   ⟧| ✗ Usia Tulang ≤ 100 hari (punya: 112) → pakai Token          |
|         | ✓ Standing +10 (punya: +24)   ✓ Infami ≤ 15 (punya: 4)       |
|         | Tahap: Tangga · Seratus Tebasan · Ujian Hati                 |
|         | [Ajukan lamaran]   (jeda 3 hari bila gagal)                  |
+----------------------------------------------------------------------+
```
Pintu yang tidak mungkin tampil `KeadaanTerkunci` dengan alasan; tablet Token menunjukkan token di tas.

#### 41.10.3 Kartu Anggota (`gabung_kartu`)

```
+----------------------------------------------------------------------+
|⟦Kartu ⟧| [potret pemain] Han Feiyu · Murid Dalam ▣ (tepi biru)        |
|⟦Shifu ⟧| Sekte Pedang Awan Ungu · bergabung hari 21 · 23 hari         |
|⟦Hukum ⟧| PK 840 (total 2.310)   Peringkat Jasa #4                     |
|⟦Murid ⟧| Shifu: Tetua Paviliun Su Mingyue · ikatan ███▒▒ 58            |
|⟦Keluar⟧| Kakak Tertua: Lin Shuangjian · Saingan: Ye Changkong (−35)  |
|        | Pelanggaran 6/100 ▁   Tugas hari ini 1/3   Jatah: siap ✦     |
|        | Berikut: Murid Inti — ✓Inti Emas ✗1.200 PK ✗Turnamen 3 besar |
|        | [Ambil jatah] [Pohon Pangkat] [Papan Misi] [Paviliun]        |
+----------------------------------------------------------------------+
```
Tablet **Shifu** (potret 3D tokoh, jadwal pelajaran berikut, misi pribadi, hadiah), **Hukum** (catatan, kurungan berjalan
`CincinWaktu`, sidang), **Murid** (murid terdaftar 41.8.7), **Keluar** (Pamit dengan centang syarat, Restu, peringatan
Membelot berwarna bahaya).

#### 41.10.4 Paviliun Jasa (`gabung_paviliun`)

```
+----------------------------------------------------------------------+
|⟦Teknik ⟧| Cari [/ ____]  Pangkat: Dalam (×0.95)   PK: 840            |
|⟦Warisan⟧|-----------------------------+--------------------------------|
|⟦Pil    ⟧| ▣ Tebasan Bulan Sabit (Xuan)|  ╭────────╮ Tebasan Bulan    |
|⟦Pusaka ⟧|    380 PK ✦corak −20%       |  │ giok 3D│ Sabit · Xuan Rnd  |
|⟦Bahan  ⟧| ▣ Pil Pengumpul Qi   6 PK   |  ╰────────╯ Dao Pedang        |
|⟦Kuota  ⟧| ▣ Pil Inti Emas 🔒 Inti     |  Stok minggu ini: 2           |
|⟦Kediaman| ▣ Warisan: Pedang Awan 🔒   |  [Tukar 380 PK]               |
|⟦Pesanan⟧|  ▼ 14 barang                |                                |
+----------------------------------------------------------------------+
```
`KartuBarang` + `Pratinjau3D` + `LabelHarga` dengan ikon PK (bukan NB); alasan kunci (pangkat, kelas, stok). Tablet Pesanan
menampilkan 3 permintaan dan tombol `[Serahkan dari tas]`.

#### 41.10.5 Papan Misi Sekte (`gabung_papan`)

```
+----------------------------------------------------------------------+
|⟦Papan ⟧| Misi hari ini 2/4      Panggilan Perang: ⚔ vs Lembah Racun  |
|⟦Aktif ⟧|------------------------------+-------------------------------|
|⟦Perang⟧| [D] Pikul bekal ke Kota Teh  |  Bela Gerbang Timur           |
|⟦Kelompok| [B] Kemah iblis di Jurang... |  Perang · skor kita +35       |
|⟦Tugas ⟧| [⚔] Bela Gerbang Timur        |  "Panji hitam terlihat di     |
|        | [A] 🔒 butuh Inti            |   punggung bukit..."          |
|        |                              |  Imbalan 150 PK · skor +15    |
|        |                              |  [Terima] [Tunjuk di peta]    |
+----------------------------------------------------------------------+
```
Tablet **Tugas** = tugas harian 41.6.4 dengan titik tujuan; **Kelompok** = misi bersama dan siapa yang bergabung.

#### 41.10.6 Pohon Pangkat (`gabung_pangkat`)

```
+----------------------------------------------------------------------+
|  Pelayan ─ Tercatat ─ Luar ─ [DALAM ◉] ─ Inti ─┬─ Pewaris ─┐          |
|                                               └─ Tetua ─ Wakil ─ KETUA|
|  Kursi Inti 5/6 · Pewaris: Lin Shuangjian · Tetua 5/6 · Tamu 1/2     |
|  ── Murid Inti ───────────────────────────────────────────           |
|  ✓ Inti Emas   ✗ 1.200 PK (840)   ✗ 3 besar Turnamen (hari 50)       |
|  Hak: teknik ≤ Di · Gua Urat · misi ≤ A · boleh Cabang               |
|  [Ajukan Kenaikan]  [Tantang pemegang kursi]                         |
+----------------------------------------------------------------------+
```
Simpul digambar sebagai cap giok berwarna `SectRanks.TRIM`; simpul terkunci 45 % tinta; pemegang kursi tampil saat hover.

#### 41.10.7 Gua Kediaman (`gabung_kediaman`)

```
+----------------------------------------------------------------------+
|⟦Gua   ⟧| Kediamanmu: Gua Kecil #3 · sewa lunas sampai hari 47        |
|⟦Pilih ⟧| Faktor meditasi ×1.20 · Jimat Pulang: ✓                     |
|⟦Kolam ⟧| Peti titip 9: ▣ ▣ ▢ ▢ ▢ ▢ ▢ ▢ ▢                             |
|        | Gua Urat #1 (Inti) 🔒 · #2 dipakai Ye Changkong             |
|        | [Perpanjang 60 PK] [Pindah] [Lepas]                         |
+----------------------------------------------------------------------+
```
Tablet **Kolam**: titik meditasi kosong/terisi, `[Pesan sesi 20 PK]`.

#### 41.10.8 Politik Sekte (`gabung_politik`)

```
+----------------------------------------------------------------------+
|⟦Majelis⟧| Ketua Mo Tianhe (T. Dewa) · Wakil: — · Pewaris: Lin S.     |
|⟦Suksesi⟧| Tetua: Bai (Agung) Qin (Hukum) Su (Paviliun) Gu (Obat) ANDA|
|⟦Perang ⟧| Sikap tetua padamu: Bai ▓▓▓▒ Qin ▓▒▒▒ Su ▓▓▓▓ Gu ▓▓▒▒      |
|⟦Kas    ⟧| Sumber 412 · Kelas Agung (t8) · naik di 1.350 · peringkat #1|
|⟦Cabang ⟧| Perang: vs Lembah Racun · skor +35 · 6 hari lagi            |
|        | [Beri suara] [Usulkan tetua] [Sumbang ke kas]               |
+----------------------------------------------------------------------+
```
Tablet **Suksesi** (tahapan Jubah dan Mangkuk), **Perang** (skor, misi, peta mini garis gerbang ↔ gerbang), **Kas**
(sumbangan per nama, grafik 7 hari), **Cabang** (aula Cabang, upeti, Serap/Lebur untuk Ketua dengan konfirmasi dua langkah).

#### 41.10.9 Layar kecil lain

| Layar | Isi |
|---|---|
| `gabung_shifu` | tawaran tetua saat upacara memilih murid: 1-3 kartu potret, janji, watak; `[Pilih]` |
| `gabung_ujian` (HUD, bukan layar) | batang Tekanan Gunung, waktu tahap, skor ringkas; jarum Lima Pilar; penghitung Seratus Tebasan |
| `gabung_putusan` | kartu putusan (stempel merah "DITERIMA" / abu "DITOLAK"), rincian skor tahap |
| `gabung_hukum` | sidang Balai Hukum (bagian Kartu) |
| `dialog` (38.U.3.1) | semua percakapan tokoh, wawancara, pemburu, utusan |

---------------------------------------------------------------------------------------------------------------------

### 41.11 Konten: tempat, worldgen, tokoh, model, animasi, suara

#### 41.11.1 Tempat yang dipakai (tidak ada struktur jenis baru)

| Tempat | Struktur (pemilik asal) | Fungsi 41 |
|---|---|---|
| Gerbang sekte | `sekte_gerbang_kecil/sedang/besar` (36 D, `gen_sekte_gerbang.py`) | panggilan ujian, Pelayan, kompleks cadangan |
| Kompleks halaman | `kompleks_sekte/halaman/*` (38DS) | Paviliun Jasa (lot baru), papan misi, altar sumpah, tugas, Balai Hukum |
| Tangga gunung / tebing | `kompleks_sekte/tangga_*`, `tebing_*` | Tangga Ujian; deret gua kediaman (tebing) |
| Kompleks dalam | `kompleks_sekte/dalam/*` | Gua Urat, kolam roh, lorong perunggu, Gua Renungan |
| Kompleks puncak | `kompleks_sekte/puncak/*` | Puncak Pribadi, aula ketua, Duel Ketua |
| Istana awan | `sekte_langit/*` (37, baca saja) | Paviliun Awan, misi Langit |
| Kota / ibu kota | `kota/kantor_sekte` (34), Hari Gerbang, Pertemuan Sekte, Turnamen Agung (38) | perekrut, peringkat, wakil sekte |
| Reruntuhan, lelang | 34/38 | sumber Token Naik Sekte |

#### 41.11.2 Perubahan generator (41W; dunia baru, NBT dibuat ulang; TplCheck dan `cek_tempat.py` harus bersih)

`tools/gen_tempat_kompleks.py` (41W pemilik tunggal saat 41):
* Lot baru halaman **`paviliun_jasa`** 30 × 14 × 22 (Lurus giok-putih, Netral kayu-emas, Iblis basalt-merah lewat processor
  aliran yang ada): aula dua lantai berpanggung, meja penukaran, rak giok (dekorasi `chiseled_bookshelf` kosong, lentera),
  titik `jasa` (penjaga), `jasa_meja`, `papan_misi` (dinding luar, tempat `PapanHidupEntity`), `pesanan`. Mengganti
  `gudang_pelayan` pada pita sedang/besar dan pada pita kecil menggantikan satu slot `dapur` (dapur pindah ke sayap
  `asrama_luar`). Dijamin tetap 3 × 3 lot.
* Titik baru di lot yang ada: `aula_utama` → `altar_sumpah` (alas 3 × 3 di depan takhta), `hukum` (meja samping), `aula`
  (sudah); `lapangan_latihan` → `ujian_corak`, `pilar_0..4` (lingkar 9 blok), `kandang` (sudut), `lorong` (sisi panjang);
  `kebun_obat` → `kerja:sumur`, `kerja:tong`; `gua_meditasi` → `kediaman_0..5` (setiap ceruk menjadi gua kecil berpintu
  lengkung, kasur + bantal meditasi + rak, satu ceruk dicadangkan `gua_renungan`); `asrama_dalam` (distrik `dalam`) →
  `kediaman_dalam_0..3`; `aula_puncak` → `kediaman_puncak_0..1` (dua paviliun kecil di sisi); tangga → `ujian_bawah` (anak
  tangga pertama), `ujian_atas` (gapura terakhir).
* `kompleks/titik` baru ditambahkan ke `KompleksAnchors.TITIK` (41W suntingan aditif): `jasa`, `papan_misi`, `altar_sumpah`,
  `hukum`, `ujian_bawah`, `ujian_atas`, `ujian_corak`, `kandang`, `lorong`, `gua_renungan`, `kediaman_*`, `pilar_*`.
* Kompleks Iblis: `kolam_darah` mendapat titik `ujian_darah`; `altar_iblis` dipakai sebagai `altar_sumpah`.

`tools/gen_sekte_gerbang.py` (41W): titik `sektenpc/ujian_bawah`, `ujian_atas` di tangga gerbang, `sektenpc/loket` (meja
pendaftaran di aula gerbang), `sektenpc/bilik_0..1` (kamar menara, kediaman cadangan), `sektenpc/altar` untuk sekte tanpa
kompleks.

Validasi: setiap titik baru tercakup uji `uji gabung titik <sekte>` (mencetak semua titik dimuat/tidak) dan harness
`scratchpad/final/wg/run_38.sh` (TplCheck `structure/kompleks_sekte/**`, `structure/sekte/**`).

#### 41.11.3 Entitas baru (41B/41C; semua `noSave`, didaftarkan selalu, muncul hanya saat dipakai)

| Entitas | Ukuran | Perilaku | Model/tekstur (41V) |
|---|---|---|---|
| `kultivator:boneka_ujian` (`BonekaUjianEntity`) | 0.6 × 1.8 | diam, menyala (glow quad) saat aktif, menerima pukulan, tidak bisa mati | boneka kayu jerami bertiang, 64 × 64 |
| `kultivator:dinding_ilusi` (`DindingIlusiEntity`) | 1 × 3 × 1 kolider | pola `ColliderEntity`; hilang saat ujian selesai | tak terlihat; kabut klien + trigram |
| `kultivator:pilar_qi` (`PilarQiEntity`) | 0.8 × 3 | penanda Lima Pilar, cahaya unsur | pilar batu ukir 5 warna unsur |
| `kultivator:cawan_ujian` (`CawanUjianEntity`) | 0.5 × 0.4 | klik = minum | cawan perunggu + uap |
| `kultivator:panji_kelas` (gambar klien, bukan entitas) | — | 41.4.6 | atlas lambang 40/36 S |

Biksu perunggu, pemburu, regu perang = `KultivatorNpc`/RIVAL yang sudah ada dengan rupa (`SosokApi.setRupa`, kunci baru
`biksu_perunggu`, `pemburu_sekte`), bukan entitas baru.

#### 41.11.4 Item baru (41A/41B; model + tekstur 41V lewat `tools/gen_gabung_assets.py`, `tools/png.py`)

| Item | Id | Ikon | Catatan |
|---|---|---|---|
| Lencana Anggota | `kultivator:lencana_anggota` | giok persegi berlambang (tint warna sekte, 3 bingkai menurut kelompok pangkat) | komponen `kultivator:anggota` |
| Tanda Calon | `kultivator:tanda_calon` | giok kecil polos | hilang saat status berakhir |
| Token Naik Sekte | `kultivator:token_naik` | lempeng emas awan | komponen opsional `kultivator:sekte_token` |
| Surat Undangan | `kultivator:surat_undangan` | gulungan bersegel | membuka pintu UNDANGAN |
| Surat Restu | `kultivator:surat_restu` | gulungan merah berstempel | 41.7.1 |
| Batu Pikul | `kultivator:batu_pikul` | batu bertali | hanya ujian TUBUH |
| Ember Pikul | prop 152 (klip), bukan item | — | tugas pikul air |

#### 41.11.5 Rupa: seragam per sekte dan jubah pemain (41V, memakai sistem 40)

* **NPC dan tokoh**: kunci rupa baru di `assets/kultivator/sosok/resep.json` (generator 40K `tools/gen_sosok_resep.py`, 41V
  menambah baris aditif): `tetua_sekte.<corak>`, `murid.<pangkat>.<corak>`, `biksu_perunggu`, `pemburu_sekte`,
  `ketua_sekte.<corak>`; warna dari lambang sekte (40.3.8 butir 1), gaya per corak (41.4.3 kolom gaya), hiasan per pangkat
  (40.4.2). Contoh: Tetua Obat corak ALKIMIA = `HANFU_L` + `jubah_luar` + celemek tungku gelap, guan giok, liontin labu.
* **Pemain anggota**: lapisan kosmetik `client/sekte/gabung/JubahSekteLayer` (render layer pemain; daftar lewat
  `LivingEntityFeatureRendererRegistrationCallback`, verifikasi nama 26.3) menggambar jubah luar setengah-panjang dengan bagian
  kubus 40K (`jubah_luar`, `sabuk`, `kerah_tinggi`) ditint warna lambang + tepi `SectRanks.TRIM` pangkat + lambang 8 × 8 di
  punggung (Inti+). Tampil di atas zirah bila pengaturan klien `gabung.jubahDiAtasZirah` (bawaan true); disembunyikan saat
  `Hancur Total`/film. Pemain lain melihatnya lewat payload `gabung_lencana` (41.9). LOD: > 32 blok tanpa lambang, > 64 tidak
  digambar. Minimal: tanpa lapisan.
* Nametag anggota: prefix pangkat berwarna tepi ("[Dalam] Han Feiyu") di atas kepala lewat penyedia nama yang ada
  (verifikasi; bila tidak ada kait klien, hanya di daftar tab pemain tidak diubah — cukup teks billboard kecil ≤ 16 blok).

#### 41.11.6 Animasi (klip 300-349, prop 150-169; `client/sekte/gabung/gerak/GabungPoses` lewat `GerakKatalog.daftar`)

| Id | Klip | Pemakai | Prop |
|---|---|---|---|
| 300 | `sumpah_berlutut` (berlutut satu lutut, tangan terkatup) | pemain, calon NPC | — |
| 301 | `dupa_tiga` (angkat 3 dupa ke dahi, tancapkan) | pemain | 150 dupa_sumpah |
| 302 | `kowtow` (3 kali sujud) | pemain, murid NPC | — |
| 303 | `terima_lencana` (dua tangan menerima, membungkuk) | pemain | 151 lencana_anggota |
| 304 | `salam_sekte` (kepalan-telapak setinggi dada) | semua anggota NPC | — |
| 305 | `dengar_ceramah` (duduk bersila, angguk) | pemain, murid NPC | — |
| 306 | `pikul_air` (pikulan di bahu, jalan berayun) | pemain, Pelayan NPC | 152 ember_pikul |
| 307 | `kipas_tungku` (jongkok mengipas) | pemain, NPC | 154 kipas_bambu |
| 308 | `pamit_bungkuk` (bungkuk dalam, mundur 3 langkah) | pemain | — |
| 309 | `diusir_tunduk` (kepala tertunduk, bahu turun, jalan pelan) | pemain | — |
| 310 | `tetua_menilai` (mengelus janggut, mata menyipit) | tetua NPC | — |
| 311 | `tetua_menunjuk_aturan` (membuka gulungan, menunjuk) | Tetua Hukum | 155 gulungan_aturan |
| 312 | `penjaga_memanggil` (tangan ke mulut, berseru) | penjaga gerbang | — |
| 313 | `tebasan_ujian` (tebasan cepat dua arah) | murid NPC pengajar | 156 pedang_kayu |
| 314 | `biksu_kuda_kuda` (kuda-kuda perunggu, napas) | biksu perunggu | — |
| 315 | `renungan` (duduk menghadap dinding) | pemain di Gua Renungan | — |
| 316 | `serah_jubah_mangkuk` (Ketua lama menyerahkan) | Ketua NPC | 153 mangkuk_ketua |
| 317 | `terima_jubah_mangkuk` | pemain | 153 |
| 318 | `lelah_berlutut` (jatuh berlutut di tangga, terengah) | calon NPC, pemain saat Tekanan 100 | — |
| 319 | `sorak_murid` (angkat kepalan) | penonton turnamen | — |

Pemain: klip pemain lewat jalur `PlayerModelMixin`/`DutyPoses` 40G (verifikasi titik pemain; 37 dan 40 sudah memose pemain di
upacara), selama upacara saja. Semua klip ≤ 3 s per siklus, pegas 40 aktif; Minimal = pose statis.

#### 41.11.7 Suara Jepang (VOICEVOX; 41V menambah ke `tools/gen_voices.py` `WORLD`, grup `gabung.*` di `VoiceEvents`)

| Kunci | Suara | Jepang | Teks id | Emosi |
|---|---|---|---|---|
| `gabung.gerbang_panggil` | m1 | 入門試験を受ける者は、前へ出よ！ | Yang ikut ujian masuk, maju! | angry |
| `gabung.tangga_tekanan` | elder | 心を静めよ。山はお前を試している。 | Tenangkan hatimu. Gunung sedang mengujimu. | calm |
| `gabung.ujian_mulai` | m2 | 試験、始め！ | Ujian, mulai! | normal |
| `gabung.ujian_lulus` | elder | ふむ…悪くない。 | Hmm... tidak buruk. | calm |
| `gabung.ujian_gagal` | elder | 縁がなかったようだな。三日後にまた来い。 | Tampaknya belum berjodoh. Datanglah tiga hari lagi. | calm |
| `gabung.diterima` | elder | 今日より、お前は我が宗門の弟子だ。 | Mulai hari ini, kau murid sekte kami. | joy |
| `gabung.istimewa` | elder | これほどの才…誰が弟子に取るか、揉めそうだな。 | Bakat sebesar ini... para tetua akan berebut. | joy |
| `gabung.sumpah_aturan_1` | elder | 同門を害するなかれ。 | Jangan mencelakai saudara seperguruan. | calm |
| `gabung.sumpah_aturan_2` | elder | 宗門の秘伝を漏らすなかれ。 | Jangan membocorkan warisan rahasia sekte. | calm |
| `gabung.sumpah_aturan_3` | elder | 宗門の危機には、必ず駆けつけよ。 | Saat sekte dalam bahaya, datanglah tanpa ragu. | calm |
| `gabung.shifu_pilih` | elder | わしの弟子にならぬか？ | Maukah kau menjadi muridku? | joy |
| `gabung.shifu_lindungi` | elder | わしの弟子に手を出すとは、いい度胸だ。 | Berani sekali menyentuh muridku. | angry |
| `gabung.shifu_lampaui` | elder | はっはっは！弟子が師を超えたか！ | Hahaha! Murid sudah melampaui guru! | joy |
| `gabung.sapa_adik` | f1 | 師弟、おはよう！ | Adik seperguruan, selamat pagi! | joy |
| `gabung.sapa_kakak` | m3 | 師兄、お疲れ様です。 | Kakak senior, terima kasih atas kerja kerasnya. | normal |
| `gabung.saingan_ejek` | m3 | 雑用係がここで何をしている？ | Pesuruh sedang apa di sini? | angry |
| `gabung.panggilan_perang` | m1 | 敵が山門に迫っている！弟子たちよ、集え！ | Musuh mendekati gerbang! Para murid, berkumpul! | angry |
| `gabung.hukum_tegur` | elder | 門規を忘れたか。 | Sudah lupa aturan sekte? | angry |
| `gabung.hukum_kurung` | elder | 思過崖で己を省みよ。 | Renungkan dirimu di Tebing Renungan. | calm |
| `gabung.usir` | elder | 今日をもって、お前を破門とする。二度と我が名を口にするな。 | Mulai hari ini kau diusir. Jangan pernah sebut namaku lagi. | angry |
| `gabung.pamit` | elder | 行くがよい。道は一つではない。 | Pergilah. Jalan tidak hanya satu. | calm |
| `gabung.pemburu` | m1 | 裏切り者め、宗門の名において捕らえる！ | Pengkhianat, atas nama sekte kau kutangkap! | angry |
| `gabung.ketua_serah` | boss | この衣と鉢を、お前に託そう。 | Jubah dan mangkuk ini, kuserahkan padamu. | calm |
| `gabung.ketua_baru` | m2 | 新宗主に、拝礼！ | Hormat kepada Ketua baru! | joy |
| `gabung.tamu_sambut` | m2 | 客卿長老、ようこそお越しくださいました。 | Selamat datang, Tetua Tamu. | normal |
| `gabung.naik_kelas` | heaven | 一宗、天に名を連ねる。 | Satu sekte mencatatkan namanya di langit. | normal |

26 baris; `en` caption ditulis 41V (contoh: "Those taking the entrance exam, step forward!"). Sebelum klip dibuat: cadangan
`kotabesar.recruit_call`, `elder.welcome`, suara murid `greet`/`cheer`/`farewell` lewat `SekteSuara.line`. Generasi VOICEVOX
hanya oleh agen finish F41.

---------------------------------------------------------------------------------------------------------------------

### 41.12 Data, sinkron dan kinerja

#### 41.12.1 Data

* **`GabungData`** (SavedData `kultivator:gabung`, satu per dunia; 41A):
  * `anggota.<uuid>` = `Keanggotaan`: `sekte` (s<i> atau ""), `status` (`BEBAS|CALON|ANGGOTA|MENYAMAR`), `pangkat`, `jalur`
    (`MURID|TAMU`), `pk`, `pkTotal`, `pkBulan`, `hariMasuk`, `hariPangkat`, `shifu` (tokohId), `ikatanShifu`, `saingan`,
    `kakak`, `pelanggaran`, `kurunganSampai` (tick), `kediaman` (titik, `sewaSampai`), `tugas` (hari, jumlah), `misi` (≤ 5 id
    aktif), `jatahHari`, `warisan` (spec id), `muridTitipan` (≤ 5 jiwa), `dikenal` (bitset), `jedaLamar.<s>`,
    `riwayatSekte.<s>` (`PAMIT|PAMIT_PAKSA|DIUSIR|PEMBELOT`, hari, `pkBeku`), `riwayat` (20 baris kunci+isian),
    `pemburu` (sampai, menang), `samaran` (MENYAMAR: sekte kedua, sampai).
  * `sekte.<s>` = `DataSekteGabung`: `papanHari` + `papan` (misi diundi), `kursi` (pangkat → daftar pemegang UUID/tokohId),
    `pewaris`, `wakil`, `ketuaPemain`, `perang` (lawan, skor, sampai, misi), `kuotaHari`, `kas` (sumbangan per UUID bulan ini),
    `merosot`, `lebur`, `turnamenHari` + bracket, `serbuanHari`, `cabang` (daftar UUID aula).
* **`TokohSekte`** (SavedData `kultivator:gabung_tokoh`; 41W): tetua dan ketua per sekte (41.6.12).
* Status murid titipan disimpan di `Keanggotaan` (tidak menyentuh `DiscipleData`).
* Semua data hidup di sisi server; batas: ≤ 12 sekte × ≤ 7 tetua, papan ≤ 9 misi per sekte, riwayat 20 baris.

#### 41.12.2 Sinkron

| Payload | Arah | Isi | Kapan | Batas |
|---|---|---|---|---|
| `kultivator:gabung_lencana` | S2C | UUID → sekte, pangkat, warna, lambang packed | pemain masuk 64 blok, berubah | ≤ 64 entri |
| `kultivator:gabung_kelas` | S2C | per sekte dikenal: kelas, tingkat, aliran, corak, warna | login + `KELAS_BERUBAH` | ≤ 12 × 12 byte |
| `kultivator:gabung_ujian` | S2C | tahap, tekanan, waktu, skor, data mini (pilar/labirin) | tiap 5 tick selama ujian | ≤ 64 byte |
| layar | `TempatLayar` (38) | lembar per layar | buka | ≤ 32 KB |

#### 41.12.3 Kinerja (laptop lemah)

* Tanpa tick per anggota: pembaruan malas `Keanggotaan.sentuh(player, hari)` saat login, saat masuk wilayah kompleks, dan saat
  layar dibuka (jatah, sewa, susut pelanggaran, pemburu). Satu tugas harian server per sekte (`GabungHarian`, satu sekte per
  20 tick bergiliran: papan, perang, kuota, turnamen, serbuan, kursi) — O(sekte).
* Tubuh: memakai slot 38 (`kompleksSekte.tubuhMaks` 12 per kompleks) — tokoh menggantikan tubuh generik, bukan menambah.
  Ujian: maks 6 boneka, 48 dinding ilusi, 2 biksu sekaligus, 1 ujian per sekte; perang/serbuan: maks 6 regu hidup per gerbang;
  pemburu maks 4. Total entitas 41 per server ≤ 24 (`DuniaBudget.setCap("gabung", 24)`), MSPT guard 45 ms menunda kemunculan.
* Klien: panji kelas ≤ 8 per sekte, kubah satu quad bola 64 segi (Normal) / 24 (Minimal: tidak ada), anak tangga ilusi menurut
  `EffectQuality` (120/400/1000), jubah pemain LOD 41.11.5. Tidak menggambar saat `ShaderCompat.shadowPass()`.
* `uji gabung anggaran` mencetak waktu tugas harian (< 0.3 ms per sekte) dan jumlah entitas.

---------------------------------------------------------------------------------------------------------------------

### 41.13 Config, perintah, panduan, advancement

#### 41.13.1 Config (`SekteConfig.section("gabungSekte", GabungConfig.class, GabungConfig::new, GabungConfig::clamp)`,
`config/kultivator-sekte.json`)

| Field | Bawaan | Rentang | Arti |
|---|---|---|---|
| `aktif` | true | – | sakelar utama (registri tetap) |
| `sebaran` | `seimbang` | `seimbang/alami/agung/kecil/datar` | 41.4.2 |
| `tingkatDatar` | 3 | 1-9 | preset `datar` |
| `tingkatMaks` | 9 | 1-9 | batas tumbuh |
| `hariUjian` | 10 | 3-30 | jeda Hari Ujian per sekte |
| `jedaLamarHari` | 3 | 0-15 | — |
| `waktuTangga` | 300 | 120-600 | detik tahap 1 |
| `skalaPk` | 1.0 | 0.25-4 | pengali semua PK masuk |
| `skalaBiaya` | 1.0 | 0.25-4 | pengali biaya promosi dan sewa |
| `usiaTulang` | true | – | syarat Usia Tulang |
| `syaratInfami` | true | – | syarat Infami |
| `bolehCabang` | true | – | 41.8.2 |
| `bolehTamu` | true | – | 41.8.3 |
| `bolehSuksesi` | true | – | menjadi Ketua |
| `bolehLebur` | true | – | 41.8.6 |
| `bolehMerosot` | true | – | status MEROSOT |
| `pemburu` | true | – | pemburu pembelot |
| `pemburuHari` | 30 | 5-120 | lama perburuan |
| `hapusWarisanTertangkap` | true | – | 41.7.4 |
| `serbuanKompleks` | true | – | 41.6.10 |
| `serbuanHari` | 12 | 4-60 | — |
| `perangPanggil` | true | – | Panggilan Perang |
| `turnamenHari` | 15 | 5-60 | Turnamen Murid |
| `kuota` | true | – | Kuota Alam |
| `tubuhMaks` | 24 | 8-48 | entitas 41 per server |
| `jubahPemain` | true | – | sinkron jubah ke pemain lain |

Klien (`config/kultivator-client.json`, grup Tampilan 33.5): `gabung.jubahDiAtasZirah` (true), `gabung.visualKelas` (true),
`gabung.tanggaIlusi` (true), `gabung.nametagPangkat` (true).

#### 41.13.2 Perintah pemain (`DuniaCommands.player("gabung", ...)`, 41A)

`/kultivasi gabung direktori | info <sekte> | lamar <sekte> [gerbang|token|undangan|pelayan|tamu] | batal | status | kartu |
papan | paviliun | kediaman | pangkat | naik | tantang <kursi> | jatah | shifu | politik | sumbang <jumlah NB> | pamit |
daftarkan <murid> | lepaskan <murid> | tamu terima|tolak`. Semua membuka layar yang sama atau menjalankan aksi yang sama dengan
tombol (satu penangan).

#### 41.13.3 Perintah uji (`DuniaCommands.test("gabung", GabungUji::node)`, op 2; chip `gabung` di `DuniaCommands.CHIPS` +
grup `command/CheatHelp` "gabung sekte")

| Sub | Fungsi |
|---|---|
| `daftar` | semua sekte: kelas, tier, corak, aliran, standing, kompleks lahir/tidak, istana awan, anggota pemain |
| `sebaran [preset]` | cetak pemetaan tier awal preset untuk seed ini + bukti urutan sama dengan `LangitNpcPlan` |
| `kelas <sekte> <1-9>` | set tier (lewat `DiplomasiGabung`), kabar dan visual berubah |
| `tp <sekte> [gerbang|aula|jasa|arena|puncak|ujian|kediaman <n>|renungan]` | teleport penguji ke titik |
| `titik <sekte>` | semua titik 41 (dimuat/tidak, posisi) |
| `kenal <sekte|semua>` | buka Direktori |
| `ujian <sekte> [tahap 1|2|3] [corak <id>]` | mulai ujian sekarang (lewati Hari Ujian) |
| `ujian lulus|gagal` | akhiri tahap berjalan |
| `terima <sekte> <pangkat>` | langsung anggota (tanpa ujian, dengan upacara singkat) |
| `sumpah` / `shifu [pilih <n>]` | jalankan upacara |
| `pangkat <pangkat>` / `pk <n>` / `ikatan <n>` | set nilai |
| `misi [isi|selesai <id>]` | papan: isi ulang / selesaikan |
| `tugas <id>` | mulai tugas harian |
| `jatah` / `sewa lewat` | paksa jatah / sewa jatuh tempo |
| `kuota bagi` | bagi kuota sekarang |
| `turnamen [mulai|babak lewati]` | Turnamen Murid |
| `perang <lawan>` / `perang skor <n>` / `perang akhir` | Perang NPC + Panggilan Perang |
| `serbuan [live]` | serbuan kompleks sekarang |
| `langgar <aturan>` / `kurung <detik>` / `sidang` | aturan dan hukuman |
| `pamit [restu]` / `usir` / `belot` | keluar |
| `pemburu` | regu pemburu sekarang di dekat penguji |
| `tamu` / `cabang` | jadikan Tetua Tamu / aula penguji Cabang |
| `suksesi [bab 1|2|3]` / `ketua` | jalankan suksesi / langsung Ketua |
| `serap` / `lebur` / `pisah` | penyatuan |
| `merosot` / `bangkit` | keadaan sekte |
| `dua_topeng` | mulai rantai penyusupan |
| `tokoh <sekte>` | daftar tetua, ketua, roster yang dipakai |
| `anggaran` | waktu dan entitas (41.12.3) |
| `reset [semua]` | hapus keanggotaan penguji (atau semua) |

#### 41.13.4 Kitab Panduan bab 22 "Bergabung dengan Sekte" (41V, `guide/GuideChapters`, `CHAPTER_COUNT` 21 → 22)

Langkah ditambah **di akhir** daftar (`step(21, nama, petunjuk, cek)`; `GATES`/`DISPLAY_ORDER` diperpanjang, bab 22
ditampilkan setelah bab sekte 15/16 di urutan tampilan): `kenal` (`adv:sekte/gabung_kenal`), `lamar`
(`adv:sekte/gabung_lamar`), `ujian` (`adv:sekte/gabung_diterima`), `sumpah`, `shifu`, `tugas` (`adv:sekte/gabung_tugas`),
`paviliun`, `misi` (`adv:sekte/gabung_misi_10`), `kediaman`, `pangkat_dalam`, `turnamen`, `perang`
(`adv:sekte/gabung_bela_gerbang`), `kuota`, `inti`, `jalan_dua` (`adv:sekte/gabung_cabang|adv:sekte/gabung_tamu`), `ketua`.
Teks `kultivator.guide.c22.<nama>.title|body`, `kultivator.guide.chapter.22` + `.desc` di `lang-src/sekte_gabung_panduan.*`:
menjelaskan dua jalan, kelas dan corak, lima pintu, ujian, PK pribadi = PK yang sama (1 PK = 2 NB, tidak bisa ditukar), aturan
dan hukuman, pamit/diusir/membelot, Cabang/Tamu/suksesi. Bab PANDUAN.md baru "69. Bergabung dengan Sekte" (agen finish, CRLF).

#### 41.13.5 Advancement (`data/kultivator/advancement/sekte/gabung_*.json`, pola `docs/ADVANCEMENT_TEMPLATE.json`, parent
`kultivator:root`, `minecraft:impossible`, `Advancements.grant(player, "sekte/gabung_<nama>")`)

| Nama | Ikon | Kapan |
|---|---|---|
| `gabung_kenal` | `minecraft:spyglass` | sekte pertama masuk Direktori |
| `gabung_lamar` | `minecraft:paper` | lamaran pertama |
| `gabung_diterima` | `minecraft:emerald` | diterima |
| `gabung_istimewa` | `minecraft:nether_star` | Calon Istimewa |
| `gabung_sumpah` | `minecraft:candle` | Upacara Sumpah |
| `gabung_shifu` | `minecraft:book` | memilih Shifu |
| `gabung_tugas` | `minecraft:bucket` | 10 tugas harian |
| `gabung_misi_10` | `minecraft:map` | 10 misi papan |
| `gabung_paviliun` | `minecraft:enchanted_book` | teknik pertama dari Paviliun |
| `gabung_kediaman` | `minecraft:campfire` | gua kediaman pertama |
| `gabung_pangkat_luar` / `_dalam` / `_inti` | `minecraft:iron_ingot` / `gold_ingot` / `diamond` | naik pangkat |
| `gabung_turnamen` | `minecraft:golden_sword` | juara Turnamen Murid |
| `gabung_bela_gerbang` | `minecraft:shield` | menang Bela Gerbang |
| `gabung_kuota` | `minecraft:ender_eye` | memakai kuota alam |
| `gabung_melampaui` | `minecraft:experience_bottle` | melampaui Shifu |
| `gabung_pewaris` | `minecraft:golden_helmet` | menjadi Pewaris |
| `gabung_tetua` | `minecraft:lectern` | menjadi Tetua |
| `gabung_ketua` | `minecraft:beacon` | menjadi Ketua |
| `gabung_tamu` | `minecraft:goat_horn` | Tetua Tamu |
| `gabung_cabang` | `minecraft:white_banner` | aula menjadi Cabang |
| `gabung_serap` | `minecraft:lodestone` | menyerap sekte NPC |
| `gabung_pamit` | `minecraft:feather` | pamit dengan Restu |
| `gabung_diusir` | `minecraft:cracked_stone_bricks` | diusir |
| `gabung_pembelot` | `minecraft:wither_rose` | membelot |
| `gabung_kembali` | `minecraft:recovery_compass` | kembali ke sekte lama |
| `gabung_bangkit` | `minecraft:totem_of_undying` | Gunung yang Bangkit |
| `gabung_dua_topeng` | `minecraft:carved_pumpkin` | menyelesaikan penyusupan |

#### 41.13.6 Lang (fragmen 41; kunci identik id_id/en_us)

`sekte_gabung` (umum, kelas, corak, pangkat, aturan, pesan, Sumber), `sekte_gabung_masuk` (41B: pintu, ujian, wawancara),
`sekte_gabung_hidup` (41C: papan, tugas, paviliun, kediaman, jatah, kuota, turnamen), `sekte_gabung_politik` (41D),
`sekte_gabung_dunia` (41W: tokoh, gelar, kabar, lore tempat), `sekte_gabung_cerita` (41W: rantai quest), `sekte_gabung_ui`
(41U), `sekte_gabung_suara` (dibuat `gen_voices.py`, 41V), `sekte_gabung_panduan` (41V).

---------------------------------------------------------------------------------------------------------------------

### 41.14 Modul, kepemilikan berkas dan urutan

#### 41.14.1 Tujuh modul (berkas baru milik penuh, tidak bertumpuk)

| Label | Lingkup | Berkas baru (milik penuh) |
|---|---|---|
| **41A** Inti & Keanggotaan | 41.3, 41.4.1, 41.4.3-41.4.4 data, 41.6.1, 41.6.2 pintu PK, 41.6.11, 41.7, 41.12.1-41.12.2, 41.13.1-41.13.3 kerangka | `sekte/gabung/{Gabung (init), GabungConfig, GabungData, Keanggotaan, GabungHarian, GabungBus, GabungPayloads, GabungCommands, GabungUji (akar node + daftar sub dari modul lain lewat `GabungUji.daftar(sub, node)`), Aturan, BalaiHukum, Keluar, Pemeriksa}`, `sekte/gabung/api/{GabungApi, KelasSekte, Corak, Pangkat, Pintu, SyaratMasuk, StatusAnggota}`, `client/sekte/gabung/GabungClient.java` (init + stub init modul klien lain), lang `sekte_gabung.*`, advancement JSON (semua, dibuat sekaligus) |
| **41B** Masuk & Ujian | 41.5 | `sekte/gabung/masuk/{Direktori, Lamaran, Perekrut (PenyediaJasa), HariUjian, TanggaUjian, UjianCorak, UjianHati, Putusan, UpacaraSumpah, PilihShifu, TokenNaik}`, `sekte/gabung/masuk/corak/{Pedang, Buddha, Binatang, Formasi, Unsur, Alkimia, Tubuh, Darah, Racun, Bayangan}.java`, entitas `{BonekaUjianEntity, DindingIlusiEntity, PilarQiEntity, CawanUjianEntity}` + `GabungEntities`, item `GabungItems` (semua item 41.11.4), data `tempat/dialog/gabung_wawancara_*.json`, lang `sekte_gabung_masuk.*` |
| **41C** Hidup Anggota | 41.6.2-41.6.9, 41.6.14 | `sekte/gabung/hidup/{PapanMisiSekte, JenisMisiGabung, TugasHarian, PaviliunJasa, StokPaviliun, Kediaman, KolamRoh, Jatah, KuotaAlam, TurnamenMurid, Pelajaran, SitusGabung (penyedia SpiritSites), NamaSekte}`, data `tempat/quest/gabung_tugas_*.json`, lang `sekte_gabung_hidup.*` |
| **41D** Politik & Perang | 41.4.2, 41.4.7, 41.6.10, 41.7.3 pemburu, 41.8, 41.9 politik | `sekte/gabung/politik/{Kursi, Suksesi, PemilihanPewaris, PanggilanPerang, MisiPerang, SerbuanKompleks, PemburuSekte, Cabang, TetuaTamu, Penyatuan, PeringkatSekte, KasSekte}`, **`sekte/diplomasi/DiplomasiGabung.java`**, lang `sekte_gabung_politik.*` |
| **41W** Dunia & Tokoh | 41.6.12-41.6.13, 41.11.1-41.11.2, jadwal tokoh | `sekte/gabung/dunia/{TokohSekte, TokohAnchors (titik baru, tubuh tokoh), TitikGabung, CeritaGabung (Syarat/Akibat/Tujuan), KabarGabung}`, data `tempat/quest/gabung_*.json` (rantai), `tempat/kabar/gabung_*.json`, `tempat/jadwal/kompleks_sekte_tokoh.json`, lang `sekte_gabung_dunia.*`, `sekte_gabung_cerita.*`; NBT dibuat ulang `structure/kompleks_sekte/**`, `structure/sekte/**` |
| **41U** Layar | 41.10 | `sekte/gabung/ui/{GabungLembar, LembarDirektori, LembarKartu, LembarPaviliun, LembarPapan, LembarPangkat, LembarKediaman, LembarPolitik, LembarLamaran}` (pembangun lembar; penangan aksi memanggil API 41A-41D), `client/sekte/gabung/ui/**` (layar + `GabungHub` kartu hub + HUD ujian), `tools/gen_gabung_ui.py` (ikon pangkat, panel aliran, stempel), `textures/gui/gabung/**`, lang `sekte_gabung_ui.*` |
| **41V** Visual, Suara, Panduan | 41.4.6 visual, 41.11.3-41.11.7, 41.13.4 | `client/sekte/gabung/{fx/VisualKelas, fx/TanggaIlusiGabung, fx/UjianFx, JubahSekteLayer, gerak/GabungPoses, model/*Renderer}`, `tools/gen_gabung_assets.py` (model item/entitas, tekstur, prop 150-169), `assets/kultivator/{models,textures}/**/gabung/**`, lang `sekte_gabung_panduan.*` |

#### 41.14.2 Pemilik berkas lama (satu pemilik selama 41; semua suntingan aditif)

| Berkas lama | Pemilik | Suntingan |
|---|---|---|
| `sect/hall/Halls.java` | 41A | `FoundVeto` + `addFoundVeto` + satu panggilan di jalur pendirian (41.3.3) |
| `sekte/SekteContent.java`, `sekte/SekteIds.java` | 41A | `Gabung.init()`; `SekteIds.GABUNG = "gabung"` (tidak masuk `MODULES` 36) |
| `client/sekte/SekteContentClient.java` | 41A | `GabungClient.init()` |
| `dunia/DuniaCommands.java`, `command/CheatHelp.java` | 41A | chip `gabung …`, grup cheat |
| `sekte/mandiri/seteru/Roster.java` | 41A | pembaca publik `anggota`, `sikap`, `ubahSikap` (UUID pemain) |
| pemilik `musuh/Ambushes` (penyedia tekanan) | 41A | satu penyedia faktor sergap bila tidak ada kait publik (41.4.5) |
| API pelupaan teknik (bila belum ada) | 41A | `forget(specId)` (41.7.4) |
| `sekte/diplomasi/SekteNpc.java` | 41D | satu baris `tier = DiplomasiGabung.tingkatAwal(e)` di `founded` |
| `sekte/diplomasi/Aksi.java` | 41D | satu baris veto (41.8.4) |
| `sekte/diplomasi/Simulasi.java` | 41D | satu baris `if (!DiplomasiGabung.bolehTumbuh(sekte)) return` sebelum naik tingkat (batas `tingkatMaks`) |
| `dunia/tempat/kompleks/KompleksAnchors.java` | 41W | titik baru di `TITIK` |
| `tools/gen_tempat_kompleks.py`, `tools/gen_sekte_gerbang.py` | 41W | lot/titik 41.11.2 |
| `assets/kultivator/sosok/resep.json` lewat `tools/gen_sosok_resep.py` | 41V | kunci rupa 41.11.5 |
| `guide/GuideChapters.java` | 41V | bab 22 |
| `tools/gen_voices.py`, `voice/VoiceEvents.java` | 41V | 26 baris, grup `GABUNG` (generasi hanya F41) |
| `dunia/DuniaMarkers.java` (+ `markers_tempat.png` bila ikon baru) | 41W | penanda `gabung/sekteku` (ikon gerbang sekte yang ada, warna sekte) |
| `docs/DESIGN.md`, `docs/PANDUAN.md`, `README` | F41 | rujukan + bab 69 |

Semua berkas 36/37/38/39/40 lain baca saja; yang dibutuhkan masuk "Butuh dari modul lain" di berkas progres.

#### 41.14.3 Kontrak antarmodul (nama tetap; stub di langkah 1 41A)

* **C41.A1** `GabungApi` (statis, server): `Keanggotaan anggota(ServerPlayer|UUID)`, `@Nullable String sekte(UUID)`,
  `Pangkat pangkat(UUID)`, `boolean anggota(UUID, String sekte)`, `void tambahPk(ServerPlayer, int, String sebab)`,
  `boolean bayarPk(ServerPlayer, int, String sebab)`, `void langgar(ServerPlayer, Aturan, BlockPos at, boolean perluSaksi)`,
  `void terima(ServerPlayer, String sekte, Pangkat, Pintu)`, `void keluar(ServerPlayer, StatusKeluar)`,
  `int kelas(String sekte)`, `Corak corak(String sekte)`, `@Nullable BlockPos titik(String sekte, String titik)` (kompleks →
  gerbang → kota), `void daftarUji(String sub, Supplier<ArgumentBuilder>)`.
* **C41.A2** `GabungBus`: `DITERIMA(player, sekte, pangkat)`, `PANGKAT_NAIK(player, sekte, lama, baru)`,
  `KELUAR(player, sekte, status)`, `PK_BERUBAH`, `KELAS_BERUBAH(sekte, lama, baru)`, `PERANG_MULAI/AKHIR(sekte, lawan, menang)`,
  `KETUA_BARU(sekte, uuid|tokoh)`.
* **C41.B1** `Lamaran.buka(player, sekte)`, `HariUjian.mulai(player, sekte, Pintu)`, `PilihShifu.mulai(player)`.
* **C41.C1** `PaviliunJasa.lembar(player, sekte)`, `Kediaman.milik(player)`, `KuotaAlam.bagi(server, sekte)`,
  `TurnamenMurid.mulai(server, sekte)`.
* **C41.D1** `DiplomasiGabung.{tingkatAwal(Entry), bolehTumbuh(SekteNpc), ubahSumber(server, sekte, n, sebab), setTier,
  setStanding(server, uuid, sekte, v), veto(owner, sekte, aksi), paktaCabang, lebur(sekte), apply(server, sekte, Peristiwa)}`;
  `Kursi.{pemegang, tantang}`, `Suksesi.bab(player)`.
* **C41.W1** `TokohSekte.{tetua(server, sekte), jabatan(server, sekte, Jabatan), ketua, tokoh(id), ubahSikap(id, uuid, d)}`,
  `TokohAnchors.tubuh(level, sekte, tokohId)`; titik lewat `TitikGabung`.
* **C41.U1** kunci lembar di `GabungLembar` (javadoc per layar) dibekukan di akhir langkah 1.
* **C41.V1** id klip 300-319 (cadangan sampai 349) dan prop 150-169 dibekukan di langkah 1 (`GabungGerakId` di
  `sekte/gabung/api`, aman di dua sisi).

#### 41.14.4 Urutan kerja

```
Prasyarat: 38/38.T, 39/39.F, 40 F3 hijau dan di-commit
  |
G0  41A langkah 1 (semua api/*, stub init semua paket server+klien, GabungData kosong, payload, config, advancement JSON,
    GabungLembar kunci, GabungGerakId, suntingan berkas lama 41A)        -> F41-0: build B0
  |
G1  41A (sisa) | 41B | 41C | 41D | 41W | 41U | 41V   (paralel; kontrak C41.*; berkas tidak bertumpuk)
  |  41W menjalankan gen_tempat_kompleks.py + gen_sekte_gerbang.py, cek_tempat.py, membaca pratinjau
  |  41V menjalankan gen_gabung_assets.py, gen_sosok_resep.py, membaca pratinjau PNG
  v
F41 finish: terapkan "Butuh dari modul lain", generator semua, VOICEVOX 26 baris, cek_tempat/cek_sosok, harness
    final/wg/run_all.sh + run_38.sh (kompleks + gerbang) + langitG/run_fin.sh (istana awan tetap 2 per seed dengan sebaran),
    mergeLang tanpa ganda, build B1 (`compileJava compileClientJava jar --no-daemon`), docs (DESIGN/PANDUAN/README,
    docs/uji/FASE-41.md)
  |
D41 detail pass (opsional, 4 agen: D-41B ujian per corak, D-41W tokoh & cerita, D-41U layar skala 2/3/4, D-41V visual)
```

Ketergantungan keras: 41B/41C/41D butuh `TokohSekte` (41W) hanya lewat kontrak C41.W1 (stub di G0); 41U butuh lembar dari
41B-41D (kunci dibekukan G0); 41V butuh tidak ada kecuali 40 (selesai). Progres: `docs/progres/gabung-41A0.md`,
`gabung-41A.md`, `gabung-41B.md`, …, `gabung-F41.md`.

---------------------------------------------------------------------------------------------------------------------

### 41.15 Keputusan (DIPUTUSKAN 2026-10-24: pengguna memilih semua saran bawaan yang dicetak tebal)

| # | Pertanyaan | Pilihan | Saran bawaan |
|---|---|---|---|
| K1 | Murid rendah (Pelayan-Dalam) boleh punya aula sendiri? | boleh bebas / **tidak, harus Pamit atau Cabang (Inti+)** | tidak (aula dorman + dialog) |
| K2 | Sebaran tingkat sekte di dunia baru | **`seimbang` (1 Agung t8, 1 Agung t7, sisanya Besar-Kecil)** / `alami` (1-5) / `agung` / `kecil` / `datar` | `seimbang` |
| K3 | Pembelot diburu? Kalah dari pemburu = | mati / **ditangkap (tidak mati, Teknik Warisan dihapus, kurungan)** / tidak diburu | ditangkap |
| K4 | Sekte NPC bisa merosot? | **ya, MEROSOT (tidak pernah hilang), bisa dibangkitkan** / tidak pernah turun | ya |
| K5 | Pemain boleh menjadi Ketua sekte NPC dan menyerapnya ke aula sendiri? | **ya** / hanya Ketua tanpa serap / tidak | ya |
| K6 | Jubah sekte terlihat di atas zirah? | **ya (bisa dimatikan di pengaturan)** / hanya tanpa zirah | ya |
| K7 | Syarat Usia Tulang (pemain "tua" harus lebih kuat) | **ya** / tidak | ya |
| K8 | PK pribadi sekte NPC = PK yang sama dengan kas aula (satu satuan) | **ya, satu satuan; tidak bisa ditukar NB** / mata uang terpisah "Poin Jasa" | ya (penyatuan) |
| K9 | Murid pemain boleh didaftarkan ke sekte NPC | **ya, maks 1 + pangkat/2** / tidak | ya |
| K10 | PvP antar-anggota di luar lei tai | dilarang (**pelanggaran A1 untuk Lurus/Netral; sekte Iblis boleh dengan tantangan**) / bebas | seperti kiri |

---------------------------------------------------------------------------------------------------------------------

### 41.16 Daftar uji (Indonesia; disalin ke `docs/uji/FASE-41.md` oleh F41)

**Uji terima (harus terlihat oleh penguji):**
1. Dunia baru, `uji gabung daftar`: 8 sekte dengan kelas `seimbang` (satu t8, satu t7); `uji gabung sebaran` membuktikan dua
   teratas = dua sekte berIstana Awan (`uji langit_sekte npc daftar`).
2. Kedai Teh memberi kabar penerimaan; Direktori menampilkan sekte dengan "?" lalu lengkap setelah kunjungan; Hari Gerbang ibu
   kota: perekrut punya tablet Jasa `[Lamar]`.
3. Lamaran dengan centang syarat benar (uji dengan Usia Tulang lewat → tombol Token muncul); status Calon + Tanda Calon.
4. Hari Ujian: penjaga berseru (suara), calon NPC anak-anak di tangga, tangga ilusi digambar, Tekanan Gunung naik saat diam dan
   turun saat jongkok; jatuh terduduk di 100.
5. Setiap corak (`uji gabung ujian <s> tahap 2 corak <id>` ×10): boneka menyala, biksu perunggu (membunuh sapi = gagal), binatang
   ditaklukkan tanpa mati, labirin tak terlihat dengan kabut dan trigram, pilar Qi jarum, kuali pil, batu pikul lambat, kolam
   darah menguras HP + Infami +10, cawan racun + penawar, ujian senyap dengan kerucut pandang penjaga.
6. Wawancara memakai layar dialog 38 dengan 3 soal aliran; putusan stempel; Calon Istimewa → 2-3 tetua berebut menawar.
7. Upacara sumpah: berlutut, dupa, kowtow, tetua membacakan 3 aturan bersuara, lencana dan jubah; jubah terlihat di atas zirah
   dengan warna lambang dan tepi pangkat; pemain lain melihatnya.
8. Pelajaran Shifu mingguan menaikkan Penguasaan Jurus; Shifu melindungi saat HP < 20 % di kompleks.
9. Tugas pikul air (klip + ember), sapu, kebun, kipas tungku memberi PK; Papan Misi berisi jenis 38 + jenis gabung; PK naik,
   **tidak** ada jalan menukar PK ↔ NB.
10. Paviliun Jasa: harga = harga Contribution/TechniqueGrade × pangkat × corak; terkunci menurut kelas dan pangkat; barang
    berkomponen `asal_sekte` ditolak toko kota 3 hari.
11. Gua Kecil: `CaveAbode` diset (Jimat Pulang pulang ke sana), faktor meditasi ×1.20 tampil di layar kultivasi; sewa lewat →
    pindah ke Bilik.
12. Promosi Luar → Dalam → Inti dengan upacara, kilat jubah, lonceng; kursi penuh → tantang pemegang kursi (duel).
13. Turnamen Murid: bracket dengan roster 39F, Duel Pedang dengan penonton; juara mendapat gelar.
14. `uji gabung perang <lawan>`: Panggilan Perang (suara), misi Bela Gerbang dengan regu ber-lambang lawan, skor naik; akhir
    perang mengubah tier (`kabar.gabung.naik_kelas`), visual kelas bertambah panji.
15. Serbuan kompleks live: pertanda, gelombang, tidak ada blok berubah, tidak ada tokoh mati.
16. Pelanggaran A1 (pukul murid NPC) disaksikan → teguran; ≥ 35 → Gua Renungan dengan pintu terkunci; kabur 3× = Membelot.
17. Pamit dengan Restu (surat) → Restu Mendirikan → aula baru dengan standing +40; Diusir → lencana disita, adegan; Membelot →
    poster buronan, regu pemburu dengan dialog Menyerah/Lawan/Lari, kalah = ditangkap (tidak mati), Warisan dihapus.
18. Kembali bergabung: PAMIT → pangkat −1, PK beku kembali; PEMBELOT ditolak sampai suksesi.
19. Anggota menaruh prasasti → aula dorman + dialog R2; Inti + Restu Gunung → Cabang (nama "· Cabang", upeti di Buku Kas 38).
20. Tetua Tamu: pemilik aula level 3 lulus Ujian Tamu; menyatakan perang ke sekte itu = Lepas Tamu.
21. Suksesi: Pemilihan Pewaris (duel + suara), Jubah dan Mangkuk bab 1-3, Ketua baru; layar Politik berubah; Serap → kompleks
    berganti lambang aula pemain, pool aula bertambah harian.
22. Dua pemain di satu sekte: Papan Jasa, misi kelompok, rebut kursi lewat duel lei tai, PvP di luar lei tai = pelanggaran.
23. Semua layar 41.10 pada skala GUI 2/3/4 tanpa tumpang tindih, bisa dengan keyboard saja.
24. Anggaran: `uji gabung anggaran` < 0.3 ms per sekte per tugas harian, entitas 41 ≤ 24; ujian ditunda saat pemain
    tribulasi (Sibuk).
25. `gabungSekte.aktif = false`: semua perilaku berhenti, item/entitas tetap terdaftar, aula pemain tetap normal.

**Daftar centang (untuk FASE-41.md):**
- [ ] Kelas 1-5 dari tier; sebaran lima preset; `tingkatMaks` membatasi tumbuh
- [ ] Corak dari kata A konsisten dengan nama; Teknik Warisan = spec nyata
- [ ] Syarat masuk per kelas (ranah, akar, Usia Tulang, standing, Infami, kejahatan) di satu `SyaratMasuk`
- [ ] Lima pintu masuk; Token dari reruntuhan/lelang/quest/sahabat
- [ ] Tiga tahap ujian, sepuluh ujian corak, wawancara aliran, putusan
- [ ] Upacara sumpah, lencana, jubah, bekal; Shifu dipilih, ikatan, pelajaran, perlindungan, melampaui
- [ ] Pangkat 0-8 + Tamu, kursi, tantangan, promosi, penurunan
- [ ] PK pribadi dari semua sumber; tidak ada tukar NB/batu
- [ ] Papan misi bersama, tugas harian, Paviliun Jasa, kediaman, kolam, jatah, kuota alam, turnamen murid
- [ ] Perang NPC dengan misi, serbuan kompleks, naik/turun kelas, Merosot/Bangkit, peringkat
- [ ] Aturan per aliran, Catatan Pelanggaran, denda, Gua Renungan, sidang
- [ ] Pamit/Restu, Pamit tanpa izin, Diusir, Membelot + pemburu, aturan teknik dan murid, kembali bergabung
- [ ] Veto pendirian, Cabang, Tetua Tamu, veto perang, suksesi, serap/lebur, murid titipan
- [ ] Multipemain: kursi, Papan Jasa, kelompok, PvP lei tai, suara politik, sinkron jubah
- [ ] Worldgen: lot `paviliun_jasa`, titik baru di kompleks dan gerbang, TplCheck/cek_tempat bersih, harness hijau
- [ ] Klip 300-319, prop 150-156, rupa per corak, 26 suara, bab panduan 22, 29 advancement
- [ ] Sibuk di semua peristiwa; tidak ada blok runtime; tidak ada LightningBolt/cuaca/waktu
