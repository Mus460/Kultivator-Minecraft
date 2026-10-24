## 42. Kota yang Hidup (Warga, Ekonomi Kota, Kehidupan Kota, Sekte di Kota, Kota yang Tumbuh)

Permintaan pengguna (2026-10-24, bahasa Indonesia, mengikat): dari daftar 50 ide untuk **kota**, pengguna memilih kelompok
A, C, D, F dan H dan meminta rancangan **"MEGA"** yang sangat detail dengan konten nyata: model 3D kubus + tekstur buatan
generator, animasi detail, tokoh bernama, cerita nyata, GUI segar, tempat nyata lewat world generation. Performa **bukan**
keprihatinan pengguna (tetap waras). Ide yang dipilih:

* **A. Warga yang hidup:** (1) bicara dengan **warga mana pun** (dialog menurut peran, jam, cuaca, peristiwa terbaru);
  (2) warga **mengingat pemain** (nama, bantuan, utang); (3) **hubungan antar-warga** (keluarga, sahabat, pasangan, musuh
  yang terlihat di perilaku); (4) warga **menikah, punya anak, menua dan meninggal**, kota berganti generasi; (5) warga
  **pindah kota, ganti pekerjaan**, anak warga bisa menjadi **calon murid sekte pemain**; (6) **suasana hati** warga
  (senang saat festival, takut saat serangan monster, sedih saat pemakaman); (7) **kebiasaan unik** per warga (pemabuk di
  kedai, kakek main catur, ibu-ibu bergosip di sumur).
* **C. Ekonomi:** (14) **beli atau sewa rumah** (kasur, peti, titik teleport); (15) **toko sendiri** (pajangan, pegawai,
  warga datang membeli); (16) **harga dinamis** (kelangkaan, festival, banjir pasar, perang); (17) **investasi** di usaha
  warga dengan bagi laba mingguan; (18) **gudang** dan **kontrak dagang antar-kota** yang lebih dalam; (19) **pajak kota**,
  pemain yang menjadi pejabat menerima penghasilan pajak; (20) **pasar gelap yang pindah tempat setiap malam**.
* **D. Kehidupan kota:** (21) pedagang keliling, pendongeng, pengamen, akrobat, penjual obat; (22) **rutinitas pagi** (tai
  chi di alun-alun, anak ke sekolah, pasar pagi); (23) **ronda malam** dengan lentera dan bedug jam; (24) **peristiwa acak**
  (pencopet yang bisa dikejar, perkelahian, gerobak terguling, kebakaran kecil); (25) **hewan kota** (kucing, anjing, ayam,
  merpati; kuda dan unta di pos kafilah); (26) **musim** memengaruhi kota (panen gugur, salju dan sup hangat musim dingin);
  (27) **upacara** (arak-arakan pernikahan, pemakaman, kelahiran).
* **F. Sekte dan kota:** (33) **kantor rekrutmen** sekte pemain di kota; (34) sekte pemain **melindungi kota** untuk upeti
  dan reputasi; (35) **murid** pemain belanja, bekerja dan berjaga di kota; (36) **cabang sekte** di kota (toko pil sekte atau
  balai latihan).
* **H. Bangunan dan visual:** (42) **interior** lebih detail dan berbeda per profesi; (43) **lentera menyala malam** dan
  **asap cerobong dapur**; (44) **bendera dan spanduk festival** yang berubah mengikuti peristiwa; (45) **kota tumbuh**
  (makin makmur, makin banyak bangunan baru yang dibangun tukang); (46) **kota rusak** karena perang/serangan dan
  **dibangun kembali** (perbaikan nyata); (47) **distrik malam** (lentera merah, kedai dan rumah judi ramai).

Bagian ini adalah kontrak implementasi lengkap untuk **sebelas modul** (42A, 42S, 42B, 42K, 42C, 42D, 42E, 42F, 42W, 42U,
42V; 42.15). Semua nama kelas, field, metode, id, kunci lang, perintah dan angka di bawah adalah nama yang **harus** dipakai.
Yang ditandai "(verifikasi)" sudah dicek polanya di kode tetapi nama persisnya wajib dicocokkan lagi dengan kode mod atau
`butterbot-fabric/reference/mcsrc` sebelum dipakai. Semua angka adalah desain game; baris Sumber hanya menjelaskan ilham.
Bagian ini berdiri sendiri di `docs/rancangan/42-kota-hidup.md`; `docs/DESIGN.md` dan `docs/PANDUAN.md` hanya disunting oleh
agen finish 42 (satu bagian ringkas + bab panduan).

Ringkasnya: penduduk 38 (`dunia/tempat/hidup`) yang kini "slot berperan" yang dibangkitkan ulang dari benih menjadi
**orang sungguhan yang tersimpan** (Warga): punya nama marga, umur, keluarga, sahabat dan musuh, ingatan tentang pemain,
suasana hati dan kebiasaan, lahir, menikah, pindah, menua dan meninggal sehingga kota berganti generasi dalam ±150 hari
dunia. Siapa pun bisa diajak bicara. Kota punya ekonomi yang bisa dimasuki pemain (rumah, toko, investasi, gudang, kontrak,
pajak dan jabatan, Pasar Hantu), kehidupan jalan yang ramai (rombongan keliling, ronda, copet, hewan, musim, upacara),
hubungan dengan sekte pemain (kantor rekrutmen, perlindungan, murid, cabang), dan wujud yang berubah (interior per
profesi, lentera, asap, panji, kavling yang dibangun bertahap, pembangunan kembali, distrik malam).

---------------------------------------------------------------------------------------------------------------------

### 42.0 Ruang lingkup dan aturan mengikat (tambahan atas DESIGN 0, 34.0, 36.0, 38.0, 39.0, 40.0, 41.0)

1. **Blok.** Saat permainan berjalan blok hanya berubah lewat `kehancuran/WorldBreaker` dan
   `sect/build/engine/BuildPlacer`. 42 **tidak menambah jalur ketiga**. Kota tumbuh (45) dan pembangunan kembali (46)
   memakai **jalur perbaikan 38.R yang sudah ada**: `WorldBreaker.submitRestore(RestoreRequest)` (semantik "ubah hanya bila
   dunia masih memegang `expected` atau udara", tanpa pembaruan tetangga, tanpa jatuhan, topi per tick, jeda MSPT). Tidak ada
   `setBlock`, `FallingBlockEntity`, `Explosion`, `LightningBolt`, `FireworkRocketEntity` atau blok api di mana pun. Lentera,
   asap, panji, perancah, tenda, lapak Pasar Hantu, panggung rombongan, tandu, usungan, api kecil, gerobak terguling: **entitas
   atau gambar klien** (pola `KotaPintuEntity`, `TemaMekanismeEntity`, `DisplayKey`, `KainKota`). Furnitur di rumah/toko milik
   pemain adalah **bangunan biasa oleh pemain** (pemain menaruh blok sendiri), bukan perubahan oleh mod.
2. **Hanya chunk baru; dunia baru.** Semua perubahan worldgen (kavling kosong, interior per profesi, distrik malam, titik
   baru, bukit makam) lahir di chunk baru lewat generator yang ada (`tools/nbt.py` + `tools/structgen.py` +
   `tools/tempat_lib.py`, generator per tempat). Perintah uji mencari (locate) lalu teleport; tidak ada `/place`. Pengguna
   selalu membuat dunia baru (memori "No save compat"): **tidak ada** kode kompatibilitas simpanan, migrasi atau penjaga dunia
   lama. Data 42 lahir bersih di kunci baru; desain **menyatukan** (contoh: `PendudukData.mati/gen/datang/kenal` pindah ke
   `WargaData`, 42.4.14), bukan menambah shim.
3. **Perbaikan hanya untuk kerusakan nyata.** 42 tidak pernah membuat kerusakan palsu dan tidak menambah sumber kerusakan
   blok. Pembangunan kembali (46) hanya mengerjakan gugus 38.R yang teratribusi dari peristiwa nyata (pecahan pemain,
   ledakan vanilla, api alami, mob vanilla; `LogAtribusi` setelah perbaikan `docs/progres/perbaikan-palsu.md`). Perang dan
   serangan 42 mengubah penduduk, suasana, harga dan tampilan (asap, abu jelaga klien, pengungsi), **bukan** blok (pilihan
   K6 untuk serangan sekte yang merusak nyata lewat `WorldBreaker`, bawaan mati).
4. **Ekonomi tunggal (38.M.0, PANDUAN 67).** Batu Roh Rendah/Menengah/Tinggi/Agung = 1/9/81/729 NB (`NilaiBatu.VALUE`),
   **1 PK = 2 NB** (`Harga.NB_PER_PK`), bayar lewat `NilaiBatu.pay/give` atau `EkonomiApi.bayar/beri` (tidak pernah memberi
   Agung). PK tidak pernah ditukar langsung dengan NB. Semua keran baru (pajak pejabat, investasi, toko, kontrak, upeti
   perlindungan) **dibatasi per pita** dan masuk tabel keran/pembuangan 42.6.0; beli-kembali ≤ 0,5 × harga jual
   (38.M.5); komponen `kultivator:asal_toko` menahan jual-ulang 3 hari.
5. **Tidak ada yang mati karena sistem baru kecuali warga fana karena usia atau sakit (42.4.6).** Kematian warga adalah
   bagian siklus generasi (permintaan A4), selalu diumumkan dan diikuti pemakaman. Warga **tidak pernah** mati karena
   peristiwa acak 42 (perkelahian berhenti di luka ringan, kebakaran kecil tanpa korban, copet tidak dibunuh). Tokoh
   bernama 38.S.2/38.T dan tokoh 42.10 yang berstatus "abadi cerita" tidak menua (42.4.3). Murid pemain tidak pernah mati
   karena 42.
6. **Satu pintu sibuk (T3).** Setiap peristiwa 42 yang menimpa pemain (copet, perkelahian yang melibatkan pemain, panggilan
   bela kota, upacara yang mengundang pemain, sidang pejabat) memanggil `dev.kultivator.util.Sibuk.why(player)` dan menunda
   `Sibuk.RETRY_TICKS` (200) bila sibuk. Keadaan 42 didaftarkan `Sibuk.register`: `kota.kejar` (mengejar copet), `kota.sidang`,
   `kota.upacara`, `kota.bela`; musuh 42 (pengepung kota) lewat `Sibuk.registerEnemy("kota.kepung", ...)` dan ikut
   `DAILY_ENEMY_CAP` 3.
7. **Performa waras.** Pengguna tidak mempersoalkan performa, tetapi 42 tetap: tanpa tick per warga (satu "Hari Warga" per
   tempat per hari dunia, bergiliran; kejar-ketinggalan malas), tubuh tetap lewat topi 38 (`tempat.tubuhGlobal`), entitas
   42 dibatasi `DuniaBudget.setCap`, efek klien patuh `EffectQuality` (Minimal/Normal/Maksimal), LOD jarak, tanpa post chain,
   aman Sodium/Iris (tipe render vanilla, tidak menggambar saat `ShaderCompat.shadowPass()`). Angka di 42.13.
8. **Klien/server terpisah; registri penuh (K1).** Kode server tidak menyentuh kelas klien; ItemStack malas. Blok, item,
   entitas, payload, komponen data, klip, advancement 42 **selalu** didaftarkan; saklar config hanya mematikan perilaku dan
   pack worldgen.
9. **Lang dan akhir baris.** Teks di `lang-src/kota_hidup*.id_id.json` + `.en_us.json`, kunci identik, Indonesia utama; tiap
   kunci tepat di satu fragmen (`mergeLang` gagal bila ganda). Berkas baru LF; berkas lama mempertahankan akhir barisnya
   (`KultivatorClient.java`, `musuh/SectRaids.java`, `docs/PANDUAN.md` CRLF).
10. **Gradle hanya agen finish** (`gradlew compileJava compileClientJava jar --no-daemon`); agen modul tidak menjalankan
    Gradle, tes, `runClient` atau `gradlew build`. Tiap modul menjaga berkas progres `docs/progres/kota42-<label>.md` (daftar
    centang, "Langkah berikutnya", "Butuh dari modul lain") agar agen yang terputus melanjutkan, tidak mengulang.
11. **Prasyarat:** 42 dibangun **setelah** pekerjaan `perbaikan-palsu` (38PR atribusi + kedai/pasar lebih ramai) selesai dan
    di-commit (berkas `dunia/tempat/hidup/{Jadwal, JadwalTema, Kegiatan, KerjaPeran, TempatHuni}` dan seluruh
    `dunia/tempat/perbaikan/**` sedang disunting agen lain dan **baca saja** sampai itu). 42 tidak bergantung pada 41 (belum
    dibangun); bila 41 ada, kait 42.8 memakai `GabungApi` lewat kontrak lunak (42.15.3).
12. **Sumber.** Setiap fitur punya baris Sumber di lang (`kultivator.kota42.<fitur>.source`, "Sumber: ...") atau `.inspired`
    ("Terinspirasi dari ..."). Yang tidak terverifikasi tidak dikutip dengan nomor bab. Tidak ada aset, kode atau teks yang
    disalin dari karya mana pun; kutipan puisi hanya judul.
13. **Tanpa cuaca, waktu, tick-rate.** Musim (42.7.6) dibaca dari `hari / 24 mod 4` (38.N.3) dan bioma; salju hanya dibaca
    (`Biome.coldEnoughToSnow`, verifikasi), tidak pernah dipaksa. `uji kota warga tahun <n>` memajukan **umur data**, bukan
    waktu dunia.

14. **Kecocokan mod dunia.** Semua potongan, lot, kavling, gudang, kantor, cabang, distrik malam dan kandang 42 tunduk pada
    keep-out yang ada (`mixin/ChunkGeneratorTempatMixin` + `TempatKeepOut`, tag `#kultivator:boleh_di_tempat`), tidak pernah
    tumbuh di atas struktur mod lain atau medannya, dan menghormati medan Tectonic serta bioma Terralith (`kompat_bioma`); rincian
    42.12.1A.
15. **Tanpa LLM.** Semua percakapan dan perilaku adalah algoritma deterministik dari data pengarang (42.5A); tidak ada model
    bahasa, layanan jaringan atau teks bebas yang dibuat mesin.

---------------------------------------------------------------------------------------------------------------------

### 42.1 Fakta dari kode (audit 2026-10-24; kode adalah kebenaran)

**Penduduk 38 (`dunia/tempat/hidup/**`, 7.347 baris)**
* `Penduduk` (record): `id, peran (NpcRole), look, suara (0-5), rumah, kasur, kerja (slot hash titik; TANPA = -1), kelompok
  (rumah tangga; TAMU = -2 tamu hari pasar), benih`; `geserJadwal()` ±666 tick. Dibangkitkan **deterministik** oleh
  `PendudukGen.untuk(TempatInfo)` dari campuran per jenis tempat: `kota/kota_besar` 650, `kota_tema/pelabuhan` 140, `oasis`
  130, `air` 130, `gunung` 110, `iblis` (campuran sendiri), `kota/kota` dan `kota/kota_kultivator` 40, `kampung/dusun` 6,
  `desa` 18, `desa_besar` 36 (+3 tamu), `klan` 30, `pos/singgah` 4, `singgah_besar` 8, `dagang` 6 (+3 tamu),
  `pos/kuil_jalan` 1; kompleks sekte dari `KompleksApi`.
* `PendudukData` (SavedData `kultivator:tempat_penduduk`): per tempat/penduduk hanya perubahan: `luka` (sampai), `mati` → setelah
  `PENGGANTI` 3 hari "pendatang baru" (`gen` naik, look/suara/benih baru, peran dan rumah tangga sama), keadaan `SEHAT/PULIH/
  DUKA/DATANG` (`MASA_PULIH` 1 hari, `MASA_DUKA` 2 hari, `MASA_DATANG` 1 hari), `kenal` per pemain (maks 99), `tanda` bebas
  (38ST, 38PR). **Belum ada** nama tersimpan, umur, keluarga antar-generasi, hubungan, ingatan selain hitungan kenal.
* `HidupApi`: `tubuh(level, situs, peran, pos, yaw[, look])`, `tubuhStaf` (bebas topi, `STAF_JARAK` 160), `klip`, `tatap`,
  `timpaJadwal(situs, acara, sampai)` / `timpaan`, `kerumunan(level, pusat, detik)` (≤ `TONTON_MAKS` 8) / `bubarkan`,
  `penduduk(situs)`, `pendudukDari(npc)`, `saksi`, `bahaya(level, pos, detik)` / `bahayaDi`. `TempatHuni` (titik, rumah,
  kasur, kerja, `tujuan(...)`, `kegiatan(p, waktu, now)`, simpul jalan, `jumlahTubuh`), `Pemanggil` (tubuh per siklus),
  `TubuhPenduduk` (1.430 baris, perilaku terlihat), `Jadwal`/`JadwalTema`/`JadwalData` (jadwal data `tempat/jadwal/*.json`
  per peran per jam), `Kegiatan`, `KerjaPeran`, `PendudukSnapshot` (impostor), `Saksi`.
* `NpcRole`: 15 peran lama (`JURU_LELANG` … `PENJAGA_KEBUN`), 38 (`PANGERAN_KOTA` … `PENGEMIS`), 38.T (`SYAHBANDAR` …
  `PEMBURU_LURUS`); `Kelompok {PEKERJA, PEDAGANG, PENJAGA, ANAK, TETUA, KULTIVATOR, CENDEKIA, RAHIB, PEJABAT, KHUSUS}`;
  `townsfolk()`, `anak()`, `stationary`, `tier`. Ditambah **di akhir** enum saja.

**Dialog dan cerita (`dunia/tempat/cerita/**`)**
* `TempatDialog.interact(player, npc)` dari kait `Kota.interact`: hanya peran 38 (`ordinal ≥ PANGERAN_KOTA`) atau tokoh;
  **peran lama (WARGA, PENJAGA, PEDAGANG_PIL …) di kota lama tidak bisa diajak bicara** selain layar dagang lamanya. Lembar
  sapa = satu baris `Kalimat.sapa` (grup = kelompok jadwal, situasi `takut/buronan/festival/hujan/kagum/dagang/keluhan/
  pagi/siang/malam`), tablet Kabar (`KabarTempat.pilih`), Jasa (`PenyediaJasa`), Quest.
* `KabarTempat`: templat `(id, pemicu, bobot, umurHari, penyebar, teks, isian)` dari `tempat/kabar/*.json`, `pemicu(level,
  situs, pemicu, isian…)`, `kabar(...)`, `pilih(player, situs, role)`, `semua`. Rumor **per tempat**, bukan per orang.
* `Tokoh` (38.S.2, 22 tokoh ibu kota + tokoh tema, `WATAK` 8: ramah, curiga, serakah, bijak, penakut, sombong, jujur, licik),
  `Syarat.daftar`, `Akibat.daftar`, `QuestTracker`, dialog data `tempat/dialog/*.json` (simpul, pilihan, syarat, akibat).

**Ekonomi (`dunia/tempat/ekonomi/**`, `dunia/kota/**`, `dunia/tempat/tema/bersama/**`)**
* `EkonomiApi.{bayar, beri, harga, kas(pemilikAula, sumber, pk, jumlah, catatan), kasKota, lencana, poinGilda, misiSelesai,
  daftarJenisMisi}`; `BukuKas` (`SIMPAN_HARI` 30, `BARIS_MAKS` 600, kunci UUID pemilik); `KasKota.{tambah, saldo, ambil,
  riwayat}` (hanya diisi pembuangan); `RumahUang` (biaya tarik 1 %, Batu Asal 180/90); `Brankas`; `Gadai`; `SuratWesel`;
  `LapakSekte` (`KIOS` 6 di ibu kota, `SLOT` 18, sewa 7 hari, `HARGA_MAKS` 1.5, `PAJAK` 0.05, aula lv ≥ 2); `KafilahSekte`;
  `KafilahJalan` (kafilah data 0,8 blok/s antar dua kota dikenal, tubuh `GerobakEntity`/unta/jung hanya bila pemain ≤ 64 blok,
  `MAKS` 8, begal 35 %); `MisiGilda`/`JenisMisi`; `Penginapan`, `Hidangan`, `Jasa`, `TokoSenjata`, `JudiBatu`.
* `Harga` (`BANDS` 9, `NB_PER_PK` 2.0, `ARTIFACT {20,60,200,700,2400,8000}`, `value(stack)`, `PILLS`, `TREASURES`, `SHADOW`);
  `Toko` (6-9 tawaran/hari, beli-kembali 0.35-0.40); `PasarGelap` (jangkar `kota/gelap` tetap, markup 1.5, razia, suap);
  `KedaiTeh` (token pasar gelap 60 NB); `KotaData.reputation/addReputation(long town, UUID)` (Nama Baik −100..100, satu
  untuk semua tempat lewat situsId).
* `tema/bersama/Dagang` (dagang antar-kota 38.T.10.1): `STOK_HARIAN` 16, jenuh `max(0.6, 1 − jenuhPerUnit × n)` (`PULIH_TICK`
  1000), permintaan bergeser ±0.05/minggu (`PERMINTAAN_MAKS` 1.35), diskon kafilah tiba 10 % 2 hari, batas laba mingguan
  per pita, selundupan + bea; `DagangLayar` (Buku Dagang). Data permintaan per jenis tempat:
  `data/kultivator/tempat/permintaan/*.json` (faktor per item, `"*"` bawaan).

**Perbaikan (`dunia/tempat/perbaikan/**`, sedang disunting `perbaikan-palsu`)**: `Cetak` (rekaman blok nyata per chunk, palet ≤
4096, `terima` = cetak biru menerima perubahan), `CetakStore`, `PindaiTempat`, `Banding`, `LogAtribusi`, `Gugus`,
`PekerjaanPerbaikan`, `Penagih`, `Bantu`, `NilaiBlok`, `KlaimPemain.diklaim` (= `ProtectedZones.claimed` ∨
`SectBuild.insideAnySite`), `PerbaikanApi.{takut, adaKerusakan, tagihanTerbuka, faktorHarga, ditakuti, diskonPersembahan,
poinBantu, ledakan, ubahBlok, terbakar}`. `WorldBreaker.RestoreRequest(dimension, positions, targets, expected,
blockEntityTags, reason, done)`, `submitRestore` (≤ `MAX_RESTORE_POSITIONS` 512 per batch, ≤ `MAX_RESTORE_REQUESTS` 64
antre, tolak lava), `setRestoreBudget(1-64)`, `addChangeListener`. `ProtectedZones.addClaimProvider(ClaimProvider)` /
`claimed(level, pos)` (pemakai: `CaveAbode`, `Desa`). Catatan: DESIGN 38.0 ringkas menyebut "jalur BuildPlacer", kode
memakai `WorldBreaker.submitRestore` — **kode benar**; `BuildPlacer` paket-privat dan terikat situs aula, tidak dipakai 42.

**Tempat dan sipil**: `kotabesar/{JamMalam (genderang 16000, tutup 16120, buka 1000, ketuk 2 NB), FestivalLentera, PasarMalam,
HariGerbang (`d % 10 == 4`, 8 lapak perekrut sekte NPC), Hukum, Istana, Kuil, GelombangBinatang, KotaBesarKalender,
TurnamenAgung, PertemuanSekte}`; `kampung/{AnakBerbakat, FestivalPanen, PasarDesa, Klan}`; `tema/bersama/TanduEntity`
(tandu, entitas mekanisme); `ekonomi/GerobakEntity`; `Towns` (`SAFE_RADIUS` 80, `SAFE_RADIUS_KOTA_BESAR` 320,
`SAFE_RADIUS_KOTA_TEMA` 160, jenis `kota/kota|kota_abadi|kota_besar|kota_tema`). **Tidak ada** hewan kota, rumah milik
pemain, toko milik pemain non-sekte, investasi, pajak, jabatan selain Perintah Istana, upacara warga, ronda di luar ibu
kota, pasar gelap berpindah, kavling tumbuh.

**Sekte**: `sect/hall/Halls.addCandidates(UUID owner, List<CandidateSpec>)` (`CandidateSpec(DiscipleData data, int tekad, double
talentChance, double physiqueChance, boolean tested, String source, …)`), `Halls.{hallOf, level, addRenown, storeAway}`;
`desa/Desa` (Desa Bawahan + upeti, `ProtectedZones.addClaimProvider(Desa::claimsPlaque)`); `sekte/mandiri/**` (39: perjalanan
BELANJA/JUAL/MISI_GILDA, tubuh `JELMAAN`, `MandiriApi.addTujuanProvider`, `PasarMurid`, `relawan`, `dagangan`);
`DiplomasiService` (sekte NPC, standing per UUID).

**Penampilan dan gerak (40)**: `SosokApi.{setUsia(e, usia), usia, perempuan, setSuasana(e, suasana, tick), setLuka, setRupa(npc,
kunci), setBusanaKhusus, gerak(e, klip, varian, tick[, propKanan, propKiri, …]), gerakSinkron, tatap, bicara}`. `GerakId`:
1-107 (38), 108-199 (40), 200-299 (39), **300-399 "tambahan 38"** (41 mengklaim 300-349 di rancangan), 400-499 cadangan,
`MAKS` 4095; `GerakKatalog.daftar` hanya menerima 200-399 (`UKURAN = CADANGAN_FIRST`). `PropId`: 1-40, 41-62 tema, 63-99 (40),
100-149 (39), **150-199 tambahan** (41 mengklaim 150-169), `MAKS` 1023; `PropKatalog.daftar` hanya 100-199. → **42 memakai klip
350-399 + 400-469 dan prop 170-199 + 200-239** dengan dua suntingan aditif batas katalog (42.12.5).

**Panduan, suara, hub, config**: `guide/GuideChapters.CHAPTER_COUNT = 21` (41 merencanakan 22) → **42 = bab 23** (indeks simpan
22) bila 41 sudah dibangun; bila 42 dibangun lebih dulu, 42 mengambil bab 22 dan finish 41 menggeser 41 ke 23 (42.14.4).
`tools/gen_voices.py` `WORLD` tuple `(kunci, suara, ja, en, id, emosi)`, suara `m1 m2 m3 f1 f2 f3 elder boss demon heaven
heaven_fierce`, emosi `normal joy angry sad fear calm`; gumam warga 66 klip (`tools/gen_voices.py` blok gumam; anak memakai
`f1`). `client/hub/HubNav` (`DIRI 0`, `MURID 2`, `SEKTE 3`, `DUNIA 4`), `HubCards.register`. Bagian config dunia yang sudah
dipakai: `cerita dimensi ekonomi38 gua jalan kampung kapal kompleksSekte kota kotaBesar kotaTema langit naga perbaikan peta
sosok temaAir temaGunung temaIblis temaOasis temaPelabuhan tempat tempatHidup umum` → 42 memakai `kotaWarga`, `kotaBicara`,
`kotaEkonomi`, `kotaAcara`, `kotaHewan`, `kotaSekte`, `kotaTumbuh` (42.14.1). Suara riuh kota klien: `client/dunia/tempat/
hidup/{RiuhKota, ObrolanSuara}` (PANDUAN 73).

**UI kit 38.U** (`client/tempat/ui/kit/**`): `PanelTempat, TabLentera, DaftarGulir, KartuBarang, Pratinjau3D, BandingStat,
LabelHarga, PemilihJumlah, LencanaPangkat, CincinWaktu, PapanSkor, KeadaanKosong, KeadaanTerkunci, TombolAksi,
GelembungDialog, Potret, KotakCari, AngkaGulir, TempatWarna, LayarTempatDasar, Lembar, Lukis, TeksTempat, IkonTempat`;
server `dunia/tempat/ui/TempatLayar.{buka, daftar, bukaDari}`, `LembarKunci`, `LEMBAR_ANGGARAN` 32 KB.

---------------------------------------------------------------------------------------------------------------------

### 42.2 Lore dan Sumber

Semua sumber adalah karya/kisah/kebiasaan nyata yang dikenal luas; rincian yang tidak terverifikasi ditulis tanpa nomor bab.

| Ilham | Isi yang dipakai | Sumber | Dipakai di |
|---|---|---|---|
| Umur fana singkat di mata kultivator | Han Li kembali dan mendapati keluarga fananya sudah menua dan berganti generasi | *A Record of a Mortal's Journey to Immortality* (Wangyu) | Skala waktu warga 42.4.3, pemakaman 42.7.7 |
| Pulang ke desa | Wang Lin kembali ke desanya dan menemukan orang tua dan kenalannya sudah tua atau tiada | *Renegade Immortal* (Er Gen) | Generasi 42.4.6, ingatan 42.4.10 |
| Anak desa diuji sekte | Han Li dan Wang Lin kecil dibawa dari desa fana untuk diuji sekte; akademi Jia Nan merekrut di Kota Wutan | *A Record of a Mortal's Journey*; *Renegade Immortal*; *Battle Through the Heavens* (Tian Can Tu Dou) | Kantor Rekrutmen 42.8.1, anak berbakat 42.4.9 |
| Kota dan pasar sungai | Gulungan lukisan kota Kaifeng: kedai, toko bermerek, pendongeng, peramal, gerobak, kuli, perahu, bendera arak | *Sepanjang Sungai saat Festival Qingming* (清明上河图, Zhang Zeduan, abad 12) | Interior 42.9.1, panji arak 42.9.3, jalan ramai 42.7 |
| Pasar fajar "pasar hantu" | Pasar sebelum fajar di Kaifeng yang bubar saat terang ("guishi zi") | *Dongjing Meng Hua Lu* (东京梦华录, Meng Yuanlao, 1147) | Pasar Hantu 42.6.7 |
| Distrik hiburan | *Washe/wazi* dan *goulan*: distrik hiburan Song dengan pendongeng, wayang, akrobat, buka sampai malam | *Dongjing Meng Hua Lu*; *Mengliang Lu* (梦粱录, Wu Zimu) | Distrik malam 42.9.6, rombongan 42.7.1 |
| Judi jual-beli | *Guanpu* (关扑): pedagang jalanan menjual barang lewat lemparan dadu pada hari festival | *Dongjing Meng Hua Lu* | Meja Guanpu 42.9.6 |
| Pemadam dan menara api | Menara pengawas api (望火楼) dan regu pemadam (潜火) di Kaifeng | *Dongjing Meng Hua Lu* | Kebakaran kecil 42.7.4, ronda 42.7.3 |
| Penjaga malam | *Dagengren* (打更人) memukul gong dan kentongan tiap *geng* (jaga) dan berseru "udara kering, hati-hati api dan lilin" | kebiasaan Tiongkok tradisional | Ronda malam 42.7.3 |
| Lumbung penstabil harga | *Changping cang* (常平仓): lumbung negara membeli saat murah dan menjual saat mahal, digagas Geng Shouchang (54 SM) | *Hanshu* (Ban Gu), catatan pangan dan dagang | Harga dinamis 42.6.3 |
| Biro sewa rumah negara | *Dianzhai wu* (店宅务): kantor Song yang menyewakan rumah dan toko milik negara di Kaifeng; akta rumah bercap pajak "akta merah" (红契) | sejarah Dinasti Song | Rumah 42.6.1 |
| Saham saudagar Shanxi | Saudagar Shanxi (晋商) dengan bagi laba "saham modal" dan "saham tenaga" (身股); bank wesel Rishengchang (1823) | sejarah dagang Qing | Investasi 42.6.4, gudang 42.6.5 |
| Saudagar terkaya | Shen Wansan, saudagar legendaris awal Ming | legenda Jiangnan | Tokoh Juragan Shen Wan 42.10 |
| Magistrat | *Zhixian* (知县) "pejabat ayah-ibu rakyat"; Hakim Bao Zheng (999-1062) dan legendanya | sejarah Song; kisah rakyat Hakim Bao | Jabatan dan pajak 42.6.6 |
| Pembakuan bangunan | *Yingzao Fashi* (营造法式, Li Jie, 1103): tahap pondasi, rangka kayu, dougong, atap | buku teknik bangunan Song | Kota tumbuh 42.9.4 (lima tahap) |
| Tukang legendaris | Lu Ban (鲁班), leluhur para tukang kayu | legenda Tiongkok | Tokoh Mandor Gong Shu 42.10 |
| Kota dibakar dan dibangun lagi | Dong Zhuo membakar Luoyang (190 M); kota-kota dibangun kembali setelah perang | *Sanguo Zhi* (Chen Shou); *Romance of the Three Kingdoms* | Pembangunan kembali 42.9.5 |
| Pasar di sekitar sumur | *Shijing* (市井, "pasar dan sumur"): pasar kuno tumbuh di sekitar sumur, sumur menjadi pusat kabar | etimologi dan kebiasaan Tiongkok | Gosip di sumur 42.5.4, kebiasaan 42.4.12 |
| Catur di bawah pohon | Kakek-kakek bermain *xiangqi* di taman dan bawah pohon | kebiasaan Tiongkok | Kebiasaan 42.4.12 |
| Burung sangkar | *Liu niao* (遛鸟): orang tua mengajak burung sangkar jalan pagi dan menggantungnya di pohon | kebiasaan Tiongkok utara | Kebiasaan 42.4.12 |
| Adu jangkrik | Adu jangkrik populer sejak Song; Jia Sidao disebut menulis *Kitab Jangkrik* (促织经) | sejarah Song | Kebiasaan 42.4.12 |
| Kucing Song | Penyair Lu You menulis beberapa puisi tentang kucing peliharaannya (*linu*) | puisi Lu You (judul saja) | Hewan kota 42.7.5 |
| Peluit merpati | Peluit merpati (鸽哨) diikat di ekor merpati | kebiasaan Beijing | Merpati 42.7.5 |
| Pendongeng | *Shuoshu* (说书) dengan balok kayu *xingmu* (醒木) | kebiasaan Song-Qing | Pendongeng 42.7.1 |
| Wayang kulit | *Piyingxi* (皮影戏) di balik layar bercahaya | kebiasaan Tiongkok | Teater bayang 42.9.6 |
| Akrobat | *Baixi* (百戏) zaman Han: akrobat, piring berputar, lompat simpai | sejarah Han | Rombongan akrobat 42.7.1 |
| Pengamen buta | Abing (Hua Yanjun), pemain erhu buta; karya *Erquan Yingyue* | sejarah musik Tiongkok abad 20 | Tokoh Ah Qin 42.10 |
| Tabib jalanan | *Jianghu langzhong* (江湖郎中) dan "plester kulit anjing"; Xue Yong berjualan obat sambil bermain silat | kebiasaan; *Water Margin* (Shi Nai'an) | Penjual obat 42.7.1 |
| Pencuri lihai | Shi Qian "kutu di atas genderang" | *Water Margin* | Pencopet 42.7.4, tokoh Tikus Seribu Jari |
| Perkelahian di kedai | Wu Song menghajar Jiang Menshen di Kuaihuolin | *Water Margin* | Perkelahian 42.7.4 |
| Penyair pemabuk | Li Bai, "abadi pemabuk"; Liu Ling dari Tujuh Bijak Hutan Bambu | sejarah sastra Tang/Jin | Kebiasaan pemabuk, tokoh Li Zuixian |
| Kekasih dari dua keluarga | Liang Shanbo dan Zhu Yingtai (*Sepasang Kupu-kupu*) | legenda rakyat | Alur "Dua Keluarga di Jalan Sumur" 42.10 |
| Pernikahan | Mak comblang (媒婆), tandu merah (花轿), arak-arakan penjemputan, angpao | kebiasaan pernikahan Tiongkok ("tiga surat enam upacara") | Pernikahan 42.7.7 |
| Pemakaman | Pakaian berkabung putih, uang kertas sembahyang, arak-arakan pemakaman, uang duka (帛金) | kebiasaan pemakaman Tiongkok | Pemakaman 42.7.7 |
| Kelahiran | Telur merah dan pesta sebulan (满月酒); *zhuazhou* (抓周): bayi memilih benda di ulang tahun pertama sebagai ramalan; Jia Baoyu memilih bedak | kebiasaan; *Yanshi Jiaxun* (Yan Zhitui); *Dream of the Red Chamber* (Cao Xueqin) | Kelahiran 42.7.7, bakat anak 42.4.9 |
| Sekolah | *Sishu* (私塾) dan *Sanzijing* (三字经) yang dilafalkan anak | kebiasaan pendidikan | Anak sekolah 42.7.2 |
| Musim dan hari raya | *Laba* (bubur delapan bahan), Qingming (sapu makam), Perahu Naga, Pertengahan Musim Gugur, Titik Balik Musim Dingin | kalender rakyat Tiongkok | Musim 42.7.6, Nenek Sup 42.10 |
| Asap dapur | Motif "asap dari desa" dalam puisi pedesaan | puisi Tao Yuanming *Kembali ke Kebun dan Ladang* (judul saja) | Asap cerobong 42.9.2 |
| Kerajaan fana dilindungi sekte | Kota fana di bawah naungan sekte yang memungut upeti | trope umum novel kultivasi (mis. sekte-sekte di *A Will Eternal*, *I Shall Seal the Heavens*, Er Gen) | Perlindungan kota 42.8.2 |
| Perguruan di kota | *Wuguan* (武馆) dan Perkumpulan Atletik Jingwu (Huo Yuanjia, 1910) | sejarah | Cabang sekte balai latihan 42.8.4 |
| Pengungsi perang | Rakyat mengungsi dan pulang membangun desa | *Romance of the Three Kingdoms* (umum) | Tenda pengungsi 42.9.5 |

Narasi dalam game (Kitab Panduan bab 23): "Kultivator datang dan pergi seperti awan. Kota tetap tinggal: anak yang kau beri
permen hari ini akan menjadi pandai besi, menikah di jalan yang sama, dan suatu pagi cucunya menunjukmu sambil berbisik,
'Itu orang yang dulu menyelamatkan Kakek.'"

---------------------------------------------------------------------------------------------------------------------

### 42.3 Peta ide: yang dipakai ulang dan yang baru

| # | Ide | Dipakai ulang (tidak digandakan) | Baru di 42 | Modul |
|---|---|---|---|---|
| A1 | Bicara dengan warga mana pun | `TempatDialog`, layar `dialog` 38.U.3.1, `Kalimat`, `KabarTempat`, `PenyediaJasa`, `Tokoh.WATAK` | jalur `interact` untuk **semua** peran sipil + kota lama; **Mesin Percakapan Lanjut** tanpa LLM (kepribadian, emosi PAD, pengetahuan, topik utilitas, tata bahasa, nada pemain, obrolan NPC-NPC, 42.5A); tablet Hubungan/Tanya Jalan/Permintaan | 42B, 42K |
| A2 | Warga mengingat pemain | `PendudukData.kenal` (dipindah), `KotaData.reputation` | `Ingatan` per warga per pemain (kesan, utang, bantuan, peristiwa ≤ 4) | 42A |
| A3 | Hubungan antar-warga | `NpcLife` obrolan, `HidupApi.kerumunan`, klip `berdebat/obrol/tertawa` | graf `Hubungan` (≤ 8 tautan per warga) + perilaku terlihat | 42A, 42D |
| A4 | Menikah, lahir, menua, mati | `PendudukData.mati` → `DUKA`, `SosokApi.setUsia` | siklus hidup penuh, pohon keluarga, pewarisan rumah/usaha | 42A |
| A5 | Pindah, ganti kerja, calon murid | `KafilahJalan` (tubuh di jalan), `AnakBerbakat`, `HariGerbang`, `Halls.addCandidates` | `Migrasi`, `Magang`, akar roh warga, jalur calon ke Kantor Rekrutmen | 42A, 42E |
| A6 | Suasana hati | `SosokApi.setSuasana`, wajah 40, `PerbaikanApi.takut` | `Suasana` lapis (pribadi + tempat + peristiwa) | 42A |
| A7 | Kebiasaan unik | `Jadwal` data per peran/jam, klip 38/40 | 32 `Kebiasaan` yang menimpa jadwal per warga | 42A, 42V |
| + | Sebaran merata, kegiatan tidak monoton | `Pemanggil` (panggil 128, terdekat dulu), `TempatHuni` (kunci titik), `Jadwal.ramai` (pasar pagi, makan siang, kedai malam) | zona + kapasitas + target per jam, Penyeimbang, pemilih tubuh proporsional, kumpulan ≥ 10 kegiatan per peran, rencana harian, selingan, belokan, kelompok, metrik (42.4A) | 42S |
| C14 | Rumah | jangkar/lot `bangunan` 38.B.2.5, `ProtectedZones.addClaimProvider`, `KlaimPemain` | `RumahKota` (beli/sewa, klaim, kunci teleport) | 42C |
| C15 | Toko sendiri | `LapakSekte` (logika jual harian), `Dagang` (jenuh), `BukuKas` | `TokoPemain`, blok `rak_dagang`, pegawai warga/murid, pembeli bertubuh | 42C |
| C16 | Harga dinamis | `Dagang.permintaan/jenuh`, `tempat/permintaan/*.json`, `PerbaikanApi.faktorHarga` | `HargaKota.faktor(level, situs, kategori)` satu pengali untuk semua toko | 42C |
| C17 | Investasi | `BukuKas`, `RumahUang` | `Investasi` di `UsahaWarga` | 42C |
| C18 | Gudang dan kontrak | `KafilahJalan`, `KafilahSekte`, `Dagang.segel/peti`, `Brankas` | `GudangKota`, `KontrakDagang` berulang, Rumah Dagang pemain | 42C |
| C19 | Pajak dan jabatan | `KasKota`, `Istana` (Perintah Istana), `Hukum`, `KotaData` | `PajakKota`, `Jabatan` 5 pangkat, sidang perkara | 42C |
| C20 | Pasar gelap pindah | `PasarGelap` (dagangan, razia, suap), `KedaiTeh` token | `PasarHantu` berpindah tiap malam, lapak entitas | 42C |
| D21 | Rombongan keliling | `Pengembara`, `KafilahJalan`, `PEDAGANG_KELILING` | `Rombongan` 5 jenis + panggung entitas + melodi klien | 42D |
| D22 | Rutinitas pagi | klip `tai_chi_pagi`, `basuh_muka`, jadwal 05-08, titik `sekolah` | `RutinitasPagi` (barisan anak, pasar pagi, kelompok tai chi) | 42D |
| D23 | Ronda malam | `JamMalam` (ibu kota), klip `genderang`, prop `lentera_tangan` | `Ronda` untuk semua kota, seruan jam (geng 1-5) | 42D |
| D24 | Peristiwa acak | `Hukum.kejahatan`, `HidupApi.kerumunan/bahaya`, `GerobakEntity` | `PeristiwaJalan`: copet, tawuran, gerobak terguling, api kecil (entitas) | 42D |
| D25 | Hewan kota | — | 6 entitas hewan + kawanan merpati | 42D, 42V |
| D26 | Musim | `musim = (d/24) % 4` 38.N.3, `FestivalPanen`, `Hidangan` | `MusimKota` (busana, warung sup, panen, Qingming, Laba) | 42D |
| D27 | Upacara | `TanduEntity`, `HidupApi.timpaJadwal`, `DUKA` | `Upacara` nikah/makam/lahir dengan arak-arakan bertubuh | 42D |
| F33 | Kantor rekrutmen | `kota/kantor_sekte` (34), `HariGerbang`, `Halls.addCandidates` | `KantorRekrut` sewaan pemain + calon dari warga | 42E |
| F34 | Lindungi kota | `Desa` (pola upeti), `DiplomasiService`, `GelombangBinatang`, `BukuKas` | `PaktaLindung` (upeti, kewajiban bela, panji lambang) | 42E |
| F35 | Murid di kota | 39 `JELMAAN`, `MandiriApi`, `LapakSekte` penjaga | tugas JAGA_KOTA, KERJA_KOTA, PEGAWAI_TOKO | 42E |
| F36 | Cabang sekte | `RumahKota` (42C), `Lambang`, `LapakSekte` | `CabangSekte` (toko pil, balai latihan, klinik) | 42E |
| H42 | Interior per profesi | generator 38 (`tempat_lib`, `kota_besar_kit`, `tempat_ds_kit`) | 24 kit interior + pajangan klien | 42W, 42V |
| H43 | Lentera dan asap | `kota/lentera`, dekorasi 38.O.3 | `LenteraKota`, `AsapDapur` (klien, jadwal deterministik) | 42V |
| H44 | Panji dan spanduk | `KainKota` 38.O.3, `Lambang` | `PanjiKota` mengikuti `kota_suasana` | 42V |
| H45 | Kota tumbuh | `WorldBreaker.submitRestore`, `Cetak.terima`, tukang 38.R, perancah `tools/gen_tempat_perancah.py` | `Kavling` 5 tahap, `Kemakmuran`, bahan kota | 42F, 42W |
| H46 | Rusak dan dibangun kembali | seluruh 38.R (hanya kerusakan nyata) | `BangunKembali` (proyek besar, pengungsi, donasi, tugu kenangan) | 42F |
| H47 | Distrik malam | `PasarMalam`, `JudiBatu` (pola EV) | `DistrikMalam`, kedai arak, Guanpu, teater bayang | 42D, 42W, 42V |

---------------------------------------------------------------------------------------------------------------------

### 42.4 Warga: penduduk yang sungguh hidup (A1-A7; modul 42A)

#### 42.4.1 Model: Warga (orang) dan Penduduk (slot)

38 memisahkan **slot** (`Penduduk`: peran, rumah, kasur, kerja, rumah tangga, dibangkitkan ulang dari benih) dari **tubuh**
(`KultivatorNpc` sementara). 42 menambah lapis ketiga, **Warga** = orang tersimpan yang **menempati** satu slot:

```
WargaData (SavedData kultivator:kota_warga)            38NL (tetap pemilik tubuh dan jadwal)
  Warga #18342 "Zhang Ming'an", 34 th, KOKI ──menempati──▶ Penduduk (situs, id 57, peran KOKI, rumah, kasur, kerja)
     ayah #9120 (arsip), ibu #9121, pasangan #18350,            │ Pemanggil.isi → tubuh KultivatorNpc
     anak #20011 #20530, sahabat #18401, musuh #18377,          │   nama papan = nama Warga, SosokApi.setUsia/setSuasana
     ingatan[pemain], suasana, kebiasaan, gosip                 ▼ Jadwal → PenimpaJadwal 42 (kebiasaan, upacara, hubungan)
```

* Slot tetap milik `PendudukGen` (jumlah dan campuran peran per jenis tempat tidak berubah) **ditambah** slot tambahan dari
  42 (`id ≥ 5000`, 42.4.7): anak di luar kuota `ANAK`, tetua pensiun, murid magang, rumah tangga di bangunan kavling baru.
* Satu slot berisi paling banyak satu Warga; Warga bisa tanpa slot (sedang di jalan, di dalam rumah tanpa tubuh, bayi).
* Bila 42 dimatikan (`kotaWarga.aktif = false`), `WargaApi.terapkan` mengembalikan `Penduduk` apa adanya dan perilaku 38
  kembali persis (tanpa generasi, tanpa nama tersimpan).

#### 42.4.2 Identitas

| Bagian | Isi | Angka |
|---|---|---|
| Marga | 100 marga dari *Bai Jia Xing* (百家姓) urut kekerapan (Wang, Li, Zhang, Liu, Chen, Yang, Huang, Zhao, Zhou, Wu …) | `byte marga` 0-99, tabel `kota_hidup_nama` |
| Nama diri | dua suku kata dari 120 suku kata per kelamin (`An, Bao, Chun, De, Fang …` / `Ai, Bi, Cui, Dan, E, Fen …`) | `short nama` (indeks pasangan suku) |
| Nama generasi | saudara kandung berbagi suku pertama (*zibei* 字辈) dari puisi generasi keluarga (8 suku per marga, berputar) | suku pertama = `puisi[marga][generasi % 8]` |
| Marga anak | marga ayah; marga ibu bila ayah tidak ada atau menantu masuk (*ruzhui*) 10 % | — |
| Perempuan menikah | **tetap** marga sendiri (kebiasaan Tiongkok); disapa "Nyonya <marga suami>" di dialog formal | — |
| Kelamin | `kotaWarga.rasioPerempuan` 0.5 (sama dengan `sosok.rasioPerempuan`) | `byte kelamin` |
| Rupa | `look` (benih Sosok 40) diwariskan: 50 % gen ayah, 50 % ibu (warna rambut/wajah lewat `SosokGen.untukNpc` dengan benih campuran) | `int look` |
| Suara | `suara` 0-5 (`KultivatorNpc.VOICES`); gumam 73 menurut Sosok | `byte suara` |
| Watak | satu dari `Tokoh.WATAK` (ramah, curiga, serakah, bijak, penakut, sombong, jujur, licik); 50 % mengikuti salah satu orang tua | `byte watak` |
| Kebiasaan | 0-2 dari 32 (42.4.12), dipilih saat umur 16 dan 50 | `byte kb1, kb2` |
| Akar roh | saat lahir: tanpa 85 %, TRASH 6 %, LOW 5 %, MID 2,8 %, HIGH 1 %, HEAVENLY 0,2 %; × 2 peluang berakar bila satu orang tua berakar ≥ LOW | `byte akar` (0 = fana, 1-5 = `RootGrade` + 1) |
| Ranah | fana kecuali peran ber-tier ≥ 2 (`HidupApi.realmFor`); anak berakar yang tidak direkrut naik paling tinggi Pemurnian Qi 3 | `byte ranah` |
| Keahlian kerja | 0-100, naik +4/tahun saat bekerja, magang mulai 10 | `byte ahli` |

Contoh kartu: **Zhang Ming'an** (张明安), 34 tahun, Koki Rumah Makan Teratai, watak *jujur*, kebiasaan *pecinta teh* dan
*penyapu jalan*, istri **Liu Cuifen**, anak **Zhang Wenbao** (9, akar MID) dan **Zhang Wenxiu** (4), ayah almarhum **Zhang
Deshun** (makam 7 Bukit Makam Barat).

#### 42.4.3 Skala waktu dan usia

`kotaWarga.hariPerTahun` = **2** hari dunia per "Tahun Fana" (1-8). Musim kota tetap kalender dunia (24 hari per musim,
38.N.3); umur warga memakai Tahun Fana (lore: "bagi kultivator yang bersemedi, umur fana berlalu secepat musim"). Ulang
tahun tiap warga digeser menurut `lahir` sehingga peristiwa tahunan tersebar rata.

| Tahap | Umur | Hari dunia (bawaan 2) | Sosok usia (40) | Peran slot | Catatan |
|---|---|---|---|---|---|
| Bayi | 0-2 | 0-5 | — (tidak bertubuh; digendong prop `bayi_bedong` oleh ibu) | — | pesta sebulan, *zhuazhou* di umur 1 |
| Anak | 3-12 | 6-25 | anak | `ANAK` | sekolah 7-10, main; akar terlihat sejak 8 |
| Remaja | 13-15 | 26-31 | muda | `ANAK` atau magang | pilih jalan kerja |
| Dewasa muda | 16-29 | 32-59 | muda | kerja | pacaran, menikah |
| Dewasa | 30-54 | 60-109 | dewasa | kerja | — |
| Tua | 55-64 | 110-129 | tua | kerja | — |
| Pensiun | 65+ | 130+ | tua | `TETUA_DESA` / `KEPALA_DESA` | catur, cucu, cerita |

* **Bahaya kematian per tahun** (fana): umur < 50 0,2 %; 50-59 0,6 %; 60: 1 %; naik ×1,09 per tahun (70: 2,4 %; 80: 5,6 %;
  90: 13 %); batas `kotaWarga.umurMaks` 98. Sakit (kesehatan < 30) × 4. Rata-rata umur ±76 = **±152 hari dunia**: satu
  generasi penuh berganti dalam satu dunia main yang wajar.
* **Warga berranah**: batas umur `Realm` (Pemurnian Qi 120, Fondasi 200, Inti Emas 500, Jiwa Baru Lahir 1000; lebih tinggi
  tidak menua dalam permainan) dan menua dengan pengali 1/1,5/4/8.
* **Abadi cerita**: tokoh 38.S.2, 38.T, 42.10 yang berbendera `abadiCerita` tidak menua dan tidak mati karena usia; tokoh
  42.10 tertentu sengaja **tidak** abadi (Lao Geng pensiun dan wafat, 42.10).
* `uji kota warga tahun <n>` memajukan umur data semua warga satu tempat (tanpa mengubah waktu dunia).

#### 42.4.4 Keluarga dan rumah tangga

* **Rumah tangga** (`int rt`): menempati satu rumah (`rumah` = slot titik `bangunan` 38.B.2.5) dengan kapasitas = jumlah titik
  `kasur` di rumah itu (min 2). Menikah: istri pindah ke rumah tangga suami (patrilokal 80 %) atau sebaliknya; bila rumah
  penuh (> kasur + 1) pasangan muda mencari rumah kosong di tempat itu, lalu di kavling baru (42.9.4), lalu pindah kota.
* **Pohon keluarga**: `ayah`, `ibu`, `pasangan`, `anak[≤ 8]`; orang yang meninggal pindah ke **arsip** tempat
  (`ArsipWarga`: id, nama, kelamin, lahir, wafat, ayah, ibu, pasangan, makam) — maks `kotaWarga.arsipMaks` 400 per tempat,
  yang tertua tanpa keturunan hidup dibuang dulu. Layar Pohon Keluarga (42.11.3) menampilkan 3 generasi ke atas dan ke bawah.
* **Makam**: tiap kematian mendapat titik `nisan_<n>` di Bukit Makam tempat itu (lot baru 42.12.1; 24-48 nisan per bukit)
  bergiliran; nama, tahun lahir-wafat dan gelar ditulis klien (billboard `TeksTempat` di batu nisan, ≤ 16 blok). Tempat tanpa
  bukit makam memakai titik `makam`/`kuil` terdekat (nama hanya di Pohon Keluarga).
* **Yatim**: diasuh kakek-nenek, paman/bibi, lalu Kuil (peran `RAHIB` mengasuh; rumah yatim = titik kasur kuil).
* **Anak angkat**: pasangan tanpa anak 5 %/tahun mengangkat yatim. Pasangan warga hanya pria-wanita (42.16 K9: tidak).

#### 42.4.5 Hubungan antar-warga

Graf ≤ 8 tautan per warga (`long` terkemas: `idLain 32 | jenis 8 | kekuatan 8 (−100..100) | sejakHari 16`). Keluarga tidak
memakai tautan (dari pohon). Pertemuan harian: tiap warga 2 undian pertemuan dengan warga yang **berbagi konteks**
(tetangga rumah, rekan kerja sama lot, pasangan kebiasaan, sesama anak di titik `main`, jemaat kuil); perubahan kekuatan =
`kecocokan[watakA][watakB] + acak(−2..+3) + 2 × (kebiasaan sama) − 2 × (saingan kerja)`.

| | ramah | curiga | serakah | bijak | penakut | sombong | jujur | licik |
|---|---|---|---|---|---|---|---|---|
| **ramah** | +3 | 0 | −1 | +2 | +2 | −1 | +2 | −1 |
| **curiga** | 0 | +1 | −1 | +1 | +1 | −2 | +1 | −3 |
| **serakah** | −1 | −1 | 0 | −2 | +1 | 0 | −3 | +2 |
| **bijak** | +2 | +1 | −2 | +2 | +1 | −2 | +3 | −1 |
| **penakut** | +2 | +1 | +1 | +1 | +1 | −3 | +1 | −1 |
| **sombong** | −1 | −2 | 0 | −2 | −3 | −2 | −1 | +1 |
| **jujur** | +2 | +1 | −3 | +3 | +1 | −1 | +2 | −3 |
| **licik** | −1 | −3 | +2 | −1 | −1 | +1 | −3 | +1 |

| Jenis | Terbentuk | Perilaku terlihat (tubuh L0/L1) | Akibat data |
|---|---|---|---|
| `SAHABAT` | ≥ +40 | berbagi titik `obrol` sore, `tertawa` bergantian, jalan berdua ke kedai teh, menjenguk saat sakit (prop keranjang) | gosip berpindah ×2, suasana +5 bila bertemu |
| `KEKASIH` | ≥ +60 antara pria dan wanita dewasa lajang 16-45 dengan watak tidak bertentangan (< −2) (42.16 K9) | jalan bersisian senja (langkah tersinkron `gerakSinkron`), memberi bunga (prop 182 `bunga`), wajah tersipu | → lamaran 42.4.6 |
| `PASANGAN` | menikah | sarapan dan makan malam bersama di meja rumah, `lambai` pagi saat berangkat kerja, duduk berdua di serambi | rumah tangga sama |
| `MUSUH` | ≤ −40 | menghindar (biaya jalur +8 di simpul dekat musuh), tatap tajam + `marah_tunjuk` saat berpapasan, `berdebat` bila dipaksa satu titik | 5 %/hari perkelahian (42.7.4); gosip buruk tentang satu sama lain |
| `SAINGAN` | peran kerja sama di tempat sama, ≤ −20 | berseru dagang bergantian lebih keras, saling melirik | toko saingan banting harga −5 % (42.6.3) |
| `GURU_MURID` | magang 42.4.7 | guru memperagakan, murid menirukan klip yang sama dengan jeda 10 tick (varian `tiru`) | `ahli` murid +8/tahun |

Keluarga juga terlihat: anak berlari ke ayah/ibu saat pulang kerja (`lari` → `lambai`), kakek menggandeng cucu ke pasar
(langkah tersinkron, `jalan_tua` + `jalan_anak`), keluarga duka berkumpul di rumah (`DUKA`).

#### 42.4.6 Menikah, lahir, menua, meninggal

Diundi sekali per Tahun Fana tiap warga (pada ulang tahunnya), urut:

| Peristiwa | Syarat | Peluang per tahun | Akibat |
|---|---|---|---|
| Lamaran | `KEKASIH` ≥ 70 selama ≥ 1 tahun, keduanya 18-45, lajang | 40 % (× 0,3 bila keluarga kedua pihak `MUSUH`, × 1,5 lewat Mak Comblang) | pertunangan; pernikahan 1-3 hari dunia kemudian (upacara 42.7.7) |
| Perjodohan | lajang 22-35 tanpa kekasih | 15 % (Mak Comblang mencarikan dari tempat sama atau tempat dikenal ≤ 3000 blok) | hubungan diset +60, lalu lamaran |
| Kelahiran | pasangan menikah, istri 18-42 | 25 %, −10 % per anak ke-3 dst., × 0,5 rumah sesak, × 1,2 kemakmuran ≥ 60, × 0,5 perang/paceklik; kembar 2 % | bayi (42.7.7), pesta sebulan |
| Anak angkat | pasangan tanpa anak, ada yatim | 5 % | yatim pindah rumah tangga |
| Menjanda/menduda menikah lagi | ≥ 2 tahun setelah wafat pasangan | 10 % | — |
| Sakit | kesehatan turun 5-20 karena musim dingin tanpa sup, paceklik, usia ≥ 60 | 8 % (+ usia) | kesehatan < 30 = sakit: di rumah (`tidur`), permintaan "obat untuk ibu" (42.5.6), tabib berkunjung |
| Sembuh | sakit | 50 % (100 % bila pemain memberi pil penyembuh atau membayar tabib) | — |
| Wafat | bahaya 42.4.3 | — | pemakaman hari berikut (42.7.7), warisan, arsip, `DUKA` rumah tangga 2 hari (38) |
| Pensiun | umur 65 | 100 % | slot kerja kosong → 42.4.7 |

* **Warisan**: rumah ke pasangan, lalu anak tertua yang tinggal; usaha (`UsahaWarga` 42.6.4) ke anak yang magang di usaha
  itu, lalu anak tertua, lalu ditutup (investor pemain dibayar nilai sisa); utang ke dan dari pemain diwariskan (alur "Utang
  Tiga Generasi" 42.10).
* **Batas penduduk**: kapasitas lunak = jumlah kasur tempat × 1,3. Di atas → tekanan pindah (42.4.8) dan permintaan kavling
  (42.9.4). Di bawah 70 % jumlah `PendudukGen` → pendatang 2-4 per minggu.

#### 42.4.7 Pekerjaan, magang, ganti kerja

* **Jabatan kerja** = slot `PendudukGen` berperan kerja (ibu kota: 10 KOKI, 8 TUKANG, 4 EMPU …). Satu slot satu pemegang.
* **Pengisian slot kosong** (wafat, pensiun, pindah, naik ke sekte): (1) murid magang peran itu di tempat itu; (2) anak
  dewasa pemegang lama yang menganggur; (3) penganggur dewasa dengan `ahli` dan watak paling cocok (`KerjaPeran` kecocokan:
  PEDAGANG suka serakah/licik/ramah, CENDEKIA bijak/jujur, PENJAGA jujur/sombong, PEKERJA siapa saja); (4) pendatang.
* **Jalan anak**: umur 12 memilih: ikut kerja orang tua 55 %, menurut hasil *zhuazhou* 30 % (benda pilihan → kelompok:
  kuas/kitab → CENDEKIA, sempoa/koin → PEDAGANG, pedang kayu → PENJAGA atau, bila berakar, **calon sekte**, palu/cangkul →
  PEKERJA, tasbih → RAHIB, cap kecil → PEJABAT), acak 15 %. Remaja 13-15 menjadi **magang** (`GURU_MURID`) pada pemegang
  slot peran itu (slot tambahan `id ≥ 5000` dengan peran sama, titik kerja di sebelah guru).
* **Ganti kerja**: `ahli < 30` dan suasana < −20 selama setahun → 5 %/tahun pindah ke slot kosong lain; usaha bangkrut (42.6.4)
  → pemilik menjadi pekerja.
* **Slot tambahan** (`PendudukTambahan`, 42A): anak di luar kuota `ANAK`, tetua pensiun (peran `TETUA_DESA`), magang,
  penghuni bangunan kavling. Rumah/kasur dari titik `TempatHuni` (berbagi titik boleh, mengurangi suasana −5 bila kasur
  kurang); kerja dari `KerjaPeran`. 38NL menerimanya lewat kait `TempatHuni.addSumberPenduduk` (42.4.14).

#### 42.4.8 Pindah antar-kota

| Pemicu (per tahun) | Peluang | Tujuan |
|---|---|---|
| rumah sesak (penghuni > kasur + 1), dewasa muda lajang | 30 % | tempat dikenal dengan kasur kosong |
| menganggur ≥ 2 tahun | 40 % | tempat dengan slot kerja kosong peran cocok |
| suasana < −40 selama setahun | 20 % | tempat paling makmur ≤ 3000 blok |
| Takut tempat ≥ 60 (38.R.5) | 10 % | tempat lain |
| perang/serangan besar di tempat (42.9.5) | 25 % keluarga tanpa rumah | kerabat, lalu tempat terdekat |
| menikah dengan warga tempat lain | 100 % salah satu pasangan | tempat pasangan |
| ikut anak yang sudah pindah (tua) | 15 % | tempat anak |

* **Perjalanan**: status `JALAN` selama `ceil(jarak / 1200)` hari dunia (min 1), rute garis jalan 38W (`RencanaJalan`) bila
  ada. Tubuh **pelancong** (`HidupApi.tubuh` peran asal, prop `buntelan` di punggung, keluarga berjalan berderet) muncul di
  posisi hitungan bila pemain ≤ 64 blok, pola `KafilahJalan` (tubuh tidak disimpan). Pemain bisa menyapa: "Kami pindah ke
  Kota Air, Tuan. Di sana ada kerja." Begal tidak menyerang pelancong (bukan musuh baru).
* **Tiba**: catatan Warga dipindah ke tempat tujuan (id tetap, **ingatan ikut**), rumah dan kerja diisi 42.4.7, gosip
  "Ada keluarga baru dari Kota <asal>" di tujuan dan "Keluarga <marga> pindah ke <tujuan>" di asal. Pemain yang bertemu
  warga itu di kota lain: "Tuan! Masih ingat saya? Dulu jual bakpao di <asal>."
* **Pendatang dari luar peta**: bila tidak ada tempat dikenal yang cocok, warga baru dibangkitkan dengan asal "negeri jauh"
  (membawa 1 gosip benih dari `tempat/kabar/umum.json`).

#### 42.4.9 Anak berbakat dan calon murid sekte

* Anak berakar ≥ LOW terlihat sejak umur 8: aura samar saat duduk (`FLAG_AURA`, intensitas = akar), kebiasaan "melamun
  menatap langit", gosip "Anak keluarga <marga> bisa membuat daun melayang."
* **Sikap keluarga** (diundi saat anak 8, dari watak orang tua): BANGGA 40 % (ingin anak masuk sekte), RAGU 35 % (butuh
  bujukan atau uang restu), MENOLAK 25 % (ingin penerus usaha; perlu quest "Restu Ayah" atau anak kabur sendiri umur 15
  bila suasana anak < −30).
* **Jalur ke sekte pemain**: Kantor Rekrutmen 42.8.1 (utama), dialog langsung dengan anak/orang tua (tablet Hubungan → `[Ajak
  ke sekte]` bila pemain punya aula), `AnakBerbakat` kampung (38DS) dan `HariGerbang` ibu kota sekarang **membaca warga
  berakar dari `WargaApi.berbakat(situs)`** alih-alih membangkitkan anak acak (satu sumber).
* **Saingan**: perekrut sekte NPC (`DiplomasiService`, `HariGerbang`) mengambil anak berakar ≥ MID yang belum direkrut
  pemain dalam 10 hari setelah umur 12 (40 %), diumumkan kabar.
* **Konversi** ke `Halls.CandidateSpec`: `DiscipleData` dibuat dari Warga (nama marga + nama, kelamin, usia → umur rekrut 40,
  akar → `RootGrade`, watak → watak murid 36 P lewat tabel `WATAK_KE_MURID`, kebiasaan → satu kenangan awal), `source =
  "kota:<situs>"`, `tested = true` bila lewat Kantor. Status Warga menjadi `SEKTE` (pindah ke arsip hidup "murid di sekte
  <aula>"), keluarga tetap: `WargaApi.keluargaMurid(soul)` dipakai 39 (murid pulang menjenguk keluarga saat perjalanan
  BELANJA ke kota asal: tubuh `JELMAAN` makan bersama keluarganya; keluarga mengirim titipan makanan).

#### 42.4.10 Ingatan tentang pemain

Maks 6 pemain per warga (LRU menurut hari terakhir bertemu); pemain disimpan sebagai indeks tabel UUID `WargaData.pemain`.

| Field | Isi |
|---|---|
| `kenal` 0-99 | jumlah pertemuan (dipindah dari `PendudukData.kenal`) |
| `nama` | tahu nama pemain: setelah pemain memperkenalkan diri (`[Perkenalkan diri]`), bila gelar/Nama Baik pemain ≥ 60 di tempat itu, atau diberi tahu lewat gosip |
| `kesan` −100..100 | perasaan pribadi; bergeser 1 per 4 hari ke 0 (kecuali dari peristiwa "kuat") |
| `bantu` | jumlah bantuan (permintaan selesai, ditolong) |
| `utang` (NB, bertanda) | + warga berutang ke pemain, − pemain berutang ke warga |
| `hadiahHari` | hari hadiah terakhir (satu hadiah berarti per 3 hari) |
| `peristiwa[4]` | id kenangan + hari (yang terbaru menggantikan yang terlemah) |

| Sumber kesan | Δ kesan | Kuat? | Kenangan |
|---|---|---|---|
| sapa (≤ 1/hari) | +1 (maks 20 dari sapaan) | – | – |
| hadiah disukai / biasa / dibenci (selera per watak dan kebiasaan) | +8 / +3 / −5 | – | `DIBERI_HADIAH` |
| permintaan selesai | +15 | ya | `DIBANTU` |
| menyelamatkan dari monster/kebakaran/copet | +30 | ya | `DISELAMATKAN` |
| hadir di pernikahan / pemakaman keluarga, memberi angpao / uang duka | +10 / +12 | ya | `HADIR_NIKAH`, `HADIR_MAKAM` |
| merekrut anaknya ke sekte (keluarga BANGGA / MENOLAK) | +20 / −25 | ya | `ANAK_DIREKRUT` |
| memukul / mencuri darinya / merusak rumahnya (38.R) | −25 / −40 / −15 | ya | `DIPUKUL`, `DICURI`, `RUMAH_RUSAK` |
| utang dilunasi tepat waktu / mangkir | +5 / −20 | ya | `UTANG_LUNAS`, `UTANG_MANGKIR` |
| gosip tentang pemain (baik/buruk) | ±3 | – | – |
| **warisan**: anak mewarisi ½ kesan orang tua yang wafat | ½ | ya | `CERITA_ORANG_TUA` |

Kesan memengaruhi sapaan, harga pribadi (pedagang ±5 %), kemauan memberi kabar, permintaan, kesaksian kejahatan (kesan ≥ 60
tidak melapor pelanggaran kecil), dan baris khusus ("Ayahku sering bercerita tentang Tuan yang memadamkan api itu").

#### 42.4.11 Suasana hati

`suasana = pribadi + tempat + peristiwa`, dijepit −100..100, dihitung malas saat Hari Warga dan saat tubuh dipanggil.

| Lapis | Sumber | Nilai |
|---|---|---|
| Pribadi | watak (ramah +10, sombong +5, penakut −5, curiga −5), kesehatan (`(k − 60) / 4`), kemakmuran rumah tangga (`(m − 50) / 5`), baru menikah +20 (10 hari, meluruh), baru punya bayi +15, keluarga wafat −50 (meluruh 5/hari), menganggur −15, rumah sesak −5 | — |
| Tempat | festival aktif +25, upacara nikah di tempat +8, pemakaman di tempat −8, serangan monster aktif −40 (dan **takut**), perang −20, pajak di atas 10 % −1 per poin, harga pangan > 1,2 −10, dilindungi sekte pemain +5, kemakmuran `(K − 50) / 10`, Takut 38.R `−takut / 4` | — |
| Musim/cuaca | dingin −5 (0 bila warung sup buka), hujan −3, salju pertama +5 (anak +15) | — |

| Kode wajah (`SosokApi.setSuasana`) | Rentang/sebab | Tampak |
|---|---|---|
| `SENANG` | ≥ +40 | senyum, langkah ringan (`jalan_santai` cepat), bersenandung |
| `NETRAL` | −20..+40 | — |
| `SEDIH` | ≤ −20 atau DUKA | mata turun, langkah lambat, pakaian putih (berkabung), menyeka mata |
| `TAKUT` | serangan, Takut ≥ 40 | mata lebar, `takut_sembunyi`, anak menempel orang tua |
| `MARAH` | habis berdebat/dicuri (≤ 1 jam dunia) | alis turun, `marah_tunjuk` |
| `MABUK` | kebiasaan pemabuk malam | wajah merah, `jalan_mabuk` terhuyung |
| `SAKIT` | kesehatan < 30 | pucat, batuk (suara), bersandar |

Akibat: baris dialog (42.5), harga pedagang (senang −3 %, marah +5 %), peluang ikut festival, peluang pindah, kerja (tukang
senang ×1,1 laju perbaikan), tanggapan pada permintaan.

#### 42.4.12 Kebiasaan unik (32)

Setiap kebiasaan adalah **penimpa jadwal** data (`data/kultivator/tempat/kebiasaan/<id>.json`: jam, titik, klip, prop, mitra,
syarat cuaca/musim) yang dibaca `PenimpaJadwal` 42A sebelum jadwal peran. Titik baru diberi generator 42W (42.12.1); titik
yang belum ada di tempat itu → kebiasaan memakai titik cadangan atau tidak tampil.

| # | Id | Siapa | Jam / titik | Yang terlihat (klip 42.12.5, prop) | Akibat | Ilham |
|---|---|---|---|---|---|---|
| 1 | `pemabuk` | dewasa pria/wanita, tidak RAHIB | 19-23 `kedai_arak`/`kursi` kedai | `minum_arak` (cawan 171), pulang `jalan_mabuk`, 10 % `tidur_bangku` di jalan sampai pagi | kesehatan −2/tahun, gosip, kadang berkelahi | Li Bai, Liu Ling |
| 2 | `kakek_catur` | tetua pria | 9-11, 14-16 `catur` (bawah pohon) | dua kakek `catur` (papan 24), penonton 1-3 `tonton_catur` berkomentar (`tunjuk`, `geleng`) | sahabat terbentuk ×2 | *xiangqi* taman |
| 3 | `gosip_sumur` | wanita ≥ 25 | 7-9, 16-17 `sumur` | `basuh_pakaian` + `bisik`/`tertawa` bergantian, ember 19 | **pusat gosip** (sebar ×3, 42.5.4) | *shijing* |
| 4 | `burung_sangkar` | tetua | 6-8 jalan → `gantung_sangkar` (pohon) | membawa sangkar (172), menggantung di dahan, duduk mendengar, peluit kecil | merpati mendekat | *liu niao* |
| 5 | `tai_chi` | siapa saja ≥ 30 | 5-6:30 alun/taman | `tai_chi_pagi` berkelompok, dipimpin tetua (varian pemimpin) | kesehatan +3/tahun | kebiasaan taman |
| 6 | `pemancing` | pria ≥ 16 | 15-18 `pancing` (tepi air) | `memancing`, kadang ikan (prop 186) diangkat | membawa ikan pulang | — |
| 7 | `adu_jangkrik` | pria ≥ 14 | 14-17 `jongkok` di gang | 3-5 orang jongkok mengelilingi guci (173), `sorak` | pemain bisa bertaruh kecil (sink) | Jia Sidao |
| 8 | `penyair` | CENDEKIA/sastrawan | 10-12 paviliun, 20-21 kedai | `menulis` dengan kuas, `deklamasi` berdiri satu tangan terangkat | pantun festival, gosip indah | Li Bai |
| 9 | `pemain_erhu` | siapa saja | 18-20 serambi rumah | `main_erhu` (174), melodi klien (42.12.6) | suasana tetangga +2 | Abing |
| 10 | `pendoa_kuil` | siapa saja | 6-7 kuil | `sujud_dupa`, dupa 20 | — | — |
| 11 | `layang_layang` | anak, dewasa muda | 15-17 lapangan, musim semi/angin | `main_layangan` (27) | — | — |
| 12 | `penyulam` | wanita | 13-16 serambi | `menyulam` (bingkai 175) | jual sulaman di pasar hari 0 | — |
| 13 | `tidur_siang` | siapa saja | 12-14 bangku teduh | `tidur_duduk` | — | — |
| 14 | `pesolek` | dewasa muda | 10-12 pasar | `bercermin` (cermin 176), jalan lambat melirik lapak kain | beli kain/kosmetik (toko pemain) | — |
| 15 | `pelit` | siapa saja | pasar | `tawar` 3 putaran, menghitung koin `hitung_koin` | toko pemain: pembeli tawar | — |
| 16 | `dermawan` | dewasa | siang | memberi koin ke pengemis (`beri_koin`), permen ke anak | suasana anak +, kesan ke pemain dermawan + | — |
| 17 | `pemarah` | dewasa | acak | `marah_tunjuk` ke pedagang/anak | hubungan −, perkelahian ×2 | — |
| 18 | `takhayul` | siapa saja | pagi | membaca almanak (*huangli*, kitab merah 177), menghindari gagak, membakar dupa di pintu | menolak keluar di hari "sial" | almanak rakyat |
| 19 | `kutu_buku` | anak, CENDEKIA | kapan saja | `jalan_baca` (berjalan sambil membaca) | ahli CENDEKIA +, anak calon sarjana | — |
| 20 | `teman_kucing` | siapa saja | sore | kucing kota mengikuti, `beri_makan_kucing` jongkok | kucing tinggal di rumahnya | Lu You |
| 21 | `penjudi` | dewasa | 20-24 `meja_dadu` distrik malam | `lempar_dadu` (dadu 178), `sorak`/`menepuk_jidat` | utang, gosip, kemakmuran rumah tangga turun | Guanpu |
| 22 | `pencerita_anak` | tetua | 18-19 serambi/alun | `cerita` ke 2-4 anak `duduk_lantai` | anak suasana +, gosip lama | — |
| 23 | `tari_kipas` | wanita ≥ 30 | 6-7 atau 19-20 alun | barisan `tari_yangge` dengan kipas 23 atau selendang 179 | festival: ikut arak-arakan | *yangge* |
| 24 | `penyapu_jalan` | siapa saja | 5-6 depan rumah | `sapu` | debu partikel; tetangga kesan + | — |
| 25 | `pelukis_gula` | dewasa | hari 0/6 sore alun | `lukis_gula` (sendok 180), anak antre | jual permen gula (pemain bisa beli: item `permen_gula`) | *tanghua* |
| 26 | `pecinta_teh` | dewasa | 15-17 kedai teh | `minum_teh`, mengomentari teh (gelembung) | kedai lebih ramai | — |
| 27 | `penggemar_opera` | siapa saja | 20-22 teater bayang | `tonton` + bersenandung | — | *piyingxi* |
| 28 | `pengintip` | wanita/pria tua | siang | `intip` (berjinjit di pagar) | tahu semua gosip (sumber terbaik untuk pemain) | — |
| 29 | `penabung` | dewasa | hari 3 Rumah Uang | ke bank, `hitung_sempoa` | kemakmuran rumah tangga + | — |
| 30 | `pemuja_kultivator` | anak, remaja | saat pemain ≥ Fondasi lewat | mengikuti pemain 20 blok, `kagum`, minta melihat jurus | pemain `[Peragakan jurus]` → kesan +10, anak berakar: peluang ingin masuk sekte + | — |
| 31 | `pemalas` | dewasa | pagi | bangun 2 jam terlambat, `menguap` | ahli lambat, majikan menegur (`marah_tunjuk`) | — |
| 32 | `pekerja_keras` | dewasa | 5-21 | buka toko pertama, tutup terakhir | ahli cepat, usaha +laba | — |

#### 42.4.13 Pembaruan murah: Hari Warga

* **Satu pass per tempat per hari dunia** pada pergantian hari 06:00 (tick 0) untuk **semua** tempat yang pernah aktif
  (`WargaData` berisi tempat itu), satu tempat per tick bergiliran (`HariWarga.antrian`). Langkah (O(n)): ulang tahun yang
  jatuh hari ini (42.4.6), 2 pertemuan per warga (42.4.5), tukar gosip di titik kumpul (42.5.4), suasana (42.4.11), isi slot
  (42.4.7), keberangkatan/kedatangan (42.4.8), antre upacara (42.7.7), peluruhan ingatan, statistik tempat (kemakmuran 42.9.4).
  Ibu kota 650 warga ≈ 650 × ±40 operasi ≈ < 1 ms.
* **Kejar-ketinggalan**: hari yang terlewat (server mati, dunia lama dibuka) dijalankan saat tempat pertama kali disentuh: ≤
  `kotaWarga.kejarHari` 60 hari penuh per tempat, sisanya diringkas (hanya umur, kelahiran, kematian; tanpa pertemuan).
* **Bangkit pertama**: saat tempat pertama kali aktif, `WargaGen.bangkitkan(huni)` mengisi setiap slot `PendudukGen` dengan
  Warga baru: umur sesuai peran (ANAK 3-12, TETUA 60-80, kerja 18-60), rumah tangga dari `kelompok` (orang tua + anak + kakek
  satu keluarga dengan marga sama, pasangan dibuat dari dua dewasa beda kelamin dalam rumah tangga), hubungan awal (2-4
  tautan acak terbobot), arsip leluhur kosong. Deterministik dari `(situs, benih dunia)`.

#### 42.4.14 Kait ke 38NL dan penyatuan data (42A pemilik tunggal berkas `dunia/tempat/hidup/**` selama 42)

| Berkas | Suntingan (aditif kecuali disebut) |
|---|---|
| `PendudukData` | **penyatuan**: buang `mati`, `gen`, `datang`, `kenal`, `_duka` (pindah ke `WargaData`); tetap `luka` dan `tanda`. `terapkan(situs, p)` → `WargaApi.terapkan(situs, p)`; `absen` → slot kosong/warga `JALAN`/bayi = absen; `keadaan` → `WargaApi.keadaan(situs, id, now)` (menambah `MABUK`, `SAKIT`, `PENGANTIN`, `BERKABUNG`); `kenal/kenalan` meneruskan ke `Ingatan`. Konstanta `SEHAT/PULIH/DUKA/DATANG` tetap (dipakai `TubuhPenduduk`). |
| `Jadwal` | antarmuka `PenimpaJadwal { @Nullable Kegiatan timpa(TempatHuni huni, Penduduk p, long waktu, int jam, long now); }` + `Jadwal.addPenimpa(PenimpaJadwal)`; dipanggil sebelum jadwal peran dan sesudah `timpaan` acara/bahaya. |
| `TempatHuni` | `addSumberPenduduk(Function<TempatHuni, List<Penduduk>>)` (slot tambahan), `addSumberTitik(Function<TempatHuni, List<Titik>>)` (titik bangunan kavling yang sudah jadi, 42F), `addPenolakTitik(BiPredicate<TempatHuni, BlockPos>)` (titik di dalam rumah/toko milik pemain tidak dipakai warga, 42C). |
| `Pemanggil` | saat memanggil tubuh: nama papan `WargaApi.namaPapan(situs, p)` (bila ada), `SosokApi.setUsia(npc, WargaApi.usiaSosok(...))`, `SosokApi.setSuasana(npc, WargaApi.wajah(...), 0)`. |
| `TubuhPenduduk` | jalan berpasangan (kekasih, kakek-cucu) lewat `WargaApi.mitraJalan(npc)`; menghindari musuh (biaya simpul); reaksi `kota_suasana` (42.7). |
| `HidupApi` | tidak berubah tanda tangannya; 42 hanya memanggil. |
| `HidupCommands` | sub `audit` mencetak warga per tubuh (nama, umur, suasana) — aditif. |
| `Pemanggil` (tambahan 42S) | `addPemilihTubuh(PemilihTubuh)`: strategi memilih warga yang diberi tubuh/impostor (bawaan = urutan lama "stationary dulu, terdekat"); 42S mendaftarkan pembagian proporsional per zona (42.4A.2) |
| `TempatHuni` (tambahan 42S) | `addPenyeimbang(Penyeimbang)`: memilih titik untuk tujuan umum (bawaan = titik pertama yang cocok); reservasi titik berkedaluwarsa |
| `TubuhPenduduk` (tambahan 42S/42K) | `addSelingan(PenyediaSelingan)` (selingan mikro 42.4A.5) dan `addUjaran(PenyediaUjaran)` (gelembung obrolan 42.5A.9 menggantikan `DATA_LINE` bila aktif) |

`WargaApi.dari(KultivatorNpc)` = `HidupApi.pendudukDari(npc)` + `situs` → Warga; NPC non-penduduk (jangkar `kota/warga` 34,
staf `tubuhStaf`) dipetakan ke warga tempat itu yang berperan sama dan sedang tidak bertubuh (atau Warga staf tetap untuk
slot layanan) sehingga **setiap** orang sipil punya nama dan ingatan.

---------------------------------------------------------------------------------------------------------------------

### 42.4A Sebaran merata dan kegiatan yang tidak monoton (modul 42S)

Permintaan susulan pengguna (2026-10-24, mengikat): "Pastikan penempatan NPC tersebar merata dan apa yang mereka lakukan tidak
monoton." 42.4A memisahkan dua pertanyaan yang sekarang tercampur di 38NL: **jenis** kegiatan pada jam itu (tetap milik jadwal
peran + `Jadwal.ramai` yang sudah dibangun: pasar pagi 07-11, makan siang 12, teh/jalan 13-16, belanja 17, kedai malam 20-22)
dan **di mana tepatnya serta variasi apa** (baru, 42S). Fakta kode: `Pemanggil` memanggil tubuh dalam `PANGGIL` 128 blok,
`BARU_PER_SIKLUS` 48 per `SIKLUS` 20 tick, mengurutkan calon "stationary dulu, lalu yang **terdekat** dengan pemain"
(`Pemanggil.java:389`), dan impostor juga "terdekat dulu" — akibatnya tubuh menumpuk di sekitar pemain dan tepi pandangan
kosong; `TempatHuni.tujuan` mengunci satu orang per titik (antre 3 blok) tetapi memilih titik pertama yang cocok, sehingga
lot dekat rumah penuh sementara lot lain kosong.

#### 42.4A.1 Zona, kapasitas dan target per jam (`PetaZona`, `Kapasitas`)

* **Zona** per tempat, dihitung sekali saat tempat aktif dari titik dan simpul `TempatHuni` (dan bertambah saat kavling jadi):
  ibu kota = 11 distrik × (lot, ruas jalan, alun); kota tema = 9 distrik; kota 34 = alun, jalan utama, 4 jalan samping, gerbang,
  luar tembok; kampung = pusat, rumah, ladang, tepi air; kompleks = halaman, dalam, puncak. Di dalam zona: **bangunan** (kotak
  `bangunan`), **ruas jalan** (potongan graf simpul ±16 blok), **lapangan** (alun, taman, dermaga).
* **Kapasitas dasar** `K` = jumlah tempat yang masuk akal: kursi 1, meja_makan 1, lapak 2 (penjual + pembeli), kerja 1, kasur 1, obrol
  2, main 3, catur 2 + 3 penonton, sumur 4, panggung 12, ruas jalan `panjang / 6` pejalan, lapangan `luas / 25`.
* **Pengali jam** (`data/kultivator/tempat/ragam/zona.json`, contoh):

| Jenis zona | 04-06 | 06-08 | 08-11 | 11-13 | 13-17 | 17-19 | 19-23 | 23-04 |
|---|---|---|---|---|---|---|---|---|
| pasar / lapak | 0,1 | 0,8 | **1,0** | 0,6 | 0,6 | 0,8 | 0,2 | 0 |
| rumah makan / warung | 0,1 | 0,6 (sarapan) | 0,3 | **1,0** | 0,4 | **0,9** | 0,5 | 0 |
| kedai teh | 0 | 0,3 | 0,5 | 0,5 | **0,9** | 0,6 | 0,5 | 0 |
| kedai arak / distrik malam | 0 | 0 | 0 | 0,2 | 0,2 | 0,5 | **1,0** | 0,4 |
| rumah | **1,0** | 0,6 | 0,25 | 0,4 | 0,25 | 0,5 | 0,7 | **1,0** |
| kuil | **1,0** (subuh) | 0,6 | 0,3 | 0,2 | 0,3 | 0,6 (senja) | 0,2 | 0 |
| sekolah | 0 | 0,2 | **1,0** (07-10 anak) | 0 | 0 | 0 | 0 | 0 |
| bengkel / kerja | 0 | 0,5 | **1,0** | 0,4 | **1,0** | 0,3 | 0 | 0 |
| ladang / tepi air / dermaga | 0,2 | 0,9 | **1,0** | 0,4 | 0,9 | 0,4 | 0 | 0 |
| alun / taman | 0,6 (tai chi) | 0,7 | 0,5 | 0,4 | 0,7 | **0,9** | 0,7 (lentera) | 0,05 |
| ruas jalan | 0,1 | **0,9** | 0,6 | 0,7 | 0,6 | **0,9** | 0,5 | 0,05 |
| gerbang / tepi kota | 0,2 | 0,6 | 0,5 | 0,4 | 0,5 | 0,6 | 0,3 | 0,1 |

  Acara (festival, upacara, rombongan, Pasar Hantu) menimpa pengali zonanya untuk durasi acara (tanpa melebihi kapasitas fisik).
* **Target** per zona per jam: `T_z = K_z × m_z(jam) / Σ_z (K_z × m_z(jam)) × N_bebas(jam)` (N_bebas = warga tanpa tugas tetap jam
  itu; staf, penjaga di pos dan pekerja di titik kerjanya sudah dihitung sebagai penghuni zona kerjanya). **Topi** `C_z =
  ceil(kapZona × T_z) + 1` (bawaan 1,25); **lantai** `L_z = floor(lantaiZona × T_z)` (0,5) untuk zona berkapasitas ≥ 4 agar tidak
  ada distrik kosong melompong. Topi per bangunan = jumlah titiknya + antre ≤ 2; per titik satu orang.

#### 42.4A.2 Penyeimbang hunian dan anti-gerombol (`Penyeimbang`)

* **Pemilihan tempat untuk tujuan umum** (`pasar`, `kedai`, `makan`, `jalan_jalan`, `obrol`, `main`, `teh`, `kuil`, `belanja` dari
  jadwal/`Jadwal.ramai`/rencana harian): undian berbobot di antara titik dari jenis itu pada **semua** zona tempat:
  `w = max(0, C_z − occ_z) / C_z × e^(−jarakDariRumah / 220) × preferensi (kebiasaan, sahabat di sana, toko langganan) × (2 bila occ_z
  < L_z)`. Titik dipesan (`reservasi` dengan kedaluwarsa = durasi kegiatan + 1 menit dunia); titik penuh → **luapan** ke titik
  sejenis terdekat di zona yang masih di bawah target; tidak ada titik → kegiatan diganti alternatif dari kumpulan 42.4A.3.
* **Anti-gerombol**: tubuh yang tidak berinteraksi menjaga jarak ≥ `jarakMin` 1,5 blok (tujuan digeser ke sel kosong tetangga
  dalam radius 3; kelompok 42.4A.6 memakai formasi sendiri); antre di depan titik ≤ 2 lalu belok ke luapan; ruas jalan yang
  melebihi topi menurunkan bobot rute melewatinya (rute jalan-jalan memilih ruas sepi).
* **Penyeimbangan per siklus** (setiap `SIKLUS` Pemanggil, satu tempat per tick): hitung `occ_z` data (semua warga, bukan hanya
  bertubuh); bila `occ_z > C_z` warga dengan sisa durasi terpendek dipindahkan ke zona kurang terisi pada pergantian kegiatan
  berikutnya (tidak pernah memutus kegiatan di tengah klip).
* **Tubuh (L0/L1) dan impostor (L2)** merata: `Pemanggil` tidak lagi murni "terdekat dulu". 42A menambah kait
  `Pemanggil.addPemilihTubuh(PemilihTubuh)`; 42S mengisi: (1) staf dan peran stationary tetap didahulukan (perilaku audit
  `audit-npc-tempat.md`); (2) sisa anggaran tubuh dalam `PANGGIL` dibagi ke zona yang terlihat secara **proporsional** jumlah warga
  data yang tujuannya di zona itu (metode sisa terbesar), minimal 1 tubuh per zona yang punya warga dan dalam 96 blok; (3) di dalam
  zona baru dipilih yang terdekat pemain. Impostor memakai pembagian yang sama sehingga tepi pandangan tetap berpenghuni.
  `BARU_PER_SIKLUS`, `LEPAS`, `JAUH` dan topi `npc_hidup` tidak berubah.
* `Jadwal.ramai` tetap menentukan *apa* (pasar/makan/kedai/jalan) dan *siapa yang bebas*; 42S hanya mengganti "titik pertama
  yang cocok" dengan Penyeimbang lewat kait `TempatHuni.addPenyeimbang(Penyeimbang)` (42A) dan menyisipkan ragam 42.4A.3-42.4A.6
  lewat `PenimpaJadwal`. Urutan penimpa: acara/bahaya > upacara > kebiasaan > **rencana harian 42S** > `ramai` > jadwal peran.

#### 42.4A.3 Kumpulan kegiatan per peran (`KumpulanKegiatan`, data `data/kultivator/tempat/ragam/peran/<peran>.json`)

Setiap kegiatan: `{id, blok (kerja/bebas/makan/malam), titik, klip + varian, prop, durasi [min, max] menit dunia, syarat (musim,
cuaca, hari, sifat, kebiasaan), bobot, selingan (daftar 42.4A.5)}`. Minimal **10 kegiatan per peran umum** (sub-varian = klip/prop/
titik berbeda); peran 38.T memakai kumpulan temanya ditambah kumpulan kelompok jadwalnya.

| Peran | Kegiatan (sub-varian dalam kurung) |
|---|---|
| PETANI | mencangkul (lurus/belokan), menanam bibit, menyiangi (`menyiangi`), menyiram (ember/gayung), memanen (musim), memikul hasil ke pasar, memberi makan ayam, memperbaiki pagar (palu), istirahat di pematang (duduk minum/kipas), mengasah cangkul, mengusir burung, berjualan di pasar desa |
| PEDAGANG (lapak) | berseru (3 gaya), menata barang, menghitung sempoa, melayani tawar, menyapu lapak, makan di lapak, mengobrol dengan lapak sebelah, menawari pejalan, tidur ayam saat sepi, mengipas, mengusir lalat (`usir_lalat`), mengambil stok dari gudang |
| KOKI | memotong di talenan, mengaduk wajan, mencicipi, menyajikan (nampan), mencuci mangkuk, mengipas tungku, belanja sayur pagi, memanggil pelanggan, istirahat di pintu belakang, menguleni adonan, menimbang beras |
| TUKANG | memalu, menggergaji, memplester, memikul papan, mengukir, memeriksa hasil, minum istirahat, mengantar barang jadi, naik perancah (kavling/perbaikan), mengambil kayu, menegur magang |
| KULI | memikul peti, mendorong gerobak, mengangkut karung, bersandar istirahat, minum, makan bakpao, adu jangkrik, menunggu kafilah, mengikat muatan, bongkar muat di dermaga/gudang |
| PENJAGA | jaga di pos, patroli (3 rute), memeriksa pedagang, menegur anak, minum teh di pos, latihan tombak, ganti regu (`hormat`), menolong nenek menyeberang, mengobrol dengan rekan, memeriksa gerbang, mengusir pemabuk (malam) |
| ANAK | kejar-kejaran, lompat tali, gasing, layangan, cuju, petak umpet (`sembunyi_tong`), memberi makan kucing, membantu orang tua (bawa kecil), sekolah, menonton pengamen/akrobat, beli permen/tanghulu, main air (panas), mengikuti pemain (pemuja) |
| TETUA | catur, berkipas, bercerita ke anak, tai chi, jalan burung sangkar, duduk di serambi, ke kuil, menonton catur, minum teh, menggandeng cucu, tidur siang, menyapu halaman |
| WARGA umum | pasar pagi, makan di rumah makan, minum teh, jalan-jalan (rute ruas sepi), mengobrol di sumur/obrol, menimba air, menjemur, menyapu, mengunjungi kerabat, ke kuil, menonton rombongan, ke Rumah Uang, ke tabib, duduk di jembatan |
| NELAYAN | memancing, menjala, menambal jala (`tambal_jala`), menjemur ikan, menjual ikan, mendayung (bila ada perahu), membersihkan ikan, mengobrol di dermaga, minum di kedai, memperbaiki perahu (palu) |
| RAHIB | menyapu kuil, sujud dupa, membaca sutra, memukul lonceng, membagi bubur, menjenguk orang sakit, meditasi berjalan, menerima peziarah, memimpin upacara, merawat kebun kuil |
| CENDEKIA / PUSTAKAWAN | menulis, membaca, mengajar, menyalin kitab, berdiskusi (dua tubuh), minum teh, jalan sambil bersyair, merapikan rak, memberi les anak, mengunjungi toko buku |

#### 42.4A.4 Rencana harian yang berbeda tiap hari (`RencanaHarian`)

* Dibuat malas saat warga pertama kali dievaluasi pada hari itu (benih `warga ^ hari`): setiap blok jadwal peran (mis. "kerja
  07-12", "bebas 13-16") diisi 2-4 kegiatan dari kumpulannya, berdurasi acak dalam rentangnya (20-90 menit dunia), dengan bobot
  `w = bobot × sifat (X: sosial ×(1 + X/100); C: kerja inti ×(1 + C/150); O: kegiatan jarang ×(1 + O/100)) × kebiasaan × musim ×
  cuaca × 0,25^(kali dilakukan hari ini) × (0,6 bila dilakukan kemarin di blok sama) × suasana/emosi (sedih: menyendiri, gembira:
  sosial)`.
* Kegiatan inti pekerjaan tetap mendominasi jam kerja (≥ 50 % waktu kerja) agar toko tetap dijaga; sisanya variasi.
* Minggu: hari 0 **hari pasar besar** (pedagang tamu, pasar ×1,3), hari 3 **hari ternak/pasar ikan** (kandang dan dermaga ramai),
  hari 6 **hari istirahat** (separuh pekerja: memancing, piknik di taman, kuil, kunjungi kerabat); musim mengganti kumpulan (dingin:
  dalam rumah ×1,4, sup; panas: tepi air; gugur: panen; semi: tanam, layangan); hari raya 42.7.6 dan upacara menimpa.

#### 42.4A.5 Selingan mikro (`Selingan`, data `tempat/ragam/selingan.json`)

Selama satu kegiatan, tiap `selinganDetik` 8-30 s (acak per warga) disisipkan satu selingan 1-3 s yang cocok dengan kegiatan dan
tidak sama dengan selingan sebelumnya: menoleh ke sekitar (`lihat_sekeliling`), meregang (`peregangan_pagi` pendek), **menyeka
keringat** (`seka_keringat`, panas/kerja berat), memeriksa barang (`lihat_barang`), melirik pemain yang lewat (`HidupApi.tatap` 2 s),
bicara singkat dengan tetangga (satu ujaran 42K), menggaruk kepala (`garuk_kepala`), menguap, membetulkan topi (`betulkan_topi`),
minum dari kendi, bersiul (gumam 73), menepuk debu baju, menghitung koin, menyapa anak yang lewat. Bobot dari sifat (E tinggi:
lebih banyak menoleh; X: lebih banyak bicara) dan keadaan (lelah setelah 2 jam kerja: seka keringat/meregang ×2).

#### 42.4A.6 Belokan spontan dan kegiatan berkelompok (`Belokan`, `KegiatanKelompok`)

* **Belokan**: saat berjalan antar-tujuan, `belokanPeluang` 12 % per perjalanan bila ada daya tarik ≤ 24 blok dari rute: beli jajanan
  di lapak (tanghulu/bakpao, prop dibawa sambil makan), mengelus kucing kota, menonton pengamen/akrobat/pendongeng (masuk kerumunan
  bila kapasitas masih ada), menyapa sahabat (berhenti, 2-4 ujaran), membaca papan pengumuman, memberi koin pengemis (dermawan),
  melongok ke toko (pesolek), duduk sebentar di bangku, anak bermain air di sumur. Maks `belokanMaks` 3 per warga per hari.
* **Berkelompok** (formasi jarak 1,2, tiba bersamaan, memesan beberapa titik bersebelahan): keluarga jalan sore (18-19, 20 % rumah
  tangga per hari), sahabat bertemu di kedai teh (jadwal tumpang-tindih pasangan `SAHABAT`), anak 2-5 kejar-kejaran, kekasih jalan
  senja, tetua berkumpul main catur, rekan kerja makan siang bersama, rombongan pembeli hari pasar, kelompok festival. Kelompok
  terhitung satu unit dalam Penyeimbang (kapasitas zona dikurangi sebanyak anggotanya).

#### 42.4A.7 Klip tambahan (450-459 untuk 42S, dibuat 42V; 460-469 cadangan)

| Id | Klip | Durasi | Kunci ringkas |
|---|---|---|---|
| 450 | `seka_keringat` | once 30 | RAX −150 ke dahi menyapu (RAY −30 → +20), HX −10 |
| 451 | `garuk_kepala` | once 24 | RAX −160, RAZ ±8 cepat, HX 10 |
| 452 | `betulkan_topi` | once 20 | RAX/LAX −165 memegang tepi topi |
| 453 | `lihat_sekeliling` | once 40 | HY −60 → +60 lambat, TWIST ikut ±15 |
| 454 | `menyiangi` | loop 30 | jongkok LEAN 55, RAX −40 → −10 mencabut |
| 455 | `usir_lalat` | once 16 | RAX −90 mengibas RAZ ±40 |
| 456 | `tambal_jala` | loop 30 | duduk, kedua tangan −60 menjalin (RAY/LAY ±15) |
| 457 | `sembunyi_tong` | loop 20 | jongkok di balik titik, HY mengintip ±40 |
| 458 | `tepuk_debu` | once 20 | LAX/RAX −30 → −10 menepuk baju bergantian |
| 459 | `piknik_duduk` | loop 80 | `duduk_lantai` + makan dari kotak |
| 460-469 | cadangan 42S | — | — |

#### 42.4A.8 Ukuran keberhasilan, uji dan alat debug (`MetrikRagam`, `RagamUji`)

| Metrik | Sasaran (diuji `uji kota_hidup ragam laporan` dan simulasi 7 hari) |
|---|---|
| Kegiatan berbeda per warga bebas per hari | rata-rata ≥ 5 (min 4); staf/stationary ≥ 3 (lewat sub-varian dan selingan) |
| Pengulangan kegiatan yang sama per warga per hari (di luar kerja inti) | ≤ 2 kali |
| Kemiripan rencana dua hari berturut (Jaccard) | rata-rata ≤ 0,6 |
| Selingan | 2-7 per menit nyata per tubuh, tidak ada selingan sama berturut |
| Belokan | 0,5-2 per warga per hari |
| Bagian tubuh terbanyak di satu zona yang terlihat | ≤ 1,5 × bagian wajarnya dan ≤ 35 % bila ≥ 4 zona terlihat (kecuali acara di zona itu) |
| Gini hunian relatif kapasitas antar-zona | ≤ 0,35 |
| Zona kosong (K ≥ 4, T ≥ 1, occ 0) | ≤ 10 % |
| Pelanggaran jarak minimum | ≤ 2 % pasangan tubuh |

Perintah (op): `uji kota_hidup sebaran peta` (lapisan klien peta panas sel 16 × 16 di dunia dan di minimap: warna = hunian/target,
debug saja, payload `kota_peta_panas` ≤ 4 KB, 1 Hz), `sebaran zona` (tabel zona: K, pengali jam, target, topi, lantai, hunian data,
tubuh), `sebaran titik` (titik dipesan diberi partikel warna menurut pemesan), `sebaran simulasi <hari>` (simulasi tanpa tubuh,
cetak metrik per jam), `ragam <warga>` (rencana hari ini, riwayat 3 hari, selingan terakhir), `ragam laporan [hari]` (semua metrik
tabel di atas: lulus/gagal).

---------------------------------------------------------------------------------------------------------------------

### 42.5 Bicara dengan siapa saja (A1; modul 42B)

#### 42.5.1 Alur klik kanan

1. `Kota.interact` → `TempatDialog.interact(player, npc)` (38ST, kait yang ada). 42B menambah **satu baris** di
   `TempatDialog.interact`: sebelum `if (!role38 && tokoh.isEmpty()) return false;` → `if (BicaraWarga.boleh(npc)) role38 =
   true;` sehingga **semua** orang sipil (`npc.role().townsfolk()` atau tubuh penduduk/jangkar warga) masuk jalur dialog.
   Nama Baik ≤ `Kota.HOSTILE_REPUTATION` tetap ditolak seperti sekarang.
2. Dialog data 38 (tokoh, quest) tetap didahulukan (`pilih(...)`). Bila tidak ada, lembar sapa diganti lembar
   **`BicaraWarga.lembar`**: baris pembuka dari Mesin Percakapan Lanjut (42.5A; sumbu konteks 42.5.2), potret, nama, umur, pekerjaan, ikon hubungan dengan
   pemain, lalu tablet: **Bicara**, **Kabar**, **Tanya Jalan**, **Hubungan**, **Tolong** (permintaan), **Beri**, **Jasa** (bila
   ada; peran lama 34 seperti `PEDAGANG_PIL` membuka layar lamanya lewat op `jasa:kota_<tab>` → `KotaNet` yang ada),
   **Quest** (38).
3. Tubuh berhenti dan menatap (`HidupApi.tatap`), klip `bicara_tangan`/gestur templat, gumam 73 (`ObrolanSuara`), suara
   VOICEVOX untuk baris yang punya kunci suara (42.12.7). Dialog berakhir: tubuh kembali ke jadwalnya, `Ingatan.sapa`.

#### 42.5.2 Sumbu konteks (dipakai 42.5A)

**Catatan penyatuan:** bila `kotaBicara.mesinLanjut` aktif (bawaan), format baris tunggal di bawah **tidak dibuat** sebagai
berkas `tempat/bicara/*.json`; sumbu dan isiannya menjadi `syarat` dan slot aturan tata bahasa 42.5A.8, contoh baris menjadi
contoh bingkai, dan target "≥ 1.400 baris" diganti target isi 42.5A.8 (≥ 900 bingkai, ≥ 2.400 terminal). Bila mesin dimatikan,
hanya sapaan 38 (`Kalimat`) yang tersisa.


Setiap baris: `{id, topik, syarat {peran?, kelompok?, tahap?, kelamin?, watak?, kebiasaan?, jam?, cuaca?, musim?, acara?,
suasana?, kesan?, kenangan?, hubungan?, tempat?}, bobot, teks (kunci lang), isian[], gestur?, suara?}`. Pemilihan: semua
baris yang syaratnya terpenuhi diberi skor `bobot × (1 + 0,5 × jumlah syarat yang cocok)` (baris yang lebih spesifik
menang), diundi berbobot; baris yang sama tidak diulang ke pemain yang sama dalam 3 hari dunia (`Ingatan` menyimpan 8 id
terakhir).

| Sumbu | Nilai |
|---|---|
| Peran/kelompok | 112 peran `NpcRole` / 10 kelompok; tahap umur (anak, remaja, dewasa, tua) |
| Jam | `subuh` 4-6, `pagi` 6-11, `siang` 11-15, `sore` 15-18, `malam` 18-22, `larut` 22-4 |
| Cuaca/musim | cerah, hujan, badai, salju, kabut Qi (`DuniaApi.sky().weatherAt`), gerhana/hujan meteor; semi/panas/gugur/dingin |
| Peristiwa | `kota_suasana` (42.13.2): festival, nikah, makam, lahir, serangan, perang, kebakaran, copet tertangkap, Pasar Hantu semalam, rombongan datang, harga pangan tinggi, kavling baru jadi, pembangunan kembali, pelindung sekte baru |
| Suasana | 7 kode wajah 42.4.11 |
| Kesan pemain | `musuh` ≤ −40, `asing` (belum kenal), `kenal`, `akrab` ≥ 30, `sahabat` ≥ 60; tahu nama atau tidak |
| Kenangan | 14 id 42.4.10 (mis. `DISELAMATKAN` → "Tuan yang menyelamatkanku dari api!") |
| Hubungan warga | punya pasangan/kekasih/musuh/sahabat/anak berakar/almarhum baru |

Isian: `{pemain}` (nama bila dikenal, selain itu sebutan menurut ranah: "Tuan Kultivator", "Nona Pendekar", "Senior"),
`{warga}`, `{pasangan}`, `{anak}`, `{sahabat}`, `{musuh}`, `{almarhum}`, `{kerja}`, `{tempat}`, `{distrik}`, `{acara}`,
`{barang_mahal}`/`{barang_murah}` (dari `HargaKota`), `{arah}`, `{jarak}`, `{sekte_pelindung}`, `{tahun}`.

**Cakupan topik** (ditulis sebagai bingkai 42.5A.8 oleh `tools/gen_kota_hidup_cakap.py`): 20 topik × 10 kelompok, ditambah bingkai
khusus peran 38/38.T, kebiasaan, kenangan dan anak. Contoh realisasi:

| Topik / syarat | Baris (id) |
|---|---|
| sapa · PEKERJA · pagi · senang | "Pagi, {pemain}! Adonan bakpao sudah naik, nanti mampir ya." |
| sapa · PEDAGANG · hujan | "Hujan begini pembeli sepi... Tuan mau payung? Murah saja." |
| keluarga · punya bayi baru | "Anakku lahir tiga hari lalu! Namanya {anak}. Tangisnya keras, pasti jadi pendekar." |
| duka · almarhum baru | "Maaf, aku tak bisa bicara lama. {almarhum} baru saja dimakamkan." |
| kenangan `DISELAMATKAN` · sahabat | "Setiap lihat api tungku aku ingat Tuan. Kalau bukan karena Tuan, rumah kami sudah jadi abu." |
| harga · harga pangan tinggi | "Beras naik lagi. Kata orang kafilah dari selatan tertahan begal." |
| sekte · dilindungi sekte pemain | "Sejak panji {sekte_pelindung} berkibar di gerbang, anakku berani main sampai sore." |
| kebiasaan `pemabuk` · malam | "Hik... bulan itu... dua? Tidak, satu... temani aku minum, Saudara!" |
| anak · pemuja_kultivator | "Kakak bisa terbang? Sekali saja, tunjukkan pedangnya! Aku janji tidak bilang ibu!" |
| takut · serangan aktif | "Masuk! Cepat masuk! Binatang-binatang itu sudah di luar tembok!" |
| musuh · `{musuh}` dekat | "Lihat itu, {musuh} lewat. Ayamku hilang sejak dia pindah ke sebelah." |

#### 42.5.3 Topik tablet Bicara

`[Apa kabar?]` (sapa/kerja), `[Ceritakan keluargamu]`, `[Bagaimana dagangan?]` (harga dan barang langka hari ini),
`[Ada kabar?]` (pindah ke tablet Kabar), `[Apa cita-citamu?]` (mimpi warga, bisa membuka permintaan), `[Tentang kota
ini]` (lore tempat 38.S.7 + sejarah warga: "Kakekku ikut membangun gerbang ini"), `[Tentang sekteku]` (pendapat tentang sekte
pemain: dari kesan, perlindungan, murid yang pernah datang), `[Perkenalkan diri]` (warga tahu nama pemain), `[Peragakan
jurus]` (pemuja kultivator, anak), `[Pamit]`.

#### 42.5.4 Gosip: lahir, menyebar, memudar (`GosipWarga`)

* **Rekam**: `Gosip(id, templat, subjek[≤ 2] (warga/pemain/tempat), asal (situs), lahir (hari), benar (BENAR/BUMBU/PALSU),
  panas 0-100, isian[])`; ≤ 64 gosip aktif per tempat (yang terdingin dibuang). Tiap warga mengenal ≤ 12 id gosip.
* **Lahir dari peristiwa nyata**: perbuatan pemain (`WargaApi.peristiwaPemain(player, situs, jenis, isian…)`: menolong,
  mencuri, menang duel di arena, membayar tagihan, menikahkan pasangan, merekrut anak), peristiwa warga (nikah, lahir, wafat,
  pindah, perkelahian, copet tertangkap, usaha bangkrut, kekasih kawin lari), ekonomi (harga naik, toko pemain baru, kontrak),
  dunia (setiap `KabarTempat.pemicu` 38 juga menyalakan gosip di tempat itu — **satu sumber**, kedai teh tetap membaca
  `KabarTempat`).
* **Menyebar**: di Hari Warga dan saat dua tubuh mengobrol (38.N.4 butir 8), tiap gosip berpindah dengan peluang
  `0,25 × panas / 100 × pengali` (sahabat ×2, pasangan ×2, keluarga ×1,5, titik `sumur` ×3, kedai teh ×2, pasar ×1,5,
  kebiasaan `gosip_sumur`/`pengintip` ×2); penerima watak `curiga` ×0,5. **Bumbu**: 15 % per lompatan isian dibesar-besarkan
  ("dua copet" → "lima copet") atau subjek tertukar (warga watak licik 30 %); gosip PALSU lahir 3 %/hari dari warga `licik`
  tentang musuhnya.
* **Memudar**: panas −10 per hari; gosip tentang pemain mengubah kesan penerima ±3 (sekali per gosip).
* **Pemain mendengar** di tablet **Kabar**: 1-3 gosip yang diketahui warga itu, gaya bicara menurut watak (penakut
  membesarkan bahaya, bijak menyangsikan bumbu: "Tapi kau tahu sendiri, Bibi Wang suka melebihkan."). Ikon kebenaran tidak
  ditampilkan; pemain bisa `[Tanya siapa yang bilang]` (menunjuk penyebar sebelumnya) → menelusuri rantai sampai sumber
  (berguna untuk alur Pasar Hantu dan perkara Hakim Pembantu).
* `[Sebarkan kabar baik sekteku]` (pemain punya aula, sekali per hari per tempat, 5 NB untuk teh): lahir gosip "sekte <x>
  menerima murid" → peluang pelamar Kantor Rekrutmen +10 % 3 hari.

#### 42.5.5 Tanya jalan

Tablet **Tanya Jalan**: tiga daftar.

* **Layanan di tempat ini** (dari `TempatApi`/jangkar layanan/titik bernama: kedai teh, tabib, Rumah Uang, gudang, kantor
  rekrutmen, Pasar Hantu malam ini (hanya warga yang tahu), rumah pemain).
* **Orang**: warga bernama dan tokoh yang dikenal pemain atau warga ("Di mana Zhang Ming'an?"), dijawab dari jadwal
  (`Jadwal.kegiatan` → titik): "Jam segini dia di Rumah Makan Teratai, sampai sore."
* **Tempat lain**: tempat yang dikenal warga (kota dalam 3000 blok dari tempatnya, gerbang sekte NPC, kampung, gua yang sudah
  diterbitkan `DuniaSites` dan didengar lewat gosip).

Jawaban: arah 8 mata angin + patokan (distrik, lot bernama, jembatan, menara) + jarak dalam "langkah" (< 300 blok) atau
"li" (1 li = 500 blok): "Ke timur, lewat Jembatan Batu Merah, kira-kira dua ratus langkah, rumah berlentera biru." Tubuh
memainkan `tunjuk` ke arah itu. `[Tandai]` → penanda sementara di peta dan panah kompas HUD 5 menit (`DuniaMarkers`
`tujuan_sementara`, verifikasi; bila tidak ada jenis sementara, 42B menambah jenis `kota/tujuan` dengan umur 5 menit).
Anak dan pendatang < 7 hari kadang tidak tahu ("Aku baru di sini, Tuan. Tanya Bibi Wang di sumur, dia tahu semua.");
warga licik minta 2 NB.

#### 42.5.6 Permintaan pribadi ("Tolong", `PermintaanWarga`, data `data/kultivator/tempat/tolong/*.json`)

Dibangkitkan dari kebutuhan nyata warga (sakit, rumah rusak, kekasih, musuh, utang, musim, anak hilang). Maks 1 aktif per
warga, 6 per tempat, 3 diterima per pemain per hari. Memakai mesin quest 38 (`QuestTracker.daftarTujuan`, `Syarat`,
`Akibat`) dengan instans dinamis (id `tolong:<situs>:<warga>:<hari>`).

| Jenis | Pemicu | Tujuan nyata | Imbalan (pita `b` pemain) |
|---|---|---|---|
| `obat_ortu` | anggota keluarga sakit | serahkan pil penyembuh atau bayar tabib | 4 × (b+1) NB atau makanan rumah, kesan +15, warga sembuh |
| `kayu_bakar` | musim dingin, rumah tangga miskin | antar 16 kayu gelondong | sup hangat ×2, kesan +10 |
| `kucing_hilang` | pemilik `teman_kucing` | cari kucing kota bernama (mengeong, jejak partikel) di gang/atap dalam 96 blok | kesan +12, kucing mengikuti pemain sehari |
| `anak_hilang` | anak bermain terlalu jauh / tersesat di luar tembok | temukan anak (tubuh dekat titik tepi kota), antar pulang | kesan +25 keluarga, gosip baik |
| `surat_kekasih` | `KEKASIH` < 70 | antar surat ke kekasih (bisa di kota lain) | hubungan +15, undangan pernikahan nanti |
| `damaikan` | dua warga `MUSUH` | bicara dengan keduanya, pilih kata (dialog 3 pilihan) | hubungan → 0 atau lebih buruk; kesan kedua pihak ± |
| `rentenir` | warga berutang ke `licik`/`serakah` | bayar utangnya, atau tekan rentenir (dialog, Nama Baik ≥ 40) | kesan +20, rentenir musuh pemain |
| `preman` | kebiasaan/peristiwa preman di kumuh | kalahkan 2-3 preman tanpa membunuh (tinju, duel ramah) | kesan +15, Nama Baik +2 |
| `barang_dicopet` | warga dicopet | cari barang di Pasar Hantu atau kejar copet | kesan +15 |
| `bantu_panen` | gugur, petani | bantu 60 s di ladang (kanal, klip `memanen`) | keranjang hasil, kesan +8 |
| `bahan_rumah` | rumah rusak (38.R, nyata) | antar bahan ke mandor | perbaikan cepat, kesan +10 |
| `ajari_anak` | anak `kutu_buku`/berakar | berdiri 60 s mengajar (klip `mengajar` 40) | ahli/aura anak +, keluarga kesan +10 |
| `cari_jodoh` | lajang 25+ | antar ke Mak Comblang / kenalkan dengan warga lain | hubungan +30 dengan calon |
| `kabar_anak_sekte` | anak keluarga menjadi murid sekte (aula pemain atau sekte NPC) | sampaikan surat; bila murid pemain: murid menulis balasan (Buku Harian 39) | kesan +15, murid Kepuasan + |
| `pinjam_uang` | kemakmuran rumah tangga < 20 | pinjamkan 10-60 NB (42.5.7) | dilunasi + bunga 0-10 % |
| `undangan` | pernikahan/pesta sebulan keluarga | hadir saat upacara (42.7.7) | makanan pesta, buff, kesan +10 |

#### 42.5.7 Hadiah, utang, pinjaman

* **Beri**: barang di tangan diserahkan; selera = tabel watak × kebiasaan × peran (`tempat/selera.json`): pemabuk suka arak,
  pecinta teh suka teh, kutu buku suka kitab/giok teknik rendah, anak suka permen gula/bakpao, pesolek suka kain/bunga, teman
  kucing suka ikan, sakit suka pil; benci: pemabuk benci teh(!), rahib benci arak dan daging, anak benci obat. Kesan +8/+3/−5
  (satu berarti per 3 hari). Hadiah berharga tinggi (≥ 50 NB) ke warga miskin → kesan +15 dan gosip "dermawan".
* **Pinjam dari warga** (warga kaya: PEDAGANG/BANKIR/PEJABAT, kemakmuran ≥ 60, kesan ≥ 20): ≤ `min(kekayaan, 50 × (b+1))` NB,
  bunga 5 % per 7 hari, jatuh tempo 7 hari; mangkir → kesan −20, gosip, di ibu kota gugatan Yamen (`Hukum` denda = utang
  + 20 %). Uang diambil dari Kas Kota tempat itu (bukan dicetak); bila Kas kurang, warga menolak.
* **Pinjami warga** (permintaan `pinjam_uang`): mangkir menurut watak (jujur 2 %, ramah 5 %, licik 30 %, serakah 20 %);
  bunga dibayar dari Kas Kota; batas bunga total yang diterima pemain `10 × (b+1)` NB per minggu (keran 42.6.0). Utang
  diwariskan (42.4.6).

---------------------------------------------------------------------------------------------------------------------

### 42.5A Mesin Percakapan Lanjut (modul 42K; tanpa LLM)

Permintaan susulan pengguna (2026-10-24, mengikat): interaksi NPC-NPC dan NPC-pemain harus terasa **alami, tidak kaku**,
tetapi **tanpa LLM**: "algoritma atau program saja, tapi canggih". 42.5A adalah mesin percakapan **deterministik** sepenuhnya
di Java: model kepribadian dan emosi, gudang pengetahuan dengan kurva lupa, pemilihan topik berbasis utilitas, mesin keadaan
percakapan, dan tata bahasa bertipe dengan register, dialek, tik bicara dan memori anti-ulang. Tidak ada jaringan, tidak ada
model bahasa, tidak ada teks yang dibuat di luar data yang ditulis pengarang; keanekaragaman datang dari kombinasi.

#### 42.5A.0 Prinsip

1. **Deterministik**: setiap keputusan memakai `SplittableRandom(Cakap.benih(benihDunia, wargaId, pemainIdx|lawanId, hari,
   nomorGiliran, "cakap"))`. Keadaan dunia yang sama → ujaran yang sama (uji emas 42.5A.12). Tidak ada `Math.random`, tidak ada
   waktu dinding.
2. **Makna dulu, kata belakangan**: server memilih **apa** yang dikatakan (niat, topik, fakta, sikap, register) dan menyusun
   **Ujaran** = pohon `Component.translatable` dengan argumen bertipe. Teks dirangkai klien dari kunci lang (Indonesia utama,
   Inggris cermin, kunci identik), jadi satu derivasi tampil benar di kedua bahasa.
3. **Efek terpisah dari teks**: percakapan yang tidak dilihat pemain hanya menjalankan efeknya (fakta berpindah, hubungan,
   emosi) tanpa merangkai kalimat (murah). Teks hanya dirangkai saat ada pendengar pemain.
4. **Data-driven**: kepribadian, emosi, predikat fakta, topik, niat, nada, tata bahasa, dialek, tik dan naskah obrolan NPC-NPC
   ada di `data/kultivator/tempat/percakapan/**` (dimuat ulang `/reload`, divalidasi `tools/cek_cakap.py`).
5. **Menyatukan, bukan menambah**: `Kalimat` 38, templat 42.5.2, obrolan 4 baris `NpcLife` dan rumor per tempat `KabarTempat`
   semua lewat 42K saat `kotaBicara.mesinLanjut` aktif (42.5A.11).

| Ilham | Isi yang dipakai | Sumber |
|---|---|---|
| Model kepribadian enam faktor | Kejujuran-Kerendahan hati, Emosionalitas, Ekstraversi, Keramahan, Ketekunan, Keterbukaan | model HEXACO (Ashton dan Lee, 2004) |
| Ruang emosi tiga sumbu | Pleasure-Arousal-Dominance dan pemetaan temperamen dari kepribadian | Mehrabian dan Russell (1974); Mehrabian (1996) |
| Penilaian peristiwa | emosi lahir dari penilaian peristiwa terhadap tujuan, standar dan selera | model OCC (Ortony, Clore, Collins, *The Cognitive Structure of Emotions*, 1988) |
| Kurva lupa | ingatan meluruh eksponensial dan menguat saat diulang | Ebbinghaus (1885) |
| Tata bahasa generatif | aturan bersimbol yang diperluas rekursif dengan slot | Tracery (Kate Compton, 2015); tata bahasa bebas konteks |
| Giliran bicara | pembuka, pasangan berdekatan, giliran, potongan, penutup | analisis percakapan Sacks, Schegloff, Jefferson (1974) |
| Relevansi | bicara seperlunya, relevan, jujur | prinsip kerja sama Grice (1975) |
| AI utilitas | skor kurva per pertimbangan, pilih berbobot | Dave Mark, *Behavioral Mathematics for Game AI* (2009) |
| Fisika sosial | aksi sosial antar-tokoh dari keadaan hubungan dan pengetahuan | *Prom Week* / Comme il Faut (McCoy dkk., 2011); *Façade* (Mateas dan Stern, 2005); opini dan rumor koloni gaya *Dwarf Fortress* / *RimWorld* |
| Sapaan kultivator | "Rekan Daois" (道友), "Senior" (前辈), "junior ini" (晚辈), merendah diri | konvensi novel kultivasi (*A Record of a Mortal's Journey*, *Renegade Immortal*) |

#### 42.5A.1 Arsitektur

```
 Peristiwa dunia/warga/pemain ──▶ Penilaian (OCC) ──▶ Emosi PAD (meluruh)        Kepribadian HEXACO (tetap)
          │                                              │                              │
          └──▶ Pengetahuan (fakta, sumber, kebenaran, kekuatan; kurva lupa) ◀── dengar/lihat/obrolan
                                   │
   konteks (jam, tempat, cuaca, acara, lawan bicara, riwayat percakapan, janji)
                                   ▼
                PerencanaTopik (utilitas, softmax ber-benih) ──▶ KeadaanPercakapan (FSM giliran)
                                                                  │  niat (speech act) + sikap
                                                                  ▼
                        TataBahasa (bingkai + slot bertipe + fitur: kelamin, jamak, register, dialek, tik)
                                                                  ▼
             Ujaran (Component bersarang) + gestur (klip 38/40/42) + suara seru/VOICEVOX ──▶ layar dialog / gelembung
   Pemain: pilih topik + niat + NADA ──▶ PenilaiNada (cek) ──▶ Akibat (kesan, emosi, harga, quest, gosip, hukum)
```

#### 42.5A.2 Kepribadian (6 sifat, `Kepribadian`)

Enam byte −100..100 per warga, dibangkitkan sekali dari watak (`Tokoh.WATAK`) + kebiasaan + acak ber-benih (simpangan 20),
lalu tetap seumur hidup (anak mewarisi rata-rata orang tua ± 25). Tabel rata-rata watak (`percakapan/kepribadian.json`):

| Watak | H Jujur-rendah hati | E Emosional | X Ekstraversi | A Ramah | C Tekun | O Terbuka |
|---|---|---|---|---|---|---|
| ramah | +20 | +10 | +40 | +60 | 0 | +10 |
| curiga | +10 | +30 | −20 | −30 | +20 | −30 |
| serakah | −60 | 0 | +10 | −20 | +20 | −10 |
| bijak | +30 | −30 | 0 | +20 | +30 | +50 |
| penakut | +10 | +60 | −40 | +10 | 0 | −10 |
| sombong | −50 | −10 | +40 | −30 | 0 | 0 |
| jujur | +60 | 0 | 0 | +20 | +30 | 0 |
| licik | −50 | 0 | +20 | −20 | 0 | +30 |

Geser kebiasaan: pemabuk C −20 X +15; kutu buku O +25 X −15; penggosip sumur X +25 H −10; pemarah A −30 E +20; dermawan A +25 H
+15; pengintip O +15 H −15; pemuja kultivator O +20. Sifat dipakai di seluruh 42.5A:

| Sifat | Pengaruh (angka di data) |
|---|---|
| X | banyak bicara (giliran 1-3 ujaran), peluang memulai obrolan NPC-NPC, memotong, bercanda, kesabaran +X/4 |
| A | menghindari topik konflik, memaafkan (kesan negatif meluruh ×(1 + A/100)), menerima nada sopan/akrab |
| H | jujur (H < −30: peluang berbohong/membumbui fakta 15-40 %), menolak suap (H > 40), merendah diri |
| E | amplitudo emosi ×(1 + E/100), bicara tentang kekhawatiran, takut pada ancaman |
| C | register lebih formal, menepati janji warga ke pemain, tidak bergosip saat bekerja |
| O | rasa ingin tahu (bertanya tentang perjalanan/sekte pemain), suhu pemilihan topik lebih tinggi, menerima candaan |

#### 42.5A.3 Emosi PAD dan penilaian peristiwa (`Emosi`, `Penilaian`)

* **Temperamen dasar** dari sifat (S = −E stabilitas): `P0 = 0,21X + 0,59A + 0,19S`, `A0 = 0,15O + 0,30A − 0,57S`, `D0 = 0,25O +
  0,17C + 0,60X − 0,32A` (dibagi 100, dijepit ±0,6), lalu P0 digeser suasana jangka panjang 42.4.11 (`+ suasana / 250`).
* **Keadaan** `(P, A, D)` −1..1 (tiga byte + hari-tick terakhir), disimpan **jarang** (hanya warga yang mendapat dorongan dalam
  2 hari dunia). Nilai sekarang dihitung malas: `v(t) = v0 + (v − v0) × 2^(−Δt / t½)`, `t½ = 2 jam dunia × (1 + C/200 − E/300)`.
* **Penilaian** (`percakapan/emosi.json`): peristiwa → dorongan ΔPAD × intensitas × `(1 + E/100)`; sasaran (pemain/warga) dicatat
  untuk emosi berarah (marah *kepada* X):

| Peristiwa | ΔP | ΔA | ΔD | Catatan |
|---|---|---|---|---|
| dipuji / dibantu | +0,30 | +0,10 | +0,10 | syukur kepada pelaku |
| diberi hadiah disukai / dibenci | +0,25 / −0,15 | +0,10 | 0 | — |
| dihina | −0,40 | +0,35 | +0,20 bila D ≥ 0, −0,20 bila D < 0 | marah atau malu |
| diancam oleh yang lebih kuat | −0,35 | +0,50 | −0,45 | takut |
| dicurangi / dicuri | −0,45 | +0,40 | +0,10 | marah kepada pelaku |
| dirayu (diterima / ditolak) | +0,25 / −0,10 | +0,30 | +0,10 / −0,10 | — |
| candaan berhasil / gagal | +0,20 / −0,15 | +0,15 | 0 | — |
| kabar duka keluarga / kenalan | −0,60 / −0,20 | −0,20 | −0,10 | — |
| festival / pernikahan di tempat | +0,20 | +0,20 | 0 | — |
| serangan monster dekat | −0,50 | +0,60 | −0,30 | — |
| menang taruhan / kalah | +0,30 / −0,30 | +0,30 | ±0,10 | — |
| mabuk (kebiasaan) | +0,15 | +0,10 | +0,25 | C efektif −30 |
| janji pemain ditepati / diingkari | +0,25 / −0,35 | 0 / +0,25 | 0 | kesan ±, fakta `PEMAIN_INGKAR` |

* **Label emosi** dari oktan PAD (Mehrabian): +P+A+D *gembira*, +P−A+D *tenang puas*, +P+A−D *terpesona/kagum*, +P−A−D *lega*,
  −P+A+D *marah*, −P+A−D *takut/cemas*, −P−A+D *jemu/ketus*, −P−A−D *sedih*. Label memilih kode wajah (42.4.11), gestur, nada
  balasan dan kata sifat intensitas ("agak", "sangat", "bukan main").

#### 42.5A.4 Pengetahuan dan kurva lupa (`Pengetahuan`, `Fakta`, `Lupa`; menyatukan gosip 42.5.4)

* **Fakta** (≤ `kotaBicara.faktaMaks` 24 per warga + catatan percakapan per pemain di 42.5A.6): `(predikat, subjek, objek, nilai,
  kebenaran BENAR/BUMBU/PALSU, yakin 0-100, sumber SAKSI|DIRI|DENGAR(wargaId)|PAPAN|PEMAIN, hariKejadian, hariTahu, kekuatan S
  (jam dunia), kaliDiceritakan, valensi −3..3, gosipId?)` ≈ 18 B. Gosip 42.5.4 = fakta yang membawa `gosipId` ke tabel gosip
  bersama per tempat (≤ 64): satu penyimpanan, `GosipWarga` pindah ke 42K.
* **Predikat** (`percakapan/predikat.json`, 44 jenis): pemain (`MENOLONG`, `MENCURI`, `MEMBELI`, `MENAWAR_KERAS`, `BERJANJI(apa,
  batas)`, `INGKAR`, `RANAH`, `SEKTE`, `PEJABAT`, `MENANG_DUEL`, `MENGANCAM`, `MENYUAP`, `MERAYU`, `BERCANDA`, `DERMAWAN`), warga
  (`NIKAH`, `BERTUNANGAN`, `HAMIL`, `LAHIR`, `SAKIT`, `WAFAT`, `PINDAH`, `BERUTANG`, `SUKA`, `BENCI`, `BERKELAHI`, `MABUK`, `KALAH_JUDI`,
  `DIREKRUT`, `USAHA_LARIS`, `USAHA_BANGKRUT`), kota (`HARGA_NAIK/TURUN`, `ACARA`, `BAHAYA`, `KAFILAH_TIBA`, `KAVLING_JADI`,
  `PELINDUNG_BARU`, `PASAR_HANTU_DI`), dunia (`TEMPAT_ADA`, `HARTA_LAHIR`, `SEKTE_PERANG` dari `KabarTempat`/`DuniaEvents`), opini
  (`OPINI(subjek, nilai)`).
* **Kurva lupa**: peluang ingat `R = e^(−Δt / S)`, Δt = jam dunia sejak terakhir diingat. `S` awal 24 jam (dengar), ×1,5 saksi
  sendiri, ×2 bila |valensi| ≥ 2 (ingatan kilat), ×1,6 setiap kali diceritakan ulang (pengulangan), ×(1 + C/200). Hari Warga
  membuang fakta R < 0,1 (kecuali yang melibatkan keluarga atau kenangan kuat 42.4.10).
* **Keyakinan**: `yakin = kepercayaan pada sumber × (1 − 0,15 × lompatan)`; kepercayaan = `50 + hubungan/2 + H_pencerita/4` (saksi =
  100). Fakta bertentangan: yang lebih yakin menang; warga yang menyaksikan sendiri **mengoreksi** ("Bukan lima copet, hanya satu,
  aku lihat sendiri") → fakta koreksi menyebar juga.
* **Kemenonjolan** (salience) untuk topik: `sal = R × (1 + |valensi|) × kebaruan × relevansi × diri`; `kebaruan = 1 / (1 +
  umurHari / 3)`, `relevansi` = 2 bila subjek dikenal pendengar (pemain/keluarga/sahabat), 1,5 tetangga, 1 lainnya; `diri` = 2 bila
  menyangkut pembicara.

#### 42.5A.5 Pemilihan topik berbasis utilitas (`PerencanaTopik`, data `percakapan/topik.json`)

Kandidat (≤ 24 per giliran) dibangun dari: fakta menonjol (8 teratas), kebutuhan warga (permintaan 42.5.6, utang, sakit), konteks
(cuaca, jam, acara, tempat, musim), riwayat dengan lawan bicara (janji jatuh tempo, pertanyaan belum terjawab, topik terakhir),
minat (kebiasaan, pekerjaan), rasa ingin tahu tentang lawan (O tinggi: perjalanan, sekte, ranah pemain), dan pertanyaan arah.

`U = 0,35·sal + 0,20·relLawan + 0,15·cocokEmosi + 0,10·konteks + 0,10·minat + 0,10·kebutuhan − 0,30·sudahDibahas − tabu`

| Pertimbangan | Rumus / kurva | Contoh |
|---|---|---|
| `relLawan` | hubungan atau kesan dinormalkan 0-1; rahasia (fakta valensi negatif tentang orang ketiga) butuh ≥ 0,7 | gosip pedas hanya ke sahabat |
| `cocokEmosi` | kedekatan valensi topik dengan P sekarang (sedih → keluhan, gembira → kabar baik) | — |
| `konteks` | 1 bila syarat jam/tempat/acara topik cocok, 0,5 sebagian | "panas sekali" siang musim panas |
| `sudahDibahas` | `0,9^jamSejak` dengan lawan yang sama (≤ 24 jam), 0,5 bila dibahas siapa pun ke pemain ini 1 jam terakhir | — |
| `tabu` | 1 (dibuang) untuk: duka di pesta pernikahan, gosip saat C > 40 sedang bekerja, rahasia ke orang asing, menjelekkan pejabat pemain di depannya bila takut | — |
| pengali sifat | X: +0,1 pada topik sosial; A: −0,2 topik konflik; E: +0,2 kekhawatiran; H: −0,3 gosip; O: +0,2 pertanyaan | — |

Pilih dengan softmax suhu `τ = 0,25 + O/400` (orang terbuka lebih variatif) memakai RNG ber-benih. **Rantai lanjutan**: tiap topik
punya tautan (`"lanjut": ["kafilah", "begal"]`) yang mendapat +0,25 pada giliran berikut, sehingga obrolan mengalir: harga naik →
kafilah tertahan → begal di jalan barat → "katanya sekte Tuan menjaga jalan itu?".

#### 42.5A.6 Mesin keadaan percakapan, giliran, potongan, kilas balik (`KeadaanPercakapan`, `Giliran`, `Kilasan`)

```
PEMBUKA ─▶ BASA_BASI ─▶ TOPIK ⇄ LANJUTAN ─▶ PERMINTAAN/TAWARAN ─▶ REAKSI ─▶ (TOPIK | KILAS_BALIK | PENUTUP)
   │            ▲            │  ▲                                              │
   └─ KILAS_BALIK (janji jatuh tempo, utang, kejadian terakhir) ──────────────┘
 setiap keadaan: POTONG (bahaya, pelanggan, anak menangis, lawan ber-X tinggi A rendah) → kembali atau PENUTUP
```

* **Kesabaran** 0-100 per sesi: awal `40 + X/4 + kesan/4 − sibuk(jadwal kerja/acara) × 30`, −8 per giliran, −20 topik yang tidak
  disukai, +10 topik minat/candaan berhasil; ≤ 0 → PENUTUP sopan ("Maaf, adonanku menunggu").
* **Giliran**: NPC 1-3 ujaran per giliran (X), **sambung rasa** pendengar (`hmm`, `oh ya?`, angguk, 30 % per ujaran panjang) di
  NPC-NPC; pemain memilih 3-6 opsi (42.5A.7).
* **Potongan**: peristiwa (bahaya 38.N.4, pelanggan toko datang, ronda memukul gong, pasangan memanggil) memotong dengan ujaran
  `POTONG.<sebab>` lalu kembali ("Tadi sampai mana? Oh ya, soal kafilah…") bila kesabaran > 30.
* **Riwayat** per (warga, pemain): 6 percakapan terakhir `(hari, topik[3], niat terakhir, nada pemain, hasil)` + **janji** (≤ 3:
  siapa, apa (predikat), batas hari, status). **Kilas balik** diprioritaskan di PEMBUKA bila: janji jatuh tempo/lewat ("Kau
  berjanji minggu lalu membawakan obat untuk ibuku…"), utang, peristiwa kuat sejak terakhir bertemu ("Sejak kau menangkap copet
  itu, pasar jadi tenang"), atau lama tak jumpa ("Lama tidak kelihatan, terakhir sebelum musim dingin"). Ungkapan waktu dari slot
  `waktu_lalu` (kemarin, kemarin lusa, tiga hari lalu, minggu lalu, bulan lalu, musim lalu, tahun lalu dalam Tahun Fana).
* Janji pemain lahir dari pilihan `[Aku akan …]` di REAKSI/PERMINTAAN; menepati/ingkar mengubah emosi, kesan dan fakta yang
  menyebar.

#### 42.5A.7 Pilihan pemain dan nada (`Nada`, `PenilaiNada`, data `percakapan/nada.json`)

Setiap opsi = **niat** (tanya topik, beri kabar, minta, tawar, janji, tolak, puji, hina, pamit) × **nada**. Layar dialog
menampilkan opsi niat; nada dipilih dengan tablet kecil di kanan (ikon) atau tombol 1-6 (bawaan Sopan). Peluang berhasil
`p = logistik(b0 + Σ wᵢ·xᵢ)`; angka tidak ditampilkan (bawaan), hanya isyarat raut ("Dia tampak tidak sabar"); `tampilPeluang`
menampilkannya.

| Nada | Syarat | Faktor (xᵢ) | Berhasil | Gagal |
|---|---|---|---|---|
| **Sopan** | — | A, C, kesan, Nama Baik | kesan +2, info terbuka, harga −0 | (jarang) kesabaran −5 |
| **Akrab** | kesan ≥ 20 | kesan, X, A, umur sebaya | kesan +4, rahasia terbuka, harga −3 % | kesan −3 "sok akrab" |
| **Merayu** | lawan dewasa, tidak menikah atau H rendah | O, X, P sekarang, kesan, ranah pemain | kesan +6, emosi +, harga −5 %, fakta `PEMAIN_MERAYU` (gosip) | kesan −8, pasangan/keluarga marah, gosip |
| **Mengancam** | — | selisih ranah, Infami, E lawan, D lawan (−), saksi | info/harga −20 %/barang diserahkan | kesan −20, emosi takut/marah; di ibu kota disaksikan = kejahatan (`Hukum.kejahatan` 3 poin); Takut 38.R + |
| **Menyuap** | NB di kantong (2-50 × (b+1)) | H lawan (−), serakah, kemiskinan rumah tangga | info rahasia, aturan longgar (penjaga, saksi perkara), harga | H > 40: ditolak + gosip "mencoba menyuap", pejabat: Infami |
| **Bercanda** | — | X, O, P sekarang, emosi tidak sedih/takut, kesan | kesan +3, emosi +, kesabaran +10, obrolan berlanjut | kesan −2, "tidak lucu"; saat duka: −10 |

* Pengulangan trik yang sama ke warga yang sama dalam 1 hari: faktor ×0,7 per ulangan.
* Akibat mengalir ke sistem lain: kesan/ingatan (42.4.10), emosi, harga pribadi pedagang (`HargaKota` × faktor pribadi ±5-20 %),
  permintaan dan quest (opsi terbuka bila berhasil), gosip (fakta tentang pemain menyebar menurut predikat), `Hukum` (ancaman di
  ibu kota), Infami (`TemaApi.tambahInfami`), perkara 42.6.6 (suap saksi).

#### 42.5A.8 Tata bahasa bertipe (`TataBahasa`, `Realisasi`, `Dialek`, `Tik`, `AntiUlang`)

**Format aturan** (bahasa-netral, `data/kultivator/tempat/percakapan/tata/<kelompok>.json`):

```json
{
  "aturan": "kabar.harga_naik",
  "niat": "BERI_KABAR",
  "topik": "harga",
  "syarat": {"fakta": "HARGA_NAIK", "label_emosi": ["jemu", "sedih", "cemas", "netral"]},
  "bingkai": [
    {"kunci": "harga_naik.0", "slot": ["$barang", "$waktu_lalu", "$keluh"], "bobot": 3},
    {"kunci": "harga_naik.1", "slot": ["$barang", "$sebab"], "bobot": 2, "register": ["biasa", "kasar"]},
    {"kunci": "harga_naik.2", "slot": ["$sebutan", "$barang"], "bobot": 1, "register": ["sopan", "kuno"]}
  ],
  "slot": {
    "$barang": {"tipe": "barang", "dari": "fakta.objek", "fitur": ["jamak"]},
    "$waktu_lalu": {"tipe": "waktu_lalu", "dari": "fakta.hariKejadian"},
    "$sebab": {"aturan": "sebab.harga", "opsional": 0.5},
    "$keluh": {"aturan": "seru.keluh", "opsional": 0.6},
    "$sebutan": {"tipe": "sebutan_pendengar"}
  },
  "lanjut": ["kafilah", "begal"], "gestur": "geleng", "seru": "keluh"
}
```

Terminal = kunci lang `kultivator.cakap.<kunci>[.<fitur>…]` di fragmen `kota_hidup_cakap.{id_id,en_us}.json` (kunci identik):
`"kultivator.cakap.harga_naik.0": "%1$s naik lagi sejak %2$s. %3$s"` / `"%1$s went up again %2$s. %3$s"`. Urutan kata ditangani
string tiap bahasa (argumen posisi), argumen sendiri adalah `Component` hasil perluasan rekursif (kedalaman ≤ 4).

* **Tipe slot** (`TipeSlot`, Java): `sebutan_pendengar` (dari ranah, kelamin, kesan, register: Tuan/Nona/Kakak/Adik/Senior/Rekan
  Daois/Tuan Abadi/Leluhur), `nama_warga` (dengan fitur kelamin), `ganti` (dia/ia, he/she), `barang` (nama item + jamak),
  `tempat`, `arah`, `jarak`, `angka` (kata bilangan ≤ 20, lalu angka), `harga` (format NB 38.U), `waktu_lalu`, `waktu_depan`,
  `cuaca`, `musim`, `kata_emosi` (intensitas dari PAD), `seru` (kata seru), `tik`, `pekerjaan`, `hubungan` (istriku/sahabatku…).
* **Fitur dan kesesuaian**: `kelamin` (l/p), `jamak` (s/j), `register` (sopan/biasa/kasar/kuno), `dialek`; pencarian kunci berurutan
  `kunci.fitur1.fitur2` → `kunci.fitur1` → `kunci` (bahasa yang tidak membedakan cukup mengisi kunci dasar; generator mengisi
  cermin otomatis). Contoh Inggris: `kultivator.cakap.ganti.l` "he", `.p` "she"; Indonesia keduanya "dia".
* **Register**: dari kelompok peran, C, hubungan dan selisih ranah: *sopan* (PEDAGANG ke pembeli, warga ke pejabat), *biasa*
  (kenalan), *kasar* (MUSUH, pemabuk, Kota Iblis, A < −40), *kuno-kultivator* (KULTIVATOR/RAHIB/tetua sekte, atau warga ke pemain ≥ 3
  ranah di atas: "Junior ini tidak berani…", "Senior, hamba hanya penjual teh").
* **Dialek** (`percakapan/dialek.json`, leksikon pengganti `kultivator.cakap.lex.<kata>.<dialek>`): `ibukota` (baku halus),
  `pelabuhan` (logat pelaut: "Bung", "ombak"), `oasis` ("Saudara", sumpah demi mata air), `air` (lembut: "Kakak", akhiran
  "ya…"), `gunung` (biara: "Dermawan" untuk pengunjung), `iblis` (kasar, ancaman sopan), `kampung` (lugas, "Nak"). Satu kata kunci
  leksikon per 40-80 kata yang sering dipakai.
* **Tik bicara** (`percakapan/tik.json`, 36 tik): satu atau dua per warga dari benih + sifat: "hehe", "astaga", "aduh biyung",
  "kata orang tua dulu", "untung-rugi" (pedagang), "uhuk" (tetua), "hik" (mabuk, malam), gagap suku pertama (E > 60: "S-saya…"),
  "begitulah dunia", "demi langit". Disisipkan dengan peluang `kotaBicara.tikPeluang` 0,25 (× 1,5 saat A tinggi emosi), tidak dua
  kali berturut.
* **Anti-ulang**: penalti bobot bingkai/terminal `× 0,1` bila dipakai pembicara yang sama ke pendengar yang sama ≤ 24 jam dunia,
  `× 0,5` bila pendengar mendengarnya dari siapa pun ≤ 1 jam; memori per pemain 256 kunci terakhir (cincin) + per warga 64.
  Sasaran keragaman: niat umum (sapa, kabar, keluhan) ≥ 10⁴ realisasi berbeda; `uji kota_hidup cakap kombinasi` mencetaknya.
* **Isi minimum** (pengganti target 42.5.2): 62 niat, ≥ 900 bingkai, ≥ 2.400 terminal leksikon (termasuk 7 dialek × 60 kata), 36
  tik; ditulis sebagai sumber pengarang `tools/kota_cakap_sumber/*.txt` (satu baris per varian, tag fitur) dan dikompilasi
  `tools/gen_kota_hidup_cakap.py` menjadi JSON tata + fragmen lang id/en.
* **Jepang**: ujaran bersuara hanya untuk niat yang dipetakan ke baris VOICEVOX 42.12.7 atau ke **kata seru** pendek (diputar di
  awal gelembung, teks tetap Indonesia/Inggris):

| Kunci | Suara | Jepang | Arti | Emosi |
|---|---|---|---|---|
| `kota42.seru.ya_l` / `_p` | m2 / f1 | はい。 | Ya. | normal |
| `kota42.seru.eh_l` / `_p` | m3 / f2 | ええっ？ | Hah? | fear |
| `kota42.seru.hmm_l` / `_p` | m2 / f2 | ふむ… / うーん… | Hmm... | calm |
| `kota42.seru.oh_l` / `_p` | m1 / f1 | へえ！ | Oh ya! | joy |
| `kota42.seru.tawa_l` / `_p` | m1 / f1 | あははっ！ | Hahaha! | joy |
| `kota42.seru.cih` | m3 | ちっ。 | Cih. | angry |
| `kota42.seru.aduh_l` / `_p` | m2 / f2 | やれやれ… | Aduh... | sad |
| `kota42.seru.terima_kasih_l` / `_p` | m2 / f1 | どうも！ | Terima kasih! | joy |
| `kota42.seru.maaf_l` / `_p` | m3 / f3 | すみません… | Maaf... | sad |
| `kota42.seru.wah_anak` | f1 | わあっ！ | Wah! | joy |
| `kota42.seru.tetua_hmm` | elder | ほっほっ。 | Ho ho. | calm |
| `kota42.seru.tetua_marah` | elder | 馬鹿者！ | Dasar bodoh! | angry |
| `kota42.seru.mabuk` | m1 | ひっく。 | Hik. | normal |
| `kota42.seru.bisik_p` | f2 | しーっ。 | Sst. | calm |
| `kota42.seru.takut_p` | f3 | ひっ…！ | Hii...! | fear |
| `kota42.seru.setuju_l` | m1 | そうだそうだ！ | Betul, betul! | joy |

(24 klip; kunci berakhiran `_l/_p` dipilih menurut kelamin pembicara.)

#### 42.5A.9 Obrolan NPC-NPC (`ObrolanWarga`, data `percakapan/obrolan/*.json`)

Dipicu oleh siklus obrolan 38 (`tempat.hidup.obrolanDetik` 40; pasangan di titik `obrol`, berpapasan, kerja satu lot, sumur, kedai):
peluang mulai `0,3 + X_A/300 + hubungan/400`, ≤ 1 per pasangan per jam dunia.

| Jenis | Syarat (hubungan, pengetahuan, emosi) | Alur giliran (naskah keadaan) | Efek nyata |
|---|---|---|---|
| `GOSIP` | ≥ kenal, salah satu punya fakta menonjol yang tidak diketahui lawan | sapa → umpan ("Sudah dengar?") → kabar (bisa dibumbui H) → reaksi (kaget/ragu/koreksi) → komentar | fakta pindah dengan keyakinan (42.5A.4), kesan pada subjek ±3 |
| `DEBAT` | `MUSUH`/`SAINGAN` atau `OPINI` berlawanan (harga, pajak, sekte pelindung) | tuduhan → bantahan → eskalasi (A rendah) → (potong pihak ketiga / pergi / perkelahian 42.7.4 bila A < −40 dan D tinggi) | hubungan −2..−8, emosi marah, peluang perkelahian |
| `MERAYU` | `KEKASIH` atau ketertarikan (kecocokan + P tinggi), lajang | puji → malu-malu → canda → janji bertemu | hubungan +3..+6, KEKASIH lebih cepat (42.4.6) |
| `DAGANG` | pedagang dan pembeli | tawar 1-3 putaran (pelit: 3) → sepakat/batal | stok kota/penjualan antar-NPC 38.N.4 tercatat, kabar harga |
| `AJAR` | `GURU_MURID`, tetua–anak, guru sekolah | nasihat/peragaan → pertanyaan → pujian/teguran | ahli murid +, fakta (lore) pindah ke anak |
| `CURHAT` | `SAHABAT`/keluarga, P < −0,3 | keluhan → hiburan → (permintaan tolong muncul bila masalah nyata) | P naik +0,2, permintaan 42.5.6 bisa lahir |
| `CANDA` | X keduanya > 20, P ≥ 0 | olok ringan → tawa | hubungan +2, P +0,1 |
| `SAPA` | lainnya | salam + satu basa-basi | — |

* **Terlihat**: bila pemain ≤ `obrolanTampilJarak` 12 blok, setiap ujaran dirangkai dan dikirim sebagai gelembung (payload
  `kota_ujaran`), dengan gestur niat (`bicara_tangan`, `bisik`, `berdebat`, `tertawa`, `tunjuk`, `geleng`, `angguk`), tatapan saling
  (`HidupApi.tatap`), gumam suara 73, jeda giliran 40-80 tick menurut panjang. ≤ `obrolanMaksTampil` 4 obrolan tampil per tempat.
* **Menguping**: pemain yang mendengar sampai akhir mendapat fakta itu sebagai **catatan** (Buku Kota tab Kabar; jurnal 38) — mis.
  lokasi Pasar Hantu malam ini, siapa berutang pada rentenir, bahwa toko saingan menimbun beras.
* **Tidak terlihat**: hanya efek (tanpa realisasi teks), ≤ 0,05 ms per obrolan.
* Pemain bisa **menyela** obrolan NPC-NPC (klik salah satu): keduanya menoleh, sesi pemain dimulai dengan topik obrolan itu.

#### 42.5A.10 Contoh (keluaran emas, `golden/contoh_bibi_wang.json`)

```
Keadaan: Bibi Wang (penakut: H+10 E+60 X−40… + gosip_sumur X+25), P −0,1 A +0,2 D −0,3 (cemas), dialek ibukota,
fakta: COPET_TERTANGKAP(pemain, pasar, h−2, BENAR, saksi), BERJANJI(pemain, "obat untuk Nenek Wang", batas h−1, belum).
[PEMBUKA→KILAS_BALIK] "Oh, Tuan Han... kemarin Tuan bilang mau bawakan obat untuk ibu mertuaku, ya? Aduh, bukan menagih,
                       hanya... batuknya makin parah."                        (gestur: tangan di dada; seru: aduh_p)
  Pemain [Sopan] Minta maaf, besok kubawa.  →  janji diperpanjang 1 hari, kesan −2, P −0,05
[LANJUTAN] "I-iya, tidak apa. Oh ya! Tuan yang menangkap copet di pasar kemarin lusa, kan? Satu pasar membicarakannya."
  Pemain [Bercanda] "Copetnya lebih lambat dari bebek Bibi."  →  p = 0,62 (X−15, P−0,1, kesan 34) berhasil: tawa kecil, kesan +3
[TOPIK] (utilitas: harga 0,41, kafilah 0,33, Zhang-Li 0,29 → softmax τ 0,25) "Beras naik lagi sejak minggu lalu. Kata Bibi
         Zhang, kafilah dari selatan tertahan begal."
[PENUTUP] kesabaran 6 → "Aduh, cucianku! Hati-hati di jalan, Tuan."
```

Uji emas menyimpan **kunci dan argumen** (bahasa-netral) plus realisasi id/en yang diharapkan; perubahan data yang menggeser
keluaran harus memperbarui berkas emas dengan sengaja (`uji kota_hidup cakap golden tulis`).

#### 42.5A.11 Hubungan dengan sistem dialog yang ada

| Sistem lama | Setelah 42K (`mesinLanjut` true) | Bila `mesinLanjut` false |
|---|---|---|
| `Kalimat.sapa` 38 (grup × situasi) | `Kalimat.sapa` meneruskan ke `Cakap.sapa(player, npc)`; kunci `kultivator.cerita.kalimat.*` dipakai sebagai leksikon cadangan niat `SAPA` | perilaku 38 |
| templat 42.5.2 (`tempat/bicara/*.json`) | **tidak dibuat**; sumbunya (peran, jam, cuaca, acara, suasana, kesan, kenangan) menjadi `syarat` aturan tata | templat 42.5.2 minimal (sapa saja) |
| obrolan 4 baris `NpcLife`/38.N.4 butir 8 (`DATA_LINE`) | diganti `ObrolanWarga` (gelembung `kota_ujaran`); `ObrolanSuara` 73 tetap memutar gumam | 4 baris lama |
| rumor per tempat `KabarTempat` | `pemicu` juga melahirkan fakta di warga saksi/penyebar; kedai teh membaca fakta paling yakin di tempat itu | 38 |
| dialog data 38 (tokoh, quest) | tetap ditulis tangan dan didahulukan; simpul boleh memuat `"cakap": "<niat>"` untuk sisipan alami (sapa, reaksi terima kasih, kilas balik janji) | teks tetap |
| gosip 42.5.4 | penyimpanan = fakta 42K; aturan sebar 42.5.4 tetap (angka sama) | — |

#### 42.5A.12 Data, kinerja, determinisme dan uji

* **Data** (SavedData `kultivator:kota_cakap`, 42K): per warga `K` (6 byte sifat), `E` (PAD 3 byte + tick, jarang), `F` (fakta ≤ 24 ×
  18 B), `R` riwayat per pemain (≤ 6 × 12 B + janji ≤ 3 × 10 B, maks 6 pemain), `U` anti-ulang (64 short); per pemain cincin 256
  short. ±0,6 KB per warga aktif → 3.000 warga ≈ 1,8 MB mentah / ±400 KB gzip. Data tata/leksikon dimuat dari datapack (≈ 600 KB
  JSON + lang).
* **Kinerja**: giliran dialog pemain < 0,2 ms (≤ 24 kandidat, perluasan ≤ 4 tingkat); obrolan NPC-NPC tanpa teks < 0,05 ms,
  dengan teks < 0,3 ms; ≤ 4 obrolan tampil per tempat per 40 tick; Hari Warga menambah peluruhan fakta O(fakta).
* **Determinisme**: benih 42.5A.0; urutan iterasi selalu terurut (id), tidak ada `HashMap` yang urutannya memengaruhi pilihan
  (`LinkedHashMap`/array terurut).
* **Validasi data** `tools/cek_cakap.py`: semua kunci ada di id dan en, jumlah `%n$s` sama dengan slot, tidak ada aturan yatim/tak
  terjangkau, kedalaman ≤ 4, laporan kombinasi per niat; dijalankan generator dan agen finish.
* **Uji emas** (`data/kultivator/tempat/percakapan/golden/*.json`, ≥ 40 berkas: tiap niat, register, dialek, nada, 8 jenis obrolan
  NPC-NPC, kilas balik, potongan): `uji kota_hidup cakap golden` membangun keadaan dari berkas, menjalankan mesin dengan benih
  tetap dan membandingkan kunci/argumen + teks id/en. Dijalankan di game oleh penguji (agen tidak menjalankan tes JUnit/Gradle).

---------------------------------------------------------------------------------------------------------------------

### 42.6 Ekonomi kota (C14-C20; modul 42C)

#### 42.6.0 Kurs, keran dan pembuangan

Satu kurs (42.0 butir 4). **Kas Kota** (`KasKota`, per situs) sekarang juga diisi **pajak simulasi** (42.6.6) dan menjadi
satu-satunya sumber uang kota: membayar gaji pejabat, bunga pinjaman warga, bahan kavling, lumbung, hadiah pertahanan dan
festival (38). Kas Kota tidak pernah negatif; pembayaran yang melebihi saldo ditunda atau ditolak.

| Keran (faucet) | Batas per pemain | Pembuangan (sink) | Besar |
|---|---|---|---|
| Penjualan toko pemain | `60 × (b+1)` NB/hari per toko (rumah toko × 1,5); harga ≤ 1,5 nilai | pajak toko | 5 % → Kas Kota |
| Bagi laba investasi | `25 × (b+1)` NB/minggu per usaha, `60 × (b+1)` total | rugi investasi, biaya tarik 2 % | — |
| Kontrak dagang | ikut **batas laba mingguan Dagang** (satu batas bersama dagang antar-kota) | ongkos kafilah 3 %, denda gagal 15 % | — |
| Gaji jabatan | `20 × (b+1) × pangkat` NB/minggu per tempat, ≤ 2 tempat pangkat ≥ 3 | suap ditolak / denda audit | — |
| Upeti perlindungan (42.8.2) | `30 × (b+1) × kelasKota` NB/minggu, ≤ 3 kota | biaya panji, kewajiban bela | — |
| Bunga pinjaman ke warga | `10 × (b+1)` NB/minggu | mangkir | — |
| Imbalan permintaan warga | 3/hari × `4 × (b+1)` | — | — |
| — | — | beli rumah (jual-kembali 50 %), sewa rumah 3 %/minggu, pajak properti 1 %/minggu, sewa lapak, sewa gudang, gaji pegawai, uang restu keluarga calon, angpao/uang duka, Guanpu (EV 0,90), adu jangkrik (EV 0,85), tiket teater, obat penjual keliling | — |

Uji `uji kota ekonomi simulasi <pita> <hari>` (42.14.3) harus jatuh di sasaran 38.M.1 ±25 % untuk pemain yang memakai
semua keran 42 bersama keran 38.

#### 42.6.1 Rumah: beli atau sewa (`RumahKota`)

* **Rumah yang tersedia**: (a) **Rumah Dijual** dari generator (lot/bangunan bertanda `dijual` 42.12.1, 2-6 per kota, 1-2 per
  kampung besar, 8 per ibu kota), (b) rumah warga yang **kosong** karena seluruh rumah tangga wafat/pindah (generasi 42.4),
  (c) bangunan kavling baru (42.9.4) yang dipesan sebelum jadi. Satu rumah = kotak titik `bangunan` (sidecar lot 38.B.2.5) +
  titik `pintu`.
* **Harga beli**: kecil (≤ 2 kasur) 300 NB, sedang (3-4) 900, besar (5+) 2.400, rumah toko 1.800, paviliun berhalaman 6.000 ×
  faktor tempat (kampung 0,5; pos 0,6; kota 1,0; kota tema 1,3; ibu kota 2,0; distrik istana/awan 3,0) × `HargaKota.faktor(
  BANGUNAN)` × kemakmuran (`0,8 + K / 250`). **Sewa** 3 % harga per 7 hari (min 10 NB), bayar di muka 1-4 minggu.
* **Syarat**: Nama Baik ≥ 10 (sewa) / ≥ 20 (beli), bukan Buronan; 1 rumah per tempat, `kotaEkonomi.rumahMaks` 4 total.
* **Akta**: item `kultivator:akta_rumah` (terikat pemain, komponen `kultivator:rumah` = situs + id rumah), hilang → cetak ulang
  di Kantor Tanah 5 NB. Kantor Tanah = petugas di Yamen/balai desa/kantor sekte 34 (tablet Jasa "Rumah").
* **Klaim**: `ProtectedZones.addClaimProvider(RumahKota::diklaim)` atas kotak rumah → `KlaimPemain.diklaim` benar → 38.R
  **tidak pernah** merekam kerusakan, menagih atau memulihkan di dalamnya; pemain bebas membongkar dan menaruh blok (bangunan
  biasa). Warga tidak memakai titik di dalamnya (`TempatHuni.addPenolakTitik`), tubuh tidak masuk (`TubuhPenduduk` menolak
  tujuan dalam klaim). Klaim bukan zona Mode Kehancuran (zona tempat 34/38 tetap melarang Kehancuran).
* **Akhir sewa / jual**: rumah **disegel** 14 hari (klaim tetap atas nama pemain, hanya pemain yang masuk, tanpa warga). Lewat
  14 hari: isi wadah dipindah ke **Barang Sitaan** 38.R (ambil gratis 14 hari di Yamen/mandor), lalu `Cetak.terima` untuk kotak
  itu (cetak biru menerima keadaan rumah seperti yang ditinggalkan pemain — furnitur pemain tidak pernah "diperbaiki" atau
  dibongkar), lalu rumah masuk daftar Rumah Dijual dan warga boleh menempatinya. Jual ke kota: 50 % harga beli.
* **Kasur**: kasur vanilla di rumah = titik bangkit biasa (tidur vanilla). **Titik teleport**: item `kultivator:kunci_rumah`
  (satu per rumah, terikat) → kanal 3 s (`Sibuk.why`), pindah ke titik `pintu` rumah; jeda `kotaEkonomi.kunciJedaMenit` 30;
  tidak dari dimensi lain atau alam rahasia; gratis (pemilik). Kursi `CaveAbode` (Jimat Pulang) tidak berubah.
* **Tetangga**: tetangga menyapa "Tetangga!", membawa kue saat festival (diberikan langsung saat pemain menyapa,
  bukan ditaruh di peti), mengundang ke pernikahan; pembantu rumah (warga menganggur, gaji 8/10/12/15 NB per minggu menurut
  jenis tempat) berdiri di rumah (tubuh dalam klaim), menyeduh teh (buff Teh Rumah: regen Qi +5 % 5 menit), memberi makan
  hewan peliharaan pemain.
* **Pajak properti** 1 % harga per 7 hari → Kas Kota (sink); 3 kali tak dibayar → disegel.

#### 42.6.2 Toko sendiri (`TokoPemain`)

* **Tempat**: rumah toko milik/sewaan (42.6.1) atau **Lapak Kota** sewaan: titik `lapak_sewa_<n>` baru di pasar tiap kota
  (2-8, generator 42W) dengan tenda entitas `tenda_lapak` (42.12.2), sewa `6 × (b+1)` NB/minggu. Lapak Sekte 38 (khusus aula,
  ibu kota) tetap; logika jualnya **dipakai bersama** (`LapakSekte.peluangLaku(h)` dipisah menjadi metode publik, 42C).
* **Pajangan**: blok baru yang **ditaruh pemain** (bangunan biasa): `kultivator:rak_dagang` (3 tumpukan tampil di rak,
  `BlockEntityRenderer`, label harga billboard ≤ 8 blok), `kultivator:meja_kasir` (tempat pegawai berdiri; toko tanpa kasir
  hanya laku saat pemain hadir), `kultivator:papan_nama_toko` (nama toko billboard + bendera kecil klien). Maks 12 rak dihitung
  per toko. Resep: Tukang menjual 20/30/15 NB, atau kerajinan (meja kerja) dari papan + lentera/besi.
* **Harga**: per tumpukan di layar Toko (bawaan = harga `HargaKota`), maks 1,5 × nilai (38.M.5).
* **Pembeli**: tiap jam dunia saat toko buka, arus pembeli = `penduduk × lalu-lintas distrik (pasar 1,0, jalan utama 0,7,
  hunian 0,3) × kurva jam (puncak 9-11 dan 17-19) × (1 + suasana rata-rata / 200) × reputasi toko / 50`. Tiap pembeli memilih
  kategori dari `tempat/permintaan/<jenis>.json` dan membeli dengan peluang laku 38.M.4.1 (h ≤ 1,0 → 80 %, ≤ 1,2 → 50 %,
  ≤ 1,5 → 20 %). **Pembeli bertubuh**: bila pemain ≤ 64 blok, warga dengan kegiatan `belanja` (penimpa jadwal 42A) berjalan
  ke pintu, `lihat_barang` di rak, `tukar_koin` di kasir, pergi membawa prop barang; penjualan dicatat saat tubuh membayar;
  tanpa pemain disimulasikan per jam.
* **Pegawai**: warga menganggur (tablet Hubungan → `[Pekerjakan]`) gaji 8/10/12/15 NB per minggu, atau murid (42.8.3, tanpa
  gaji NB). Watak: ramah +10 % laku, licik +5 % tetapi 5 %/minggu barang hilang, jujur tanpa hilang, malas tutup 2 jam lebih
  awal; `ahli` naik +4/minggu (laku +0,2 % per ahli).
* **Reputasi toko** 0-100: harga wajar (≤ 1,1 → +1/hari), ragam ≥ 6 jenis +, kualitas barang, pegawai ramah, hiasan festival
  (pemain menaruh lentera = blok), keluhan (barang palsu/beracun) −10.
* **Pencurian malam** 2 %/minggu tanpa penjaga (satu tumpukan); murid penjaga (42.8.3) atau anjing kota pemain mencegah.
* Buku: `BukuKas.catat(server, pemilik, "toko", …)` (kunci UUID pemain, tidak perlu aula). Hasil penjualan disimpan di laci kasir
  (diambil di layar) atau langsung ke saldo Rumah Uang (pilihan).

#### 42.6.3 Harga dinamis (`HargaKota`)

`HargaKota.faktor(MinecraftServer, long situs, Kategori k)` = `clamp(0,6, 1,6, permintaan × langka × acara × musim × perang ×
jenuhPemain × pelindung × faktorTakut38R)`; **dipakai semua penjual 34/38/42** (`Toko`, `TokoSenjata`, `PasarDesa`, `Hidangan`,
`Penginapan` (JASA), `PasarGelap`, toko pemain, `Dagang` untuk barang dagang memakai `jenuh/permintaan` miliknya sendiri yang
sudah ada dan hanya dikali `acara × perang`). Beli-kembali dikali faktor yang sama tetapi tetap ≤ 0,5 × harga jual.

| Kategori | Diproduksi oleh (per hari) | Dipakai oleh | Contoh barang |
|---|---|---|---|
| `PANGAN` | PETANI 3, NELAYAN 2, PEMETIK 1 | semua warga 0,2/orang | bakpao, beras, ikan, hidangan |
| `OBAT` | ALKEMIS 2, TABIB 1 | sakit 1, serangan 5 | pil, ramuan |
| `HERBA` | PEMETIK_HERBA 3, PETANI 0,5 | ALKEMIS 2 | herba muda |
| `SENJATA` | EMPU 2, PANDAI 1 | PENJAGA 0,2, perang 10 | senjata kota |
| `ZIRAH` | EMPU 1, PANDAI 1 | PENJAGA 0,2 | zirah vanilla |
| `JIMAT` | RAHIB 1, CENDEKIA 1 | — | jimat |
| `BAHAN` | KULI 1, TUKANG 0,5 | kavling/perbaikan | kayu, batu, genteng |
| `KAIN` | PENENUN 3 | festival, pemakaman, musim dingin | kain, jubah |
| `MEWAH` | AHLI_TEH, PELUKIS | festival, PEJABAT | teh mahal, giok hias, arak |
| `DAGANG` | — (dagang antar-kota 38.T) | — | barang `kultivator:dagang/*` |
| `JASA` | — | — | kamar, tabib, teleport |
| `BANGUNAN` | — (mengikuti kemakmuran dan kepadatan) | — | harga beli/sewa rumah, kavling |

* **Langka**: stok kota per kategori 0-200 (awal 100): + produksi + kafilah tiba (+20 per kafilah, `KafilahJalan` tiba di
  tempat) + penjualan pemain, − konsumsi − pembelian pemain; `langka = 1 + 0,5 × (100 − stok) / 100`.
* **Acara**: festival PANGAN ×1,15, MEWAH ×1,25, KAIN ×1,1; pernikahan besar MEWAH ×1,05; pemakaman KAIN ×1,05; serangan/perang
  SENJATA ×1,3, OBAT ×1,25, PANGAN ×1,2, MEWAH ×0,8; Pasar Hantu malam ini tidak memengaruhi.
* **Musim**: gugur (panen) PANGAN ×0,8; dingin PANGAN ×1,1, BAHAN (kayu) ×1,2, KAIN ×1,1; semi HERBA ×0,9.
* **Perang** (42.8.2/42.9.5): produksi ×0,5, kafilah ke tempat itu berhenti.
* **Banjir pasar** (jenuh pemain): harga jual pemain × `max(0,6, 1 − 0,01 × n)`, n = unit kategori itu yang dijual pemain ke
  tempat itu 24 jam terakhir (pulih 1 unit per jam dunia; pola `Dagang.jenuh`).
* **Pelindung**: dilindungi sekte pemain ×0,97 (lebih aman, kafilah lebih banyak).
* **Lumbung Penstabil** (*changping cang*): PANGAN > 1,3 selama 3 hari → Kas Kota membeli dari kafilah (`200 NB` maks/hari) dan
  membuka lumbung: stok PANGAN +40, antrean warga berkarung di titik `lumbung` (gudang), gosip "Lumbung dibuka".
* **Tampilan**: `LabelHarga` dengan panah tren dan tooltip sebab ("Langka · stok 23 · Festival +15 %"); **Papan Harga**
  (`papan_hidup` 38 di pasar, teks klien) menampilkan 6 perubahan terbesar hari ini.

#### 42.6.4 Investasi di usaha warga (`UsahaWarga`, `Investasi`)

* **Usaha** = lot kerja dengan pemilik Warga: rumah makan, kedai teh, kedai arak, toko kain, bengkel pandai, apotek, penginapan,
  warung sup, toko kue, rumah judi (distrik malam), perahu pos (Kota Air), tenda bazar (Oasis). Tiap usaha: `modal` (nilai),
  `omzet harian = dasar[jenis] × lalu-lintas × (1 + suasana/200) × harga × (0,7 + ahli/200) × saingan`, margin 5-15 % menurut
  watak pemilik (serakah +3 %, pemalas −4 %, pemabuk −3 %, pekerja_keras +3 %).
* **Menanam**: 50 NB sampai `200 × (b+1)` per usaha; maks 3 usaha per tempat, 10 total. Saham = setoran / (modal + setoran).
  Bagi laba **mingguan** = saham × laba minggu (bisa negatif: nilai saham turun). Rata-rata +3..6 %/minggu, simpangan 5 %;
  batas keran 42.6.0.
* **Peristiwa**: kebakaran kecil di lot (42.7.4) −1 minggu laba; festival +40 %; saingan buka (kavling baru sejenis) −15 %;
  pemilik wafat → ahli waris lanjut (50 %) atau tutup (saham dibayar 70-100 % nilai); pemilik jadi pemabuk −20 %; pemain
  mengirim murid sebagai **pengelola** (42.8.3) +10 % laba.
* **Tarik**: pemberitahuan 7 hari, dibayar nilai saham saat itu − 2 %.
* **Pialang**: Juragan Shen Wan (tokoh 42.10) di Rumah Uang ibu kota dan tiap kota tema; di kota kecil langsung ke pemilik
  usaha (tablet Hubungan `[Tanam modal]`).
* **Usaha baru di kavling**: pemain membiayai satu bangunan kavling (42.9.4, biaya bahan) → usaha baru dengan pemain sebagai
  pemegang saham 60 %, pemilik = warga menganggur/magang terbaik; kota tumbuh lebih cepat.

#### 42.6.5 Gudang dan kontrak dagang (`GudangKota`, `KontrakDagang`, `RumahDagang`)

* **Gudang**: sewa ruang di titik `gudang` (lot gudang ibu kota, gudang pelabuhan, gudang kafilah pos, gudang kota 34 baru
  42W): 27/54/108 slot untuk 10/18/30 NB per minggu, isi disimpan di SavedData (pola `Brankas`; wadah berisi dan cincin
  **ditolak**, 38.0 butir 16). Penjaga gudang (Nyonya Fang 42.10 atau `KULI` kepala).
* **Kirim antar-gudang**: kafilah data `KafilahJalan` (tubuh gerobak terlihat) ongkos 3 % nilai, `ceil(jarak / 1500)` hari,
  begal 10 %/hari tanpa pengawal (2 % dengan ≥ 2 murid Fondasi+ atau pemain mengawal, misi `kafilah` 38).
* **Kontrak berulang** di **Rumah Dagang** (petugas `PEDAGANG_SEBERANG`/`KAFILAH_BASHI`/`BANKIR` di tiap kota ≥ 34) dan Papan
  Kontrak Muatan 38.T.10.3: "Kirim 64 Teh Awan dari Kota Air ke Ibu Kota setiap 7 hari selama 4 kali, 3,2 NB/unit, denda gagal
  15 % nilai". Dipenuhi dari gudang asal (kafilah otomatis berangkat bila stok cukup pada hari kontrak). **Nama Dagang** pemain
  0-1000 (+10 per pengiriman tepat waktu, −30 gagal) membuka tingkat I-V (volume dan harga). Kontrak aktif ≤ 2 + tingkat.
  Laba masuk **batas laba mingguan Dagang** yang sudah ada (satu batas).
* **Pesanan Kota**: kota sendiri memesan bahan untuk kavling dan pembangunan kembali (42.9.4/42.9.5): "Kota butuh 200 kayu, 300
  batu, 120 genteng tanah liat", dibayar `HargaKota × 1,1` dari Kas Kota, Nama Baik +1 per 32 barang.

#### 42.6.6 Pajak kota dan jabatan (`PajakKota`, `Jabatan`, `Perkara`)

* **Pajak**: tarif 5-20 % (bawaan 8 %) per tempat. Pemasukan mingguan Kas Kota = Σ laba usaha × tarif + pajak properti 1 % +
  pajak toko pemain 5 % + bea (oasis 38.T) + sewa lapak/gudang. Pengeluaran otomatis: penjaga, lumbung, festival (38),
  bahan kavling (42.9.4), pembangunan kembali (42.9.5). Tarif > 10 % → suasana −1 per poin; < 6 % → kemakmuran +1/minggu.
* **Jabatan** (per tempat; kota 34, kota tema, ibu kota; kampung hanya pangkat 1-2 "Tetua Kehormatan"):

| Pangkat | Gelar | Syarat | Tugas | Gaji (dari Kas Kota) | Hak |
|---|---|---|---|---|---|
| 1 | Juru Tulis Kehormatan | Nama Baik ≥ 40, quest "Catatan Kota" | — | 0 | diskon 3 %, lencana |
| 2 | Pengawas Pasar | ≥ 55, 3 tugas kota | periksa timbangan (temukan pedagang curang: dialog + bukti) 1/minggu | 1 % pajak mingguan | diskon 5 %, Papan Harga terbuka penuh |
| 3 | Hakim Pembantu | ≥ 70, Fondasi+ | **sidang 2 perkara/minggu** | 2 % | lihat semua gosip "benar/bumbu" |
| 4 | Wakil Wali Kota | ≥ 80, Inti Emas+, quest "Banjir Musim Semi" | pilih bangunan kavling berikut, ubah pajak ±2 | 3 % | kursi di balai, panji pribadi kecil |
| 5 | Wali Kota (ibu kota: Menteri Kehormatan lewat Perintah Istana; tidak pernah Pangeran) | ≥ 90, Jiwa Baru Lahir+, restu tetua (dialog) | tetapkan pajak 5-20 %, rencana tumbuh, anggaran festival, rekrut penjaga | 5 % | namamu di gerbang (billboard), suara di kalender acara |

  Gaji dibatasi `20 × (b+1) × pangkat` NB/minggu per tempat; maks 2 tempat berpangkat ≥ 3. Tidak datang 14 hari → peringatan,
  28 hari → turun satu pangkat.
* **Perkara** (`data/kultivator/tempat/perkara/*.json`, ≥ 24 templat, isian warga nyata): sengketa batas kebun, ayam dicuri,
  utang tak dibayar, mas kawin, warisan rumah, perkelahian di kedai, pedagang curang, gosip fitnah (tautan ke rantai gosip
  42.5.4), anak hilang, kebakaran karena lalai. Tiap perkara: 2-3 saksi (tubuh dipanggil ke balai), bukti (barang/gosip yang
  bisa ditelusuri), 3 putusan; putusan benar → kesan pihak benar +, suasana tempat +, salah → −; **suap** (pihak licik
  menawarkan NB) → terima: NB + Infami +2 (`TemaApi.tambahInfami`), 20 % audit oleh pejabat atas (Hakim Bao Qing di ibu
  kota) → turun pangkat + denda 2 × suap.

#### 42.6.7 Pasar Hantu (`PasarHantu`)

* **Di mana**: tiap kota 34, ibu kota, kota tema (kecuali Kota Iblis yang punya pasar gelapnya sendiri) dan pos dagang punya
  3-5 lokasi (`pasar_hantu_<n>` titik baru 42W: bawah jembatan, gudang kosong, gang buntu, dekat makam di luar tembok, dermaga
  tua; cadangan: titik `gelap`, `kumuh`, `obrol` sepi). Tiap malam satu lokasi dipilih `hash(situs, hari)` (deterministik, jadi
  gosip bisa "tahu").
* **Kapan**: geng ke-5 01:00-05:00 (tick 19000-23000); bubar saat fajar (lapak lenyap satu per satu, lentera padam).
* **Menemukan**: token Kedai Teh (60 NB, `KedaiTeh` yang ada) kini memberi **sandi malam** (teka-teki lokasi: "Di tempat air
  tidur di bawah batu" = bawah jembatan); anak pengemis penunjuk 5 NB; warga `pengintip`/`licik` lewat Kabar; lentera hijau
  Tuan Lentera Hijau (gambar klien) terlihat dari 48 blok saat buka.
* **Isi**: 3-6 lapak entitas `lapak_hantu` (kain di tanah, peti, lentera hijau), penjual bertopeng (`SosokApi.setRupa`
  `topeng_kertas`); dagangan = daftar `Harga.SHADOW` lewat `PasarGelap.dagangan(situs, hari)` (dipisah publik, 42C) + **barang
  curian pemain** (dari copet 42.7.4; tebus 1,2 × nilai) + gulungan peta langka, barang dagang selundupan (`minyak_bayangan`);
  **penadah** membeli barang curian/selundupan 0,6 × nilai (Infami +1).
* **Topeng**: item `kultivator:topeng_pasar_hantu` (20 NB dari penjual token) → diskon 10 % dan tanpa Nama Baik hilang saat
  razia.
* **Razia** 10 % per malam (logika razia `PasarGelap` yang sama): penjaga berlentera datang dari dua arah, lapak lenyap, pemain
  tanpa topeng yang tertangkap Nama Baik −5 (suap: `PasarGelap` yang ada).
* `PasarGelap` di jangkar `kota/gelap` tetap sebagai "kantor siang" (penadah dan penjual token); tidak ada pasar gelap kedua.

---------------------------------------------------------------------------------------------------------------------

### 42.7 Kehidupan kota (D21-D27; modul 42D)

Semua peristiwa 42.7 yang terlihat hanya dimainkan bila pemain ≤ 96 blok dari tempat (selain itu cukup data + gosip), lewat
`HidupApi.timpaJadwal` (acara satu tempat), `PenimpaJadwal` 42A (peserta per warga) dan tubuh 38. Kalender: `KalenderKota`
(42D) menyatukan semua acara 42 per tempat dan mendaftarkan ke `KotaBesarKalender` (ibu kota) dan tab Acara Peta Kota.

#### 42.7.1 Rombongan keliling (`Rombongan`)

Enam rombongan bernama **global** berkeliling di antara tempat yang dikenal (rute `RencanaJalan` 38W, tubuh berjalan pola
`KafilahJalan`): tiba tiap 5-9 hari per kota (hash), menetap 1-2 hari, berangkat ke kota berikut (gosip: "Rombongan Bulan
Sabit akan ke Kota Air lusa"). Panggung = entitas `panggung_keliling` (42.12.2) di titik `panggung` atau alun (bukan blok).

| Rombongan | Jenis | Tokoh (42.10) | Pertunjukan / dagangan | Untuk pemain | Ilham |
|---|---|---|---|---|---|
| **Rombongan Bulan Sabit** | akrobat + barongsai | Nyonya Hong Ling (ketua), Yan Feiyan & Yan Feihu | 10:00, 15:00, 19:00, 4 menit: piring berputar (`akrobat_piring`), lompat simpai (`akrobat_simpai`), piramid manusia 3 tingkat (`akrobat_piramid`, 3 tubuh tersinkron), egrang (`egrang`), **barongsai** (entitas `barongsai`, 2 penari) | tonton sampai akhir → buff *Terhibur* (pemahaman +0,01/menit 10 menit), tip 1-10 NB → kesan + | *baixi* Han |
| **Lao Shuo dan Balok Kayu** | pendongeng | Lao Shuo | sesi 10:00, 15:00, 20:00 di kedai teh/alun: 12 kisah (42.10), `dongeng` + `balok_kayu` (prop 193, suara ketuk kayu), warga duduk `duduk_lantai` | dengar ≥ 60 s → satu halaman lore (38.S.7) + petunjuk satu tempat tersembunyi (`DuniaSites.discover` sebagai rumor) | *shuoshu* |
| **Ah Qin si Erhu** | pengamen | Ah Qin | main di jembatan/gerbang 9-12, 16-19; 3 lagu erhu (melodi klien 42.12.6) | lempar koin (kesan, sink), minta lagu, alur "Lagu di Bawah Jembatan" | Abing |
| **Tabib Keliling Hu Lu** | penjual obat | Hu Lu | peragaan silat mematahkan papan prop, jual "Plester Harimau" (sembuh kecil) dan "Pil Seribu Khasiat" (60 % sembuh, 30 % kosong, 10 % mual) | pemain Tingkat Alkemis ≥ 2 bisa membongkar penipuan (Nama Baik +3) atau bermitra (bagi laba kecil) | *jianghu langzhong*, Xue Yong |
| **Kafilah Saudagar Ma** | pedagang keliling besar | Saudagar Ma Fu | barang dari kota jauh (`Dagang` barang asal kota lain, × 1,1), 1 barang langka | Buku Dagang mode pasar | `PEDAGANG_KELILING` |
| **Pemusik Pengantin Suona** | pemusik upacara | — (4 pemusik) | disewa keluarga warga untuk pernikahan/pemakaman (42.7.7) | — | kebiasaan rakyat |

#### 42.7.2 Rutinitas pagi (`RutinitasPagi`)

| Jam | Yang terjadi | Detail terlihat |
|---|---|---|
| 05:00 | ayam jago berkokok (42.7.5), lonceng kuil, ronda geng ke-5 terakhir | suara, merpati terbang |
| 05:00-06:30 | **tai chi** di alun/taman: 4-16 warga berkebiasaan `tai_chi` + tetua pemimpin di depan | barisan grid 4 kolom, klip `tai_chi_pagi` tersinkron (`gerakSinkron`), pemimpin varian `pimpin`; pemain berdiri di barisan 60 s → buff *Napas Pagi* (regen Qi +5 % 10 menit, sekali per hari) |
| 05:30-06:30 | penyapu jalan, basuh muka di sumur, asap dapur pertama | `sapu`, `basuh_muka`, asap 42.9.2 |
| 06:00-08:00 | **pasar pagi**: 3-6 entitas `tenda_pasar` di titik `pasar_pagi_<n>` (atau `lapak`), petani membawa keranjang sayur, penjual bakpao dengan kukusan beruap | `jalan_pikul` → `seru_dagang`, uap partikel; harga PANGAN −5 % sampai 08:00 |
| 06:30-07:00 | sarapan warung | `makan` di bangku warung |
| 07:00 | **anak ke sekolah**: Tuan Guru Zhou Shu berjalan dari rumah ke rumah, anak keluar dan berbaris di belakangnya (rantai ikut, jarak 1,2 blok) menuju titik `sekolah` (atau kuil bila tidak ada sekolah) | `jalan_anak` berbaris, lambaian ibu di pintu |
| 07:15-10:00 | sekolah: anak duduk, menghafal bersama (suara `kota42.sekolah_hafal`, 3 baris *Sanzijing*), guru `mengajar` | gelembung teks hafalan |
| 10:00 | sekolah bubar: anak berlari keluar (`main_kejar`) | — |

#### 42.7.3 Ronda malam (`Ronda`)

* Dua penjaga malam per kota 34/tema/ibu kota (kampung satu): pembawa **gong tangan** (prop 190) dan **kentongan** (prop 191)
  + lentera (28), rute simpul jalan utama + titik `ronda_<n>` baru (42W). Di ibu kota bersambung dengan genderang
  `JamMalam` (22:00).
* **Geng** (jaga malam): geng 1 19:00, 2 21:00, 3 23:00, 4 01:00, 5 03:00 (tick 13000/15000/17000/19000/21000). Awal tiap geng:
  pukulan berpola (geng n = n pukulan gong + derap kentongan), lalu seruan: geng 1-2 "Udara kering, hati-hati api dan
  lilin!", geng 3 "Kunci pintu, waspada pencuri!", geng 4 "Geng keempat, tidurlah tenang!", geng 5 "Geng kelima, fajar
  menjelang!" (suara 42.12.7; terdengar ≤ 64 blok).
* Penjaga malam memeriksa pintu (`ketuk_pintu`), mengusir pemabuk pulang, melapor api kecil (42.7.4), mengejar pencuri
  rumah. **Pemain ikut ronda** (dialog `[Ikut ronda]`): berjalan bersama satu geng penuh → Nama Baik +1, 3 NB dari Kas Kota,
  peluang menangkap pencuri ×3. Murid penjaga (42.8.3) ikut ronda.
* Generasi: Lao Geng menua (42.10), muridnya Xiao Bang mewarisi gong.

#### 42.7.4 Peristiwa acak (`PeristiwaJalan`)

2-4 peristiwa per kota per hari dunia, hanya saat pemain ≤ 96 blok; selain itu menjadi gosip.

| Peristiwa | Pemicu | Yang terlihat | Pilihan pemain | Hasil |
|---|---|---|---|---|
| **Copet (pada pemain)** | pemain diam/berjalan pelan di kerumunan ≥ 4 warga (pasar, festival), tidak sibuk, maks 1/hari/kota, 3 %/menit | tubuh `PENCOPET` (peran baru) menabrak (`tabrak`, suara "permisi"), takarir "Kantongmu terasa ringan!"; pemain Fondasi+ 50 % merasakan lebih dulu (prompt `[Tangkap tangan]` 1,5 s) | **kejar** 30 s: pencopet berlari lewat gang (kecepatan 1,25 × jalan pemain, rute simpul), pukul sekali = berlutut | tertangkap: barang kembali, Nama Baik +2, saksi kesan +3, pencopet ke penjaga; lolos: barang ke **Pasar Hantu** malam itu (tebus) atau sarang copet (alur Tikus Seribu Jari) |
| Copet (pada warga) | pasar ramai | warga berteriak "Copet!", pencopet lari | kejar | tangkap → kesan korban +20, gosip |
| **Perkelahian** | dua `MUSUH`, pemabuk, penjudi kalah | kerumunan (`HidupApi.kerumunan`, 8 penonton `sorak`), tinju kedai (`tinju_kedai_a/b` tersinkron), meja terbalik (prop) | `[Lerai]` (dialog: tenangkan/gertak, ranah ≥ 2 di atas otomatis berhasil), `[Taruhan]` (EV 0,9), `[Ikut]` (Nama Baik −3) | lerai: kesan kedua pihak ±, suasana tempat +; penjaga datang 40 s; tidak ada yang mati (luka ringan `PULIH`) |
| **Gerobak terguling** | jalan utama, siang | `GerobakEntity` keadaan terguling (miring 70°, roda berputar), 6-10 barang tercecer (entitas klien `barang_tercecer`), pemilik meratap | `[Angkat]` (kanal 3 s, kuat ranah ≥ Pemurnian Qi 5 sekali), kumpulkan barang (klik tiap barang) | makanan/barang kecil, kesan pemilik +10; diam saja: warga lain membantu 3 menit |
| **Api kecil** | dapur/warung/lapak, 2 %/hari per usaha, malam ×2 | entitas `api_kecil` (lidah api + asap + gemeretak) di titik lapak/dapur; tiap 60 s tanpa padam menjalar ke titik tetangga (maks 3 entitas); warga membentuk **rantai ember** dari sumur (`oper_ember` tersinkron), penjaga malam memukul gong cepat | siram dengan ember air (klik entitas), teknik air/es, perintah murid | padam: Nama Baik +3, kesan pemilik +25 (`DISELAMATKAN`); 3 menit tidak padam: api "habis", usaha rugi 1 minggu, suasana −10 (**tanpa blok berubah**, tanpa korban) |
| Ayam lepas | kampung, pagi | ayam kota berhamburan, petani mengejar | tangkap 3 ayam (klik) | telur ×2, kesan +5 |
| Anak tersesat | anak di luar kuota jarak, sore | anak menangis di tepi kota | antar pulang | permintaan `anak_hilang` 42.5.6 |

#### 42.7.5 Hewan kota (`HewanKota`, entitas 42.12.2)

| Hewan | Varian | Tempat (titik) | Perilaku | Interaksi |
|---|---|---|---|---|
| **Kucing** `kucing_kota` | 6 corak: oranye belang, hitam, putih, belang tiga, abu tabby, siam | atap/jendela (`kucing_tidur`), pasar ikan, dapur | tidur berjemur, menjilat, meregang, mengikuti penjual ikan, menggosok kaki pemiliknya, mendesis ke anjing, mengejar merpati, lompat 2 blok ke atap | elus (klik: dengkur partikel), beri ikan → mengikuti 1 jam; kucing bernama **Lao Hua** di pasar ikan ibu kota; diadopsi untuk rumah pemain (warga `teman_kucing`) |
| **Anjing** `anjing_kota` | kampung kuning, hitam, belang, anjing penjaga besar berbulu | pintu rumah, gerbang, tukang daging | mengikuti anak, tidur di depan pintu, mengemis di tukang daging, menggonggong pada orang asing malam hari dan pada pemain bernama buruk, ikut ronda | elus, beri tulang → menjaga rumah/toko pemain (mencegah pencurian) |
| **Ayam** `ayam_kota` | jago, betina, anak ayam (3-5 mengikuti induk) | halaman kampung, kandang | mematuk, jago berkokok 05:00, kabur mengepak saat pemain berlari | peristiwa ayam lepas |
| **Merpati** `kawanan_merpati` | satu entitas = 8-16 burung (boid klien) | alun, atap kuil, menara | mendarat, mematuk, terbang serempak saat pemain berlari/≤ 3 blok, berputar dengan dengung peluit merpati | diberi makan tetua `burung_sangkar`/`lempar_pakan` |
| **Kuda** `kuda_tambat` | 4 warna | pos kafilah, penginapan, kandang (`tambat`) | kibas ekor, angguk, makan dari palung, meringkik saat kafilah tiba | ditunggangi pengantin (42.7.7, visual) |
| **Unta** `unta_tambat` | 2 warna | Kota Oasis, pos kafilah gurun | mengunyah, berlutut istirahat, mendengus | (sewa unta 38.T tetap) |
| **Keledai** `keledai_kota` | 1 | kampung (penggilingan), pasar | memutar batu gilingan (prop), membawa karung | — |

Hewan **tidak** dijinakkan, tidak berkembang biak, tidak menjatuhkan barang, tidak disimpan (`noSave`), muncul dari titik saat
pemain ≤ 64 blok (pola `Pemanggil`), ≤ `kotaHewan.perTempat` 24 per tempat; serangan monster → lari bersembunyi.

#### 42.7.6 Musim (`MusimKota`; musim = `(hari / 24) % 4`, 38.N.3)

| Musim | Hari raya (hari ke- dalam musim) | Kota | Barang/jasa | Visual |
|---|---|---|---|---|
| Semi | **Tahun Baru** (0, terbesar): petasan klien, kuplet merah di pintu, 福 terbalik, barongsai keliling, angpao untuk anak; **Qingming** (4): keluarga ke Bukit Makam menyapu nisan, membakar dupa, ranting dedalu di pintu, layang-layang | tanam, kebun bunga | `kue_keranjang` (tahun baru), angpao (item) | kelopak bunga di pohon kota, panji merah |
| Panas | **Perahu Naga** (5, kota air 38.T dan kota bersungai), **Qixi** (15): malam kekasih, lamaran ×2 | anak main air, kipas | `air_plum` (minuman dingin: tahan panas gurun 38.T 30 % lebih lama) | lentera biru, kipas di tangan warga |
| Gugur | **Panen** (6; `FestivalPanen` kampung + pasar panen kota), **Pertengahan Musim Gugur** (14): kue bulan, warga di atap/alun menatap bulan, lentera kelinci; **Chongyang** (18): tetua naik bukit | PANGAN ×0,8, tumpukan hasil (prop) | `kue_bulan` | daun gugur partikel, lentera kelinci |
| Dingin | **Laba** (8): bubur delapan bahan dibagikan gratis di kuil (pemain menyumbang beras → Nama Baik), **Titik Balik** (21): pangsit | **Warung Sup Nenek Tang** buka (sup hangat), warga memakai mantel kapas (rupa `*.dingin`), lebih lama di dalam rumah (38) | `sup_hangat` (*Hangat*: kebal beku salju bubuk + regen I 30 s), `bubur_laba` | asap dapur tebal, salju **hanya bila bioma bersalju** (dibaca) |

#### 42.7.7 Upacara: pernikahan, pemakaman, kelahiran (`Upacara`)

Upacara adalah **peristiwa nyata warga** (42.4.6) yang dijadwalkan pada hari berikut di jam tetap; terlihat bila pemain ≤ 96
blok, selain itu berlangsung sebagai data (gosip, suasana). Payload `kultivator:kota_upacara` (42.13.2) membawa jalur dan
peserta agar klien menggambar efek.

**Pernikahan** (09:00-21:00):
1. 09:00 Mak Comblang (Bibi Mei 42.10 atau `TETUA_DESA` perempuan) mengetuk rumah pengantin wanita.
2. 09:30 **penjemputan**: pengantin pria di atas `kuda_tambat` berhias pita merah (tubuh menumpang entitas kuda), 4 pemusik
   (suona prop 192, genderang kecil 194) dan pembawa payung merah berjalan dari rumah pria ke rumah wanita (rute simpul,
   kecepatan 0,6).
3. 10:30 pengantin wanita bercadar merah (rupa `pengantin`, `SosokApi.setBusanaKhusus`) naik **tandu merah** (`TanduEntity`
   yang ada, varian merah 囍, 4 pemikul `pikul_tandu` tersinkron); petasan dan kertas merah (partikel klien), anak-anak
   berlari mengejar arak-arakan meminta permen.
4. 11:30 di rumah pria: **tiga sembah** (langit-bumi, orang tua, satu sama lain; klip `sembah_nikah` tersinkron dua tubuh),
   rumah memasang 囍 (panji 42.9.3).
5. 18:00-21:00 **jamuan**: meja di rumah/kedai/alun (entitas `meja_jamuan` bila tidak ada meja), tamu `makan`, `bersulang`,
   nyanyi; pemain diundang (kesan ≥ 20 atau permintaan `undangan`) → `[Beri angpao]` (min `2 × (b+1)` NB, sink) → kesan +10
   kedua keluarga, kenangan `HADIR_NIKAH`, item `kue_pengantin` (kenyang + suasana pemain? tidak ada suasana pemain: buff
   *Berkah* keberuntungan menjarah +5 % 20 menit).

**Pemakaman** (08:00-11:00 sehari setelah wafat):
1. Rumah duka: lentera putih, kain putih di pintu, 奠 di panji; keluarga berpakaian putih (rupa `berkabung`), altar dengan foto
   lukisan (prop potret 195) dan dupa.
2. 08:00 Paman Bai (pengurus upacara 42.10 atau `RAHIB`) memimpin; peti di **usungan** (`usungan_peti`, 8 pemikul
   tersinkron), pemusik suona lagu duka, uang kertas sembahyang ditaburkan (partikel klien), ratap tangis (suara sedih
   42.12.7), warga di sepanjang jalan menunduk (`hormat` lambat).
3. Di Bukit Makam: peti diletakkan di depan nisan yang diberikan (42.4.4) lalu menghilang ke tanah (partikel tanah, entitas
   tenggelam 40 tick, tanpa blok), nama terukir di nisan (billboard klien), keluarga `sujud_dupa`.
4. Pemain hadir → `[Beri uang duka]` (min `2 × (b+1)` NB, amplop putih, sink) → kesan +12, kenangan `HADIR_MAKAM`. Saat pemain
   memasuki tempat itu dalam 3 hari: kartu kecil "Kakek Xiang wafat semalam" bila pemain mengenalnya (kesan ≥ 20).

**Kelahiran**:
1. Hari lahir: ayah membagikan **telur merah** (item `telur_merah`) ke tetangga dan pemain (kesan ≥ 10), gosip.
2. **Pesta sebulan** (hari dunia berikut 18:00): jamuan kecil di rumah, ibu menggendong bayi (prop `bayi_bedong` 196).
3. ***Zhuazhou*** (umur 1, hari dunia ke-2 setelah lahir, 10:00): tubuh bayi sementara (Sosok anak, skala 0,45) di atas tikar
   dikelilingi 8 benda (prop kuas, sempoa, pedang kayu, cap, kitab, palu, tasbih, koin); bayi merangkak (`merangkak`) lalu
   memilih (`pilih_benda`); keluarga dan tamu bersorak; hasil menentukan kecenderungan jalan anak (42.4.7); bayi berakar
   yang memilih pedang → gosip "Calon pendekar!" dan tanda di Kantor Rekrutmen.

---------------------------------------------------------------------------------------------------------------------

### 42.8 Sekte dan kota (F33-F36; modul 42E)

Syarat umum: pemain punya aula (`Halls.hallOf`); level aula dari `Halls.level`. Bila 41 sudah dibangun, anggota sekte NPC
(tanpa aula) tidak mendapat 42.8 kecuali fitur yang dibuka 41 (Kantor Rekrutmen atas nama sekte NPC ditolak; 42.15.3).

#### 42.8.1 Kantor Rekrutmen (`KantorRekrut`)

* **Tempat**: gedung `kota/kantor_sekte` di kota 34 (sudah ada di NBT), lot baru `kantor_rekrut` di distrik Gilda ibu kota (4
  bilik sewaan) dan tiap kota tema (2 bilik), kampung besar: meja di balai desa (titik `rekrut`). Sewa: kampung 5, kota 10,
  kota tema 15, ibu kota 25 NB per 7 hari; syarat aula lv ≥ 2, Nama Baik tempat ≥ 10.
* **Penjaga**: murid ditugaskan (42.8.3) atau warga juru tulis (gaji 42.6.2). Panji lambang sekte di pintu (`PanjiKota`),
  papan pengumuman (`papan_hidup` 38) "Sekte <nama> menerima murid".
* **Pelamar** per hari: `0,3 × bobotAkar × (1 + renown / 1000) × (1 + kesanKeluarga / 100) × gosip × kualitas penjaga`
  dari warga berakar yang belum direkrut (anak ≥ 8, remaja, dewasa ≤ 30), dikurangi bila sekte NPC punya perekrut di tempat
  itu (`HariGerbang`). Kartu pelamar: potret 3D, nama, umur, akar (pasti bila diuji, perkiraan bila belum), watak, kebiasaan,
  hasil *zhuazhou*, sikap keluarga, uang restu.
* **Aksi**: `[Uji akar]` (Batu Uji di meja: entitas `batu_uji` menyala warna akar, 2 NB), `[Terima]` (uang restu: BANGGA 0,
  RAGU `10 × akar × (b+1)` NB, MENOLAK → quest "Restu Ayah"), `[Tolak]`. Maks 3 diterima per kantor per 7 hari → `Halls.
  addCandidates` (42.4.9). **Adegan pamit**: anak memeluk orang tua (`peluk` dua tubuh), berjalan ke gerbang membawa buntelan
  dan lenyap di luar gerbang; gosip bangga.
* **Hari Ujian Terbuka** (pemain mengadakan, sekali per 10 hari per kantor, 20 NB): 6-12 anak berbaris, satu per satu berdiri
  di Batu Uji (aura), kerumunan warga (`HidupApi.kerumunan`), pemain memilih; pelamar ×2 hari itu.

#### 42.8.2 Sekte pemain melindungi kota (`PaktaLindung`)

* **Siapa**: kota 34, kota tema (kecuali Kota Iblis bagi sekte Lurus; sekte Iblis malah hanya Kota Iblis dan kampung yang
  Takut ≥ 40), kampung 38 (bukan Desa Bawahan vanilla yang punya sistemnya sendiri). Ibu kota tidak (pelindungnya istana);
  gantinya gelar "Sekte Sahabat Istana" lewat Perintah Istana (sudah 38).
* **Syarat**: aula lv ≥ 3 (kota/tema) atau ≥ 2 (kampung), Nama Baik tempat ≥ 50, maks `kotaSekte.paktaMaks` 3 kota.
* **Pelindung lama**: 50 % tempat lahir di bawah sekte NPC terdekat beraliran cocok (`DiplomasiService.nearest`, ≤ 2000 blok;
  panji lambangnya di gerbang). Merebut: (a) dialog dengan utusan sekte NPC (standing ≥ 20: diserahkan baik-baik), (b)
  tanding dengan juara utusan (Duel Pedang aturan ramah), atau (c) tetua kota memilih pemain (renown pemain > 2 × tier
  sekte NPC × 100) dengan akibat `DiplomasiService.adjust(owner, sekte, −10, "kota.rebut_pelindung")`.
* **Upeti** mingguan: `30 × (b+1) × kelasKota` NB (kelas kampung 1, kota 2, kota tema 3; dari Kas Kota; tunda bila Kas
  kurang) + barang surplus kategori terbanyak (≤ 16, ke Lumbung lewat `EkonomiApi.kas` catatan "upeti kota"); renown +5/minggu
  (`Halls.addRenown`); Nama Baik +1/minggu; harga −3 % (42.6.3).
* **Kewajiban**: **Panggilan Bela Kota** (`SerbuanKota`, 1 per 10-20 hari per pakta, lewat tekanan musuh `Sibuk`): binatang,
  begal atau kultivator iblis mengepung (gelombang di luar tembok, pola `GelombangBinatang` 38KS; 42E memakai metode publik
  baru `GelombangBinatang.mulai(level, situs, kekuatan, jenis)` yang ditambahkan aditif oleh 42E). Jawab dalam 1 hari dunia:
  pemain datang (gelombang nyata) atau kirim ≥ 2 murid Fondasi+ (42.8.3, hasil disimulasikan: kekuatan murid vs gelombang,
  murid tidak pernah mati, bisa terluka 39). Gagal → suasana −20, upeti ½ minggu itu, pengungsi visual; 3 gagal berturut →
  pakta putus, Nama Baik −15.
* **Tampak**: panji lambang sekte pemain di gerbang dan menara (`PanjiKota`), pos jaga murid, warga menyapa "Pelindung!",
  penjaga gerbang memberi hormat, gosip kemenangan bela kota.

#### 42.8.3 Murid di kota (`TugasKotaMurid`, memakai sistem 39)

Tiga jenis tugas baru lewat kontrak 39 (`MandiriApi.addTujuanProvider` + kegiatan baru yang didaftarkan 42E ke katalog 39
lewat API publik; bila katalog 39 tidak membuka pendaftaran, 42E menambah satu metode aditif `MandiriApi.daftarTugas(...)`):

| Tugas | Penugasan | Tubuh di kota (`JELMAAN` 39) | Efek |
|---|---|---|---|
| `JAGA_KOTA` | kota berpakta, rotasi 3/7 hari | berdiri di gerbang/pos (`berdiri_jaga`, seragam sekte), patroli siang, ikut ronda malam | copet −50 %, pencurian toko 0, kekuatan bela kota + ranah murid; murid ber-jalan PENDEKAR/PENJAGA Kepuasan + |
| `KERJA_KOTA` | toko pemain, cabang, kantor rekrutmen, usaha yang ditanami | di belakang `meja_kasir` (`hitung_sempoa`, `seru_dagang`), mengajar di balai latihan (`mengajar`) | pegawai tanpa gaji NB, watak murid berlaku (42.6.2), pengelola usaha +10 % laba |
| `BELANJA` (39 yang ada) | — | 39.6.6 | belanja memakai `HargaKota`; murid membeli dari toko sekte sendiri dengan potongan 20 % (uang tetap dari dompet murid ke kas toko) |

Murid bertugas mendapat XP harian kecil (`MandiriApi` skala 39), bisa berteman dengan warga (hubungan warga ↔ murid disimpan di
sisi warga sebagai tautan `SAHABAT` dengan id `murid:<soul>`), dan kadang menjadi "kenalan hati" warga (busur asmara 39.8.3);
Buku Harian mencatat kisah kota. Murid pulang menjenguk keluarga kota asal (42.4.9).

#### 42.8.4 Cabang sekte di kota (`CabangKota`)

* **Syarat**: bangunan milik pemain (42.6.1: besar, rumah toko atau paviliun), aula lv ≥ 4, pakta di kota itu **atau** Nama Baik
  ≥ 60. Pemain menaruh blok `kultivator:papan_cabang` (papan berlambang, bangunan biasa) di dalam bangunan → cabang terdaftar.
  Nama: "<Sekte> · Cabang Kota <tempat>" (beda dengan Sekte Cabang 41.8.2; kode `CabangKota`).
* **Jenis** (dipilih di layar Cabang; ganti dengan jeda 7 hari):

| Jenis | Isi wajib (blok pemain di dalam bangunan) | Penjaga | Efek |
|---|---|---|---|
| **Toko Pil Sekte** | ≥ 4 `rak_dagang`, 1 `meja_kasir` | murid `KERJA_KOTA` | toko pemain (42.6.2) dengan "merek sekte": laku +15 %, pembeli tambahan kultivator (Pengembara, murid sekte NPC sekutu) yang membeli barang pita lebih tinggi; batas keran toko × 1,5 |
| **Balai Latihan** (*wuguan*) | ≥ 3 boneka latihan (armor stand pemain) | murid pengajar | 2-6 pemuda kota berlatih (tubuh `latihan_tinju`/`latihan_pedang`), iuran `3 × kelasKota` NB per murid kota per minggu (dari Kas Kota, keran 42.6.0 upeti), pelamar Kantor Rekrutmen +20 %, kekuatan bela kota +10 % |
| **Klinik Sekte** | kuali/brewing stand + 2 kasur | murid ber-Tingkat Alkemis | bahaya wafat sakit warga −20 %, permintaan `obat_ortu` diselesaikan otomatis, jual pil (toko), kesan warga +1/minggu |

  Cabang memberi renown +2/minggu, Nama Baik +1/minggu, panji lambang dan lentera berwarna sekte di depan bangunan.
* Bangunan cabang tetap klaim pemain (42.6.1); bila rumah dijual/disegel, cabang ditutup.

---------------------------------------------------------------------------------------------------------------------

### 42.9 Bangunan dan visual (H42-H47; modul 42W, 42F, 42V)

#### 42.9.1 Interior per profesi (42W, chunk baru)

Kit interior baru `tools/kota_hidup_kit.py` (dipakai `gen_tempat_kota_besar.py`, `gen_dunia_kota.py`, `gen_tempat_kampung.py`,
`gen_tempat_tema_*.py`, `gen_kota_tumbuh.py`): fungsi `interior_<id>(k, x0, y0, z0, w, d, h, hadap)` yang menaruh blok vanilla,
titik kerja/pajangan dan papan nama. **Pajangan** kecil (mangkuk, teko, gulungan, botol pil, kain terlipat, ikan) adalah model
item prop 38.O.4/42.12.4 yang digambar klien di titik `pajangan:<jenis>` (`DisplayKey`, ≤ 24 blok), bukan blok.

| Kit | Bangunan | Blok utama (vanilla) | Titik kerja / pajangan | Papan & bendera |
|---|---|---|---|---|
| `rumah_makan` | rumah makan, bakmi | meja (pagar + pelat), bangku tangga, dapur terbuka (smoker, campfire bertudung, barrel), rak mangkuk | `kerja:wajan`, `kerja:talenan`, `meja_makan` ×6-12, `pajangan:mangkuk`, `pajangan:mi` | papan "面" piksel, bendera kain |
| `kedai_teh` | kedai teh | meja bundar (cakram pelat), kursi, panggung kecil pendongeng, rak teko (flower pot) | `kerja:teko`, `kursi` ×8, `panggung`, `pajangan:teko` | "茶" |
| `kedai_arak` | kedai arak | guci arak (decorated pot), tong (barrel), meja panjang, lentera merah | `kerja:guci`, `kursi` ×8, `meja_dadu` | bendera arak "酒" (42.9.3) |
| `apotek` | toko pil, tabib | **dinding laci seratus** (chiseled bookshelf + trapdoor + barrel bergantian), timbangan (lightning rod + pelat), lumpang, brewing stand | `kerja:laci`, `kerja:lumpang`, `pajangan:botol_pil`, `pajangan:herba` | "药" |
| `penjahit` | toko kain | alat tenun (loom), gulungan wol berwarna, armor stand berbaju (NBT entitas), meja potong | `kerja:tenun`, `kerja:gunting`, `pajangan:kain` | "布" |
| `pandai` | bengkel pandai | anvil, grindstone, blast furnace, ember pendingin (cauldron air), rak senjata, rantai | `kerja:palu`, `kerja:tungku`, `pajangan:pedang` | "铁" |
| `tukang_kayu` | bengkel kayu | tumpukan log, crafting table, stonecutter, kerangka kursi setengah jadi | `kerja:gergaji`, `pajangan:papan` | — |
| `penjual_ikan` | lapak ikan | ember/tub (cauldron), meja batu basah, kail tergantung | `lapak`, `pajangan:ikan` | — |
| `toko_kue` | bakpao, kue | kukusan bambu bertumpuk (composter + scaffolding), tungku | `kerja:kukus` (uap), `pajangan:bakpao` | "饼" |
| `toko_lentera` | lentera & kertas | lentera tergantung (blok lentera vanilla padam tidak ada; dipakai lentera + kertas = wol putih) | `pajangan:lentera_kertas` | — |
| `toko_buku` | kaligrafi & buku | lectern, rak buku, meja tulis | `kerja:kuas`, `pajangan:gulungan` | "书" |
| `toko_jimat` | jimat | altar kecil, lilin, kertas kuning (wol kuning) | `kerja:jimat`, `pajangan:jimat` | "符" |
| `rumah_gadai` | pegadaian | konter tinggi berjeruji (iron bars), peti terkunci | `kerja:konter` | "当" |
| `rumah_judi` | rumah judi | meja hijau (wol hijau), lentera merah, layar | `meja_dadu` ×4, `kursi` | lentera merah |
| `penginapan` | penginapan | kamar berkasur, lorong, tangga, meja penerima | `kasur`, `kerja:konter` | "客栈" (dua glif) |
| `sekolah` | sekolah | meja rendah (trapdoor), lectern guru, papan tulis (blackstone) | `sekolah`, `kursi` anak ×8-12, `mengajar` | — |
| `altar_leluhur` | ruang leluhur rumah | altar (stair terbalik + slab), lilin, papan arwah (item frame dengan kertas) | `dupa` | — |
| `rumah_petani` | rumah tani | jagung kering tergantung (hay block di dinding), keranjang (barrel), cangkul bersandar | `kasur`, `meja_makan`, `pajangan:keranjang` | — |
| `rumah_nelayan` | rumah nelayan | jala (chain + string), dayung, guci ikan asin | `kasur`, `pajangan:jala` | — |
| `rumah_sarjana` | rumah sarjana | ruang baca, layar lipat, pot bonsai, kolam ikan halaman | `kerja:kuas`, `kursi` | — |
| `rumah_kaya` | rumah saudagar | dinding bayangan (*yingbi*), halaman dalam, kolam, paviliun kecil | `kursi`, `catur` | — |
| `gudang` | gudang | barrel, peti kayu (chest), karung (hay/wool), timbangan besar | `gudang`, `lumbung` | — |
| `balai_kota` | balai desa/kota | kursi pejabat, meja panjang, papan pengumuman | `sidang`, `rekrut`, `kerja:konter` | — |
| `kuil_kecil` | kuil kota | altar dewa bumi, pembakar dupa, lonceng | `dupa`, `kerja:sapu` | — |

Generator juga menambah titik baru (42.12.1): `sumur`, `catur`, `gantung_sangkar`, `pancing`, `jongkok`, `kucing_tidur`,
`tambat`, `cerobong`, `lentera_gantung`, `panji`, `ronda_<n>`, `pasar_pagi_<n>`, `pasar_hantu_<n>`, `lapak_sewa_<n>`, `nisan_<n>`,
`lumbung`, `panggung`, `kavling` (42.9.4), `dijual` (rumah dijual).

#### 42.9.2 Lentera menyala malam dan asap cerobong (klien, 42V)

* **Lentera kota** (`LenteraKota`): model lentera kertas (42.12.4) digambar di titik `lentera_gantung` (atap, pintu toko, jembatan,
  tiang jalan; 20-60 per kota, ibu kota ±400) yang dikirim **sekali** saat potongan termuat (bagian payload titik 42.13.2).
  Siang padam (tekstur kertas), **18:00 menyala** bergelombang dari gerbang ke dalam (penyala lentera: warga peran `PELAYAN`/
  `KULI` dengan galah, klip `nyalakan_lentera`, terlihat bila tubuhnya ada; tanpa tubuh lentera menyala berurutan 2-6 s),
  06:00 padam. Cahaya = quad pancar + halo + bercak cahaya di tanah (decal radial aditif); **tidak** mengubah tingkat cahaya
  blok. Warna mengikuti suasana: merah (biasa), putih (rumah duka), hijau (Pasar Hantu), biru (Malam Lentera Air 38.T),
  warna sekte (cabang/pakta). Kualitas: Minimal 0 halo/16 lentera, Normal 128, Maksimal 512 dalam 64 blok.
* **Asap cerobong** (`AsapDapur`): titik `cerobong` per dapur rumah/usaha. Jadwal deterministik dari hash rumah + jam (klien
  menghitung sendiri, tanpa jaringan): 06:00-07:00, 11:30-12:30, 17:30-19:00 untuk rumah; usaha makanan sepanjang buka; pandai
  menyemburkan percik saat `palu_landasan`; musim dingin asap 2× tebal; rumah duka hari pertama tanpa asap. Partikel sprite
  `asap_dapur` (8 bingkai, 42.12.4), naik 0,04 blok/tick, condong menurut arah angin klien (hash hari). Minimal 1 partikel/2 s per
  cerobong ≤ 32 blok, Maksimal 4/s ≤ 96 blok.

#### 42.9.3 Panji, bendera dan spanduk yang berubah (klien, 42V)

Kain `KainKota` (38.O.3, gelombang verteks) di titik `panji` (gerbang, menara, tiang jalan), bendera arak di kedai arak,
spanduk melintang di jalan utama (titik `spanduk_a/_b` berpasangan), dan kertas di pintu rumah. Isi dipilih dari
`kota_suasana` (42.13.2) per tempat dan per rumah:

| Keadaan | Panji gerbang/jalan | Rumah | Glif piksel (16 × 16, digambar tangan di generator) |
|---|---|---|---|
| biasa | warna distrik/kota (token `TempatWarna`) | — | nama toko di papan |
| festival (38/42) | merah-emas, spanduk festival | — | 福, 寿 |
| Tahun Baru | merah, lentera besar | kuplet merah di kusen, 福 **terbalik** di pintu | 福, 春 |
| pernikahan | — | 囍 di pintu dan jendela rumah pengantin | 囍 |
| pemakaman | — | kain putih, 奠 di pintu | 奠 |
| Qingming | — | ranting dedalu di pintu (prop) | — |
| panen | emas-jerami | — | 丰 |
| perang/serangan | hitam-merah, "awas" | — | 戒 |
| dilindungi sekte pemain | **lambang sekte pemain** (atlas `Lambang`) | — | lambang |
| Pasar Hantu (malam) | — | lentera hijau di lokasi | — |
| kedai arak | bendera arak segitiga | — | 酒 |

#### 42.9.4 Kota tumbuh (`Kavling`, `Kemakmuran`, `PekerjaanTumbuh`; 42F + 42W)

* **Kavling** (dari worldgen, chunk baru): tanah rata berpagar rendah dengan patok dan papan "Kavling Kota" (billboard klien
  menampilkan rencana: "Rumah Toko · tahap 0/5"). Jumlah: ibu kota 24 (hunian barat, pasar timur, di luar tembok selatan),
  kota tema 8-12, kota 34 6 (elemen pool baru `kota/kavling_*` di jigsaw kota), kampung `desa_besar` 4, `desa` 2. Titik `kavling`
  membawa data `jenis` (rencana bangunan) dan `gaya`.
* **Rencana bangunan** per (gaya, jenis): `rumah_kecil`, `rumah_besar`, `rumah_toko`, `kedai_teh`, `kedai_arak`, `bengkel`,
  `gudang`, `kuil_kecil`, `sekolah`, `menara_jaga`, `taman` × gaya `kota_kultivator`, `ibukota_<distrik>` (5), `pelabuhan`,
  `oasis`, `air`, `gunung`, `kampung` = ±110 templat, masing-masing **5 tahap kumulatif** NBT
  `data/kultivator/structure/kota_tumbuh/<gaya>/<jenis>/tahap_<1-5>.nbt` dibuat `tools/gen_kota_tumbuh.py` dari kit yang sama
  dengan worldgen (bentuk serasi). Tahap (*Yingzao Fashi*): 1 **pondasi** (alas batu, lantai), 2 **rangka** (tiang, balok), 3
  **dinding** (dinding, jendela kertas/kaca, pintu), 4 **atap** (dougong, genteng, bubungan), 5 **isi** (interior kit, lentera,
  papan nama, titik).
* **Kemakmuran** `K` 0-100 per tempat (Hari Warga, mingguan dirata): `K = 0,25 × (50 + suasana rata/2) + 0,25 × ekonomi (laba
  usaha + pajak bersih Kas Kota, dinormalkan per jenis tempat) + 0,15 × keamanan (100 − serangan/pencurian; pakta +10) + 0,15 ×
  dagang (kafilah & kontrak tiba per minggu) + 0,1 × kesehatan + 0,1 × (pajak rendah)`.
* **Pemicu**: `K ≥ kotaTumbuh.ambang` 55 **dan** (kepadatan ≥ 1,0 **atau** ≥ 3 penganggur **atau** anak ≥ 8 tanpa sekolah)
  **dan** Kas Kota ≥ biaya tahap 1 **dan** tidak ada pembangunan kembali berjalan. Satu kavling dibangun sekaligus per tempat
  (ibu kota 2). Pilihan rencana: Wakil/Wali Kota pemain memilih (layar Kavling); selain itu otomatis menurut kebutuhan (sesak →
  rumah, penganggur → usaha, anak → sekolah, serangan sering → menara jaga).
* **Bahan**: tiap tahap butuh satuan bahan dari palet blok templat: `kayu` (log/papan/tangga/pagar kayu: 1 per 4 blok), `batu`
  (batu, bata, terakota: 1 per 4), `genteng` (blok atap: 1 per 2), `kaca_kertas` (kaca/panel/wol putih jendela: 1 per 2),
  `kain` (wol/karpet: 1 per 4). Sumber: stok bahan kota (produksi KULI/TUKANG harian), **Pesanan Kota** (42.6.5) yang dipenuhi
  kafilah (gerobak `KafilahJalan` bermuatan kayu/batu terlihat menuju lot), **pemain** menjual/menyumbang barang blok nyata di
  layar Kavling (dibayar `HargaKota × 1,1` atau disumbangkan → Nama Baik +1/32), dan uang Kas Kota (membeli dari kafilah).
* **Pengerjaan**: Mandor Gong Shu (tokoh 42.10, di ibu kota; di tempat lain mandor 38.R) + regu tukang 38.R (tubuh, prop,
  klip `perbaiki_*`, `angkat_papan`) memakai mesin regu yang sama lewat API aditif baru `PerbaikanApi.kerjakan(Pekerjaan p)`
  (42F menambah; `PekerjaanTumbuh` mengisi operasi). **Penerapan blok satu-satunya**: `WorldBreaker.submitRestore(new
  RestoreRequest(dim, posisi, target, expected = keadaan kavling yang terekam di Cetak, null, "tumbuh", done))` — blok hanya
  ditaruh bila dunia masih memegang keadaan kavling kosong atau udara; posisi yang berisi blok pemain **gagal** dan tidak pernah
  ditimpa (mandor menegur lewat dialog, kavling "terhalang" sampai pemain membersihkannya). Laju sama dengan perbaikan (1 blok
  per tukang per 5 s; regu 3 ±36 blok/menit), tunduk `perbaikan.blokPerTick` dan topi WorldBreaker. Rumah kecil ±600 blok ≈ 1-3
  hari dunia termasuk menunggu bahan. Perancah gambar klien (`tools/gen_tempat_perancah.py` yang ada) di tahap 2-4.
* **Kecocokan**: sebelum tiap tahap pemeriksaan struktur asing, medan dan klaim 42.12.1A; gagal = kavling `TIDAK_LAYAK` permanen.
* **Selesai**: `Cetak.terima` untuk kotak kavling (cetak biru menerima bangunan baru, jadi 38.R merawatnya), titik templat menjadi
  titik hidup tempat (`TempatHuni.addSumberTitik`), rumah tangga pindah masuk (pengantin baru, rumah sesak, pendatang) atau usaha
  baru dibuka (pemilik warga, investor pemain). **Upacara peresmian**: barongsai (Rombongan Bulan Sabit bila di kota atau
  penari warga), petasan klien, pita merah dipotong Wali Kota (pemain bila pejabat/pembiaya), gosip "Kota tumbuh". Papan kavling
  menjadi papan nama + "Dibangun dengan bantuan <pemain>" bila pemain menyumbang ≥ 25 % biaya.
* **Batas**: kavling terbatas (direncanakan worldgen). Semua kavling terbangun → "Kota Penuh" (kemakmuran tinggi menaikkan
  kualitas hidup, bukan bangunan); tidak ada penambahan bangunan di luar kavling (aturan blok).

#### 42.9.5 Rusak dan dibangun kembali (`BangunKembali`; 42F di atas 38.R)

* **Hanya kerusakan nyata** (42.0 butir 3): gugus 38.R yang teratribusi. Bila kerusakan satu tempat > 200 blok dalam satu hari
  dunia **atau** satu rumah rusak > 30 % volumenya, gugus-gugus itu digabung menjadi **Proyek Bangun Kembali**:
  * rumah tangga yang rumahnya rusak menjadi **pengungsi**: tidur di **tenda pengungsi** (entitas `tenda_pengungsi` di titik
    lapang/alun, 2-8 tenda), makan di dapur umum (warung sup), suasana −20;
  * mandor menempel "Mohon Bantuan" (38.R.6) dengan kemajuan proyek; Pesanan Kota untuk bahan (42.6.5); **tukang pendatang**
    dari kota tetangga (+1 regu, tubuh berpakaian kota asal) bila proyek > 500 blok;
  * pemain: donasi (38.R.6, laju ×2), antar bahan, Bantu Tukang; pejabat pemain mengalihkan Kas Kota (laju ×1,5);
  * murid pemain (`KERJA_KOTA`) ikut sebagai tukang (laju +1 regu);
  * selesai: upacara syukur (dupa, petasan klien), pengungsi pulang, opsional **Tugu Kenangan** (prasasti gambar klien di titik
    alun dengan nama 5 penyumbang terbesar dan tanggal; tanpa blok).
* Tagihan ganti rugi 38.R.4 tetap untuk kerusakan berpelaku pemain; takut 38.R.5 tetap.
* **Perang dan serangan** (Serbuan Kota 42.8.2, perang sekte NPC di sekitar kota, gelombang begal) **tidak** mengubah blok:
  akibatnya blokade (produksi ×0,5, kafilah berhenti), suasana, warga luka (`PendudukData.luka`, tidak mati), pengungsi
  keluarga dari rumah di luar tembok, asap hitam di cakrawala (klien) dan, bila K7 aktif, **bekas jelaga** gambar klien di
  dinding dekat titik serangan yang digosok warga (`gosok_dinding`) dan memudar dalam 3 hari.

#### 42.9.6 Distrik malam (`DistrikMalam`; 42D, 42W, 42V)

* **Tempat**: ibu kota (perbatasan Pasar Timur-Kumuh: lot baru `kedai_arak_besar`, `rumah_judi`, `teater_bayang`,
  `rumah_teh_malam`), tiap kota tema satu lot `jalan_malam`, kota 34 elemen pool `kota/kedai_arak` (menggantikan satu rumah),
  pos dagang kedai arak kecil.
* **Jam** 18:00-02:00: lentera merah rapat (42.9.2), musik pipa/erhu (melodi klien), warga berkebiasaan `pemabuk`, `penjudi`,
  `penggemar_opera` + 20 % dewasa muda datang; penjaga lebih sedikit; copet ×1,5, perkelahian ×2.
* **Kedai arak**: minum (`minum_arak`), bernyanyi (`nyanyi_mabuk`), **suit jari** (*huaquan*, dua tubuh tersinkron `huaquan_a/b`
  dengan teriakan angka), penari kipas; pemain membeli arak (`Hidangan` Arak Bunga Persik) dan bisa bersuit dengan warga
  (taruhan kecil EV 0,9).
* **Rumah judi** (`meja_dadu` entitas di titik): layar `guanpu` (42.11): **Besar-Kecil** (tiga dadu di cawan: besar/kecil bayar
  0,85:1 dan kalah bila tiga kembar → peluang menang 105/216, EV 0,90; tiga kembar tertentu bayar 1:150 → EV 0,70) dan **Guanpu** (lempar untuk barang: bayar 1/8
  harga barang per lemparan, menang bila jumlah ≥ 15 → peluang 9,3 %, EV ≈ 0,74 nilai barang; dibatasi 20 lemparan/hari).
  Rentenir menagih penjudi warga (alur cerita), pemain bisa melunasi (permintaan `rentenir`).
* **Teater bayang**: entitas `layar_bayang` (layar kain bercahaya dari belakang + bingkai kayu) di lot teater/kedai teh;
  pertunjukan 19:00 dan 21:00 (3-4 menit) dari 4 naskah (Kera Sakti, Ular Putih, Hua Mulan, Hakim Bao): siluet wayang (sprite
  klien pada quad) bergerak menurut naskah (`data/kultivator/tempat/wayang/*.json`: kunci posisi/rotasi per tick), gong-genderang-
  erhu prosedural; tiket 2 NB (sink); penonton warga duduk; tonton sampai akhir → buff *Terhibur* + satu lore.
* Di ibu kota distrik ini di dalam tembok, jadi tetap buka setelah genderang `JamMalam`.

---------------------------------------------------------------------------------------------------------------------

### 42.10 Tokoh bernama dan alur cerita (42B data cerita, 42D jadwal tokoh)

Nama tetap di **ibu kota pertama** dan kota tema pertama per tema (pola 38.S.2); kota lain memakai generator nama 42.4.2 dengan
arketipe dan watak yang sama. Tokoh tinggal di slot warga (mereka Warga dengan bendera `tokoh:<id>` dan `abadiCerita` kecuali
disebut), jadi punya keluarga, ingatan dan jadwal seperti warga lain. Didaftarkan ke `Tokoh` 38 (data `tempat/tokoh/*.json`)
sehingga dialog data dan quest 38 bekerja.

| Id | Nama | Peran / tempat | Watak · umur | Suara | Kait |
|---|---|---|---|---|---|
| `lao_shuo` | Lao Shuo (老说), pendongeng | Rombongan, Kedai Teh Agung | bijak · 70 (abadi) | elder | 12 kisah, petunjuk tempat |
| `ah_qin` | Ah Qin (阿琴), pemain erhu buta | pengamen keliling | jujur · 45 | f2 | alur "Lagu di Bawah Jembatan" (mencari lagu almarhum gurunya di tiga kota) |
| `hong_ling` | Nyonya Hong Ling (红绫) | ketua Rombongan Bulan Sabit | ramah · 50 (abadi) | f1 | barongsai peresmian kavling |
| `yan_feiyan` / `yan_feihu` | Yan Feiyan (燕飞燕) dan Yan Feihu (燕飞虎) | akrobat kakak-adik | ramah / sombong · 19 / 17 | f3 / m3 | Feihu jatuh cinta pada warga kota (pindah kota) |
| `hu_lu` | Tabib Keliling Hu Lu (胡卢) | penjual obat | licik · 40 (abadi) | m2 | dibongkar atau bermitra |
| `ma_fu` | Saudagar Ma Fu (马福) | kafilah keliling | serakah · 55 | m2 | barang langka, kontrak |
| `lao_geng` | Lao Geng (老更), penjaga malam | ibu kota, ronda | jujur · 72 (**tidak abadi**) | elder | alur "Lentera Terakhir Lao Geng" |
| `xiao_bang` | Xiao Bang (小梆) | murid penjaga malam | penakut · 16 | m3 | mewarisi gong |
| `bibi_mei` | Bibi Mei (梅婆), mak comblang | Hunian Barat | licik · 58 | f2 | perjodohan, pernikahan |
| `bibi_zhang`, `bibi_li`, `bibi_wang` | Tiga Bibi Sumur | sumur Hunian Barat | ramah / curiga / penakut · 40-50 | f1/f2/f3 | pusat gosip, `pengintip` |
| `kakek_xiang`, `kakek_che` | Kakek Xiang (象) dan Kakek Che (车) | catur di bawah pohon *huai* | bijak / sombong · 74 / 77 (tidak abadi) | elder | kematian salah satunya mengubah kebiasaan yang lain (duduk sendiri menatap papan) |
| `li_zuixian` | Li Zuixian (李醉仙) | penyair pemabuk, Kedai Arak Bulan Mabuk | sombong · 38 | m1 | pantun festival, satu giok teknik HUANG bila ditemani minum 7 malam |
| `shi_xiaoqian` | Shi Xiaoqian "Tikus Seribu Jari" (千手鼠) | bos copet, Kumuh | licik · 26 | m3 | alur "Pasar Hantu dan Seribu Jari" |
| `lentera_hijau` | Tuan Lentera Hijau (绿灯先生) | tuan rumah Pasar Hantu | licik · ? (abadi, wajah berganti) | demon (bisik, efek) | sandi malam |
| `gong_shu` | Mandor Gong Shu (公输) | mandor pembangunan ibu kota | jujur · 60 (abadi) | elder | kavling, *Yingzao Fashi* |
| `shen_wan` | Juragan Shen Wan (沈万) | pialang/saudagar, Rumah Uang | serakah · 52 (abadi) | m2 | investasi, alur "Utang Tiga Generasi", "Pejabat Jujur" |
| `qiu_panitera` | Panitera Qiu (秋) | juru pajak, Yamen | jujur · 44 | m1 | jalur jabatan |
| `nenek_tang` | Nenek Tang (汤婆婆) | warung sup musim dingin | ramah · 81 (tidak abadi) | f2 (pitch rendah) | alur "Sup Hangat" |
| `paman_bai` | Paman Bai (白叔) | pengurus upacara duka | bijak · 63 | elder | pemakaman |
| `guru_zhou` | Tuan Guru Zhou Shu (周书) | guru sekolah | bijak · 48 | m2 | barisan anak, anak berbakat |
| `fang_gudang` | Nyonya Fang (方) | kepala gudang | curiga · 46 | f1 | gudang, kontrak |
| `gao_senior` | Kakak Senior Gao (高) | perekrut sekte NPC terkuat beraliran Lurus | sombong · 24 | m1 | saingan rekrut anak berbakat |
| `xiao_yu` | Xiao Yu (小雨) | anak Kumuh berakar **HEAVENLY** | ramah · 9 | f1 | alur "Anak Akar Langit" |
| `lao_hua` | Lao Hua (老花), kucing | pasar ikan ibu kota | — | (suara kucing) | maskot, permintaan `kucing_hilang` |
| `zhang_shan`, `li_ying` | Zhang Shan (张山) dan Li Ying (李莺) | anak keluarga tahu dan keluarga arak | jujur / ramah · 20 / 19 | m3 / f1 | alur "Dua Keluarga di Jalan Sumur" |

**Kisah Lao Shuo** (12; teks 6-10 baris per kisah di `kota_hidup_cerita`, ringkasan sendiri, tidak menyalin terjemahan): Kera
Batu Mengacau Istana Langit (*Journey to the West*), Ular Putih di Danau Barat (legenda Ular Putih), Penggembala dan Penenun
(Niulang-Zhinü), Chang'e Terbang ke Bulan, Mulan Menggantikan Ayahnya (*Balada Mulan*), Hakim Bao dan Tiga Pisau Pemenggal
(kisah Hakim Bao), Delapan Dewa Menyeberangi Laut, Yu Gong Memindahkan Gunung (*Liezi*), Sarjana dan Rubah (*Liaozhai Zhiyi*, Pu
Songling), Sepasang Kupu-kupu (Liang-Zhu), Penjara Iblis di Bawah Gunung (lore mod 34), Sekte yang Turun dari Awan (lore mod 37).

**Alur cerita** (rantai quest data `data/kultivator/tempat/quest/kota42_*.json`, mesin 38.S; tujuan baru 42B: `bicara_warga`,
`gosip_telusur`, `hadir_upacara`, `bayar_utang`, `perkara`, `kavling_tahap`, `kejar_copet`, `ronda_geng`):

| Alur | Tempat · tokoh | Tahap | Cabang dan akhir | Hadiah |
|---|---|---|---|---|
| **Dua Keluarga di Jalan Sumur** | Hunian Barat · Zhang Shan, Li Ying, Tiga Bibi, Bibi Mei | 1 gosip pertemuan rahasia di sumur; 2 antar surat Li Ying; 3 kedua ayah melarang, Bibi Mei menolak; 4 telusuri asal permusuhan (Pohon Keluarga + arsip: utang batas sumur dua generasi lalu, gosip ditelusur ke sumber); 5 putusan | **A Damai**: buktikan utang sudah lunas (kwitansi di dasar sumur, barang cerita) atau bayar → pernikahan besar dua keluarga, anak pertama mereka diberi nama kehormatan pemain; **B Kawin lari**: kawal keduanya ke Kota Air (pelancong 42.4.8, begal) → menikah di sana, keluarga sedih, berdamai di *zhuazhou* cucu bila pemain membawa kedua kakek; **C Dibiarkan 10 hari**: Li Ying dijodohkan ke keponakan Shen Wan, Zhang Shan menjadi `pemabuk` (kebiasaan tetap), bisa ditebus kemudian | kesan +40 kedua keluarga, `kue_pengantin`, advancement |
| **Lentera Terakhir Lao Geng** | ronda ibu kota · Lao Geng, Xiao Bang | 1 ikut ronda 3 malam; 2 ajari Xiao Bang pola pukulan (HUD irama 5 ketukan, 42.11.9); 3 Lao Geng pensiun; 4 pemakaman Lao Geng (wafat alami ≤ 20 hari kemudian, atau segera bila ≥ 80); 5 Xiao Bang memimpin ronda dengan gong gurunya | — | item `kentongan_tua` (dibunyikan: seruan jaga, hiasan), Nama Baik +5 |
| **Utang Tiga Generasi** | ibu kota · Shen Wan, keluarga Wu (bakmi) | 1 keluarga Wu meminta tolong (`rentenir`); 2 baca surat utang kakek Wu (arsip); 3 bunga melebihi hukum | **A** bayar lunas; **B** bawa ke Hakim Bao (perkara, Shen Wan didenda, kesan Shen Wan −50); **C** tanam modal di bakmi Wu sampai laba melunasi (usaha milik pemain 40 %) | kesan, saham usaha |
| **Pasar Hantu dan Seribu Jari** | Kumuh · Shi Xiaoqian, Lentera Hijau | 1 dicopet/permintaan korban; 2 telusuri gosip ke Pasar Hantu; 3 sandi malam, temui Lentera Hijau; 4 sarang copet (anak-anak jalanan) | **A** serahkan ke Yamen (anak-anak ke kuil; satu anak berakar muncul di Kantor Rekrutmen); **B** jadikan informan (copet kota itu berhenti mengincar pemain, lokasi Pasar Hantu tiap malam); **C** bantu insaf: modali toko kunci (usaha) | topeng, saham, calon murid |
| **Anak Akar Langit** | Kumuh · Xiao Yu, ayahnya (penjudi), ibunya (pelukis gula), Kakak Senior Gao | 1 gosip "daun melayang"; 2 Gao menawar uang ke ayah; 3 lunasi utang judi ayah / bujuk ibu / kalahkan Gao dalam tanding ramah; 4 *zhuazhou* kenangan (ibu bercerita), Hari Ujian Terbuka | **A** ke sekte pemain (calon akar HEAVENLY); **B** ke sekte Gao (kelak muncul di Turnamen Agung sebagai saingan bernama roster 39F); **C** tinggal (dewasa menjadi tabib kota) | calon murid langka |
| **Kota yang Tumbuh** | ibu kota · Mandor Gong Shu | 1 kavling pertama, kekurangan kayu; 2 kawal kafilah kayu (begal); 3 kumpulkan 64 genteng; 4 peresmian | — | jalan diberi nama pemain (papan nama jalan billboard di titik `nama_jalan`), Nama Baik +10 |
| **Musim Dingin Sup Hangat** | warung Nenek Tang | 1 sup habis di hari Laba; 2 kumpulkan 8 bahan dari 4 toko/2 kota; 3 bagikan bubur di kuil (klip `bagi_bubur`); 4 Nenek Tang wafat musim dingin berikutnya → cucunya meneruskan warung | — | resep: `sup_hangat` lebih murah selamanya, kesehatan kota + |
| **Pelindung Baru** | kota 34 terdekat dengan pelindung sekte NPC yang lalai | 1 tetua mengeluh begal; 2 bersihkan kemah begal (38 `pos/kemah_begal`); 3 utusan sekte lama menantang; 4 Serbuan Kota pertama; 5 upacara menaikkan panji | — | pakta, renown |
| **Malam Wayang** | teater bayang ibu kota | 1 wayang dicuri; 2 telusuri ke Pasar Hantu; 3 tebus atau rebut; 4 pertunjukan perdana (pemain menabuh gong, HUD irama) | — | tiket gratis selamanya, lore |
| **Pejabat Jujur** | Yamen · Panitera Qiu, Shen Wan, Hakim Bao | 1-3 jabatan 1-3 (perkara); 4 paceklik: harga PANGAN 1,5, Shen Wan menimbun; 5 perkara besar | **A** sita timbunan ke Lumbung (harga turun, Wali Kota); **B** terima suap (Infami +10, audit) | jabatan 5 |

---------------------------------------------------------------------------------------------------------------------

### 42.11 Antarmuka (modul 42U; kit "Pasar Giok" 38.U, 320 × 240; ⟦ ⟧ = tablet kiri, [ ] = tombol)

Server membangun lembar (`TempatLayar.buka`, ≤ 32 KB) dan mendaftar penangan (`TempatLayar.daftar`); klien menggambar
(`UiTempatClient.daftar(layar, Pembuat)`), kelas di `client/dunia/kota42/ui/layar/**`, kit dipakai baca-saja. Kunci lembar di
`dunia/kota42/ui/KotaLembar.java` (javadoc per layar, pola `LembarKunci`). Panel: `kertas` (warga, kota), `kayu` (toko,
gudang, pasar), `batu` (jabatan, perkara), `kain` (upacara, rumah), `giok` (sekte); Pasar Hantu memakai `kain` gelap dengan
token warna hijau `#3FA86A` baru di `TempatWarna` (42U menambah satu konstanta, aditif). Keyboard penuh, skala GUI 2-4.

**Hub**: `HubCards.register(HubNav.DUNIA, "kota_buku", …)` "Buku Kota" (kota terdekat/dikenal), `HubCards.register(HubNav.DIRI,
"kota_milikku", …)` "Rumah & Usahaku" (rumah, toko, investasi, gudang, kontrak, jabatan), `HubCards.register(HubNav.SEKTE,
"kota_sekte", …)` "Sekte di Kota" (pakta, kantor, cabang, murid bertugas; terkunci tanpa aula). Topik Ensiklopedia `warga`,
`ekonomi_kota`, `kehidupan_kota`, `sekte_kota`, `kota_tumbuh`.

#### 42.11.1 Dialog warga (layar `dialog` 38.U.3.1 yang diperluas; tablet baru)

```
+----------------------------------------------------------------------+
|⟦Bicara ⟧| ┌──────┐ Zhang Ming'an · 34 · Koki Rumah Makan Teratai ☺   |
|⟦Kabar  ⟧| │potret│ ♥ Akrab (kesan 42) · tahu namamu                    |
|⟦Jalan  ⟧| │ 48px │ "Pagi, Tuan Han! Adonan bakpao sudah naik, nanti    |
|⟦Hubungan│ └──────┘  mampir ya. Istriku titip salam."                  |
|⟦Tolong ⟧|  1 [Ceritakan keluargamu]   2 [Bagaimana dagangan?]          |
|⟦Beri   ⟧|  3 [Apa cita-citamu?]       4 [Tentang sekteku]              |
|⟦Jasa   ⟧|  5 [Pamit]                                    (Esc) tutup    |
+----------------------------------------------------------------------+
```
Ikon suasana (☺ senang, ☁ sedih, ! takut, ✕ marah, ≈ mabuk, + sakit) di samping nama; hati berwarna menurut kesan. Tablet
Jalan = 42.5.5 (tiga daftar + `[Tandai]`), Kabar = 1-3 gosip + `[Tanya siapa yang bilang]`, Tolong = permintaan aktif warga ini,
Beri = slot tangan + petunjuk selera ("Sepertinya dia suka teh"), Jasa = layanan (termasuk layar lama 34).

#### 42.11.2 Kartu Warga (`warga_kartu`, tablet Hubungan)

```
+----------------------------------------------------------------------+
|⟦Diri   ⟧| [Pratinjau3D tubuh]  Zhang Ming'an 张明安 · laki-laki · 34   |
|⟦Keluarga| Koki (ahli 61) · Rumah Makan Teratai · watak jujur          |
|⟦Teman  ⟧| Kebiasaan: pecinta teh · penyapu jalan    Suasana ███▒ +44  |
|⟦Ingatan⟧| Lahir hari 3 di sini · akar: — · zhuazhou: sempoa            |
|⟦Pohon  ⟧|--------------------------------------------------------------|
|        | Tentang kamu: kenal 23× · kesan +42 · utang: dia −0 / kamu 0  |
|        | Kenangan: ✦ Diselamatkan dari api (hari 41) · Diberi teh     |
|        | [Beri] [Pekerjakan🔒 bekerja] [Tanam modal] [Pinjamkan uang] |
+----------------------------------------------------------------------+
```
Tablet **Keluarga** (pasangan, anak, orang tua, saudara; masing-masing kartu kecil dengan status hidup/arsip/pindah/sekte),
**Teman** (sahabat, kekasih, musuh, saingan dengan bilah kekuatan; ikon "di mana sekarang"), **Ingatan** (semua kenangan
tentang pemain), **Pohon** (42.11.3). Tombol muncul menurut keadaan (anak berakar: `[Ajak ke sekte]`).

#### 42.11.3 Pohon Keluarga (`warga_pohon`)

```
+----------------------------------------------------------------------+
|        Zhang Deshun ✝(h.38)──Wu Lan (71)                            |
|                 │                                                    |
|   Zhang Mingyu (40, Kota Air) ─┬─ Zhang Ming'an (34) ══ Liu Cuifen   |
|                                │          │                          |
|                         ┌──────┴──────┐                              |
|                Zhang Wenbao (9, akar MID ✦)   Zhang Wenxiu (4)        |
|  [◀ naik generasi]  [turun ▶]  [Makam Deshun: Bukit Barat #7]       |
+----------------------------------------------------------------------+
```
Simpul = potret kecil (kepala Sosok) dengan cincin warna (hidup putih, arsip abu ✝, pindah biru, sekte emas); klik =
Kartu Warga atau ringkasan arsip; seret untuk menggeser, 3 generasi terlihat.

#### 42.11.4 Buku Kota (`kota_buku`) — hub DUNIA dan papan balai

```
+----------------------------------------------------------------------+
|⟦Ringkas⟧| Kota Teratai Hijau (kota 34) · Musim Gugur hari 6 · Panen  |
|⟦Warga  ⟧| Penduduk 47/52 kasur · lahir 3 · wafat 1 · pindah +2/−1    |
|⟦Acara  ⟧| Kemakmuran ███████▒▒▒ 68 ▲  Suasana +18  Pajak 8 %          |
|⟦Harga  ⟧| Pelindung: Sekte Awan Ungu (kamu) · upeti siap ✦            |
|⟦Kavling⟧| Kas Kota 1.240 NB · Wali Kota: Lin Bo · Jabatanmu: Pengawas |
|⟦Kabar  ⟧| Besok: Pernikahan Zhang Shan & Li Ying (09:00, Jalan Sumur) |
|        | Malam ini: Pasar Hantu ? (beli sandi di kedai teh)          |
+----------------------------------------------------------------------+
```
Tablet **Warga** (`DaftarGulir` + `KotakCari`: semua warga hidup/arsip, filter peran/umur/berakar/kenal, klik → Kartu),
**Acara** (kalender 14 hari: upacara, rombongan, hari raya, ronda, Hari Ujian; `CincinWaktu`), **Harga** (12 kategori dengan
faktor, tren 7 hari, sebab), **Kavling** (42.11.13), **Kabar** (gosip yang sudah didengar pemain di kota itu, dengan siapa yang
memberi tahu).

#### 42.11.5 Rumah (`rumah`)

```
+----------------------------------------------------------------------+
|⟦Dijual ⟧| Rumah Sedang · Jalan Lentera No. 4 · 3 kasur · 9 × 7 blok  |
|⟦Sewa   ⟧| ╭──────────╮  Harga 1.080 NB (×1,0 kota, ×1,2 kemakmuran) |
|⟦Milikku⟧| │ denah    │  Sewa 32 NB / 7 hari · Pajak 11 NB / 7 hari   |
|⟦Pembantu| │ mini     │  Tetangga: Zhang (akrab), Wu (asing)          |
|        | ╰──────────╯  Syarat: ✓ Nama Baik 34 ≥ 20  ✓ bukan buronan  |
|        | [Beli 1.080 NB]  [Sewa 1 mgg] [Sewa 4 mgg]  [Tunjuk]        |
+----------------------------------------------------------------------+
```
Denah mini = proyeksi atas kotak rumah (warna lantai/dinding dari rekaman Cetak, tanpa membaca chunk klien). Tablet
**Milikku**: daftar rumah, sisa sewa, `[Kunci rumah]` (cetak ulang), `[Jual 50 %]`, status segel; **Pembantu**: warga
dipekerjakan.

#### 42.11.6 Toko Pemain (`toko_pemain`)

```
+----------------------------------------------------------------------+
|⟦Rak    ⟧| Toko "Pil Seribu Embun" · reputasi ████▒ 72 · buka (pegawai)|
|⟦Harga  ⟧|-----------------------------+--------------------------------|
|⟦Pegawai⟧| Rak 1 ▣ Pil Penyembuh ×12   |  Pil Penyembuh                 |
|⟦Laci   ⟧|        14 NB (h 1,08) ~50 % |  Nilai 13 · kota ×1,04 ▲       |
|⟦Laporan⟧| Rak 2 ▣ Jimat Api ×4  🔥    |  Laku perkiraan: 50 %/jam ramai|
|        | Rak 3 ▢ kosong              |  [−] 14 NB [+]  [Harga pasar]  |
|        |  Hari ini 186/360 NB batas  |  Pajak 5 % · laci 412 NB       |
+----------------------------------------------------------------------+
```
Tablet **Pegawai** (warga/murid, watak, ahli, gaji), **Laci** (ambil hasil, atau otomatis ke Rumah Uang), **Laporan** (7 hari:
pembeli, laku per barang, grafik).

#### 42.11.7 Investasi (`investasi`)

```
+----------------------------------------------------------------------+
|⟦Portofolio| Total ditanam 1.900 NB · nilai 2.044 ▲ · minggu ini +61  |
|⟦Peluang⟧|------------------------------------------------------------|
|⟦Tarik  ⟧| Bakmi Wu (ibu kota)   35 % · 800→871 · laba 7 hr +28  ▁▃▅▆ |
|        | Kedai Teh Embun (Kota Air) 20 % · 600→615 · +9           ▃▂▅▄|
|        | Bengkel Pandai Lin (Teratai) 12 % · 500→558 · +24  ⚠ pemilik|
|        |   sakit (ahli waris: Lin Hu, magang)                        |
|        | [Tambah] [Tarik (7 hari)] [Kirim murid pengelola]           |
+----------------------------------------------------------------------+
```

#### 42.11.8 Gudang dan Kontrak (`gudang`)

```
+----------------------------------------------------------------------+
|⟦Gudang ⟧| Gudang Pelabuhan Timur · 54 slot (sewa s.d. hari 61)       |
|⟦Kirim  ⟧| ▣▣▣▣▣▣▣▣▣ ▣▣▣▣▢▢▢▢▢ ▢▢▢▢▢▢▢▢▢ ...  (seret dari tas)       |
|⟦Kontrak⟧|------------------------------------------------------------|
|⟦Pesanan⟧| Kontrak II: 64 Teh Awan → Ibu Kota tiap 7 hari (2/4)        |
|        |   stok 40/64 ✗ · berangkat besok · harga 3,2 · denda 15 %   |
|        | Nama Dagang 240 (Tingkat II) · kafilah berjalan: 1 (◷ 1 hari)|
|        | [Ambil kontrak] [Kirim antar gudang] [Kawal kafilah]        |
+----------------------------------------------------------------------+
```

#### 42.11.9 Jabatan dan Perkara (`jabatan`, `perkara`)

```
+----------------------------------------------------------------------+
|⟦Jabatan⟧| Hakim Pembantu (3) · Kota Teratai · gaji 7 hr: 46 NB        |
|⟦Pajak  ⟧| Tugas minggu ini: perkara 1/2 · hadir ✓ (hari 52)            |
|⟦Perkara⟧| Berikut: Wakil Wali Kota — ✓NB 80 ✗Inti Emas ✗Banjir Semi   |
|⟦Kavling⟧|------------------------------------------------------------|
|        | Perkara: "Ayam Bibi Wang hilang" · saksi 2 · bukti 1         |
|        | [Panggil saksi] [Telusuri gosip] [Putuskan]                 |
+----------------------------------------------------------------------+
```
Layar `perkara`: dua pihak berhadapan (potret), saksi (gelembung kesaksian), bukti (kartu barang/gosip dengan rantai penyebar),
tiga putusan dengan perkiraan akibat; amplop suap muncul dari pihak licik (tombol `[Terima amplop]` berwarna bahaya). HUD irama
ronda/wayang (42.10) = cincin 5 ketukan dengan penanda tekan (bukan layar penuh).

#### 42.11.10 Pasar Hantu dan Guanpu (`pasar_hantu`, `guanpu`)

```
+----------------------------------------------------------------------+
|⟦Lapak  ⟧| ☾ Pasar Hantu · Bawah Jembatan Batu · bubar 04:12 ◷         |
|⟦Curian ⟧| ▣ Gulungan Peta Rawa  140 NB     "Barang bagus...           |
|⟦Penadah⟧| ▣ Minyak Bayangan ×4   88 NB      untuk yang tahu diam."    |
|        | ▣ ★ Kantong batumu (dicuri h.50)  tebus 24 NB                |
|        | Topeng: ✓ (−10 %, aman razia)   Razia: tenang ▁              |
+----------------------------------------------------------------------+
```
`guanpu`: meja hijau, cawan bambu `Pratinjau3D` yang dikocok (animasi 20 tick), tiga dadu jatuh dan berguling (fisika klien
sederhana), papan taruhan Besar/Kecil/Kembar, riwayat 10 lemparan, batas harian; tab **Guanpu** (barang di meja pedagang, harga
per lempar, peluang menang tertulis jujur).

#### 42.11.11 Kantor Rekrutmen (`kantor_rekrut`)

```
+----------------------------------------------------------------------+
|⟦Pelamar⟧| Kantor Sekte Awan Ungu · Kota Teratai · sewa s.d. hari 58  |
|⟦Ujian  ⟧|-----------------------------+--------------------------------|
|⟦Kantor ⟧| ✦ Zhang Wenbao · 12 · MID?  |  [potret 3D] Zhang Wenbao      |
|        |   Lin Xiaohua · 15 · LOW    |  ayah: Zhang Ming'an (akrab)   |
|        |   Wu Tie · 22 · TRASH       |  zhuazhou: sempoa · watak jujur|
|        |                             |  Keluarga: RAGU · restu 60 NB  |
|        |  Diterima minggu ini 1/3    |  [Uji akar 2 NB] [Terima] [Tolak]|
+----------------------------------------------------------------------+
```
Tablet **Ujian** (Hari Ujian Terbuka: jadwal, `[Adakan 20 NB]`), **Kantor** (penjaga, panji, gosip sekte, statistik).

#### 42.11.12 Pakta Perlindungan dan Cabang (`pakta`, `cabang`)

```
+----------------------------------------------------------------------+
|⟦Pakta  ⟧| Kota Teratai Hijau dilindungi Sekte Awan Ungu (hari 33)    |
|⟦Bela   ⟧| Upeti 7 hr: 120 NB + 16 beras ✦ siap   Renown +5/mgg        |
|⟦Murid  ⟧| Penjaga: Su Mei (Fondasi), Gu Tian (Fondasi) · rotasi 2 hr |
|⟦Cabang ⟧| Panggilan Bela Kota: tenang · terakhir menang (hari 45)    |
|        | Gagal berturut 0/3                                          |
|        | [Ambil upeti] [Tugaskan murid] [Putus pakta]                |
+----------------------------------------------------------------------+
```
`cabang`: jenis (Toko Pil / Balai Latihan / Klinik) dengan centang isi wajib (rak 4/4 ✓, kasir ✓ …), penjaga, efek, laporan.

#### 42.11.13 Kavling (`kavling`)

```
+----------------------------------------------------------------------+
|⟦Kavling⟧| Kavling 3 · Pasar Timur · Rumah Toko (gaya ibu kota pasar) |
|⟦Bahan  ⟧| Tahap 3/5 Dinding ███████▒▒▒  regu 3 · ±1 hari lagi         |
|⟦Pilih  ⟧| Bahan tahap ini: kayu 42/60 · batu 30/30 ✓ · kertas 6/14   |
|⟦Dana   ⟧| [Pratinjau3D bangunan tahap 5 berputar]                     |
|        | Kemakmuran 68 ≥ 55 ✓ · Kas Kota cukup ✓ · terhalang: —       |
|        | [Jual bahan dari tas] [Sumbang NB] [Tunjuk]                 |
+----------------------------------------------------------------------+
```
Tablet **Pilih** (pejabat pangkat ≥ 4: pilih rencana berikut dari daftar kebutuhan), **Dana** (penyumbang, nama di papan).

#### 42.11.14 Layar kecil lain

| Layar | Isi |
|---|---|
| `upacara` | kartu undangan (merah 囍 / putih 奠 / merah telur), waktu, tempat, `[Beri angpao/uang duka]` dengan `PemilihJumlah`, `[Tunjuk]` |
| `rombongan` | jadwal pertunjukan rombongan di kota, `[Beri tip]`, daftar kisah Lao Shuo yang sudah didengar |
| `lapak_sewa` | sewa Lapak Kota (pola 38.U.3.6 tab Lapak) |
| HUD `kejar` | panah ke pencopet, hitung mundur 30 s, jarak |
| HUD `irama` | cincin 5 ketukan (ronda, wayang) |

---------------------------------------------------------------------------------------------------------------------

### 42.12 Konten: worldgen, entitas, blok, item, model, tekstur, animasi, efek, suara

#### 42.12.1 World generation (42W; chunk baru, data-driven; NBT dibuat ulang generator; `cek_tempat.py` dan TplCheck bersih)

Semua potongan ≤ 48 × 48 (batas `tools/nbt.py`), JSON `structure`/`template_pool`/`processor_list` yang ada diperluas, tanpa
jenis struktur baru (tata letak 38W `kultivator:tata_letak` dan jigsaw kota 34 sudah cukup). Kit bersama baru
`tools/kota_hidup_kit.py` (interior 42.9.1, titik, kavling, makam, distrik malam).

| Generator (pemilik saat 42: 42W) | Tambahan |
|---|---|
| `tools/gen_tempat_kota_besar.py` + `tools/kota_besar_kit.py` | lot baru: `kantor_rekrut` (24 × 12 × 18, distrik gilda, 4 bilik), `kedai_arak_besar` (24 × 14 × 20), `rumah_judi` (20 × 12 × 18), `teater_bayang` (24 × 14 × 24), `rumah_teh_malam` (18 × 12 × 16) di perbatasan pasar-kumuh; `gudang_kota` (20 × 10 × 24, gilda); varian lot hunian barat dan pasar timur dengan 2-4 **kavling** (total 24, termasuk 6 di luar tembok selatan); **Bukit Makam Barat** (32 × 14 × 32, di luar tembok barat, 48 titik `nisan_<n>`, gapura, pembakar uang kertas); 8 rumah bertanda `dijual`; interior kit pada semua rumah dan toko; titik 42 di semua lot |
| `tools/gen_dunia_kota.py` (kota 34) | elemen pool `kota/rumah`: `kavling_kecil` (9 × 6 × 9), `kavling_besar` (15 × 6 × 15), `kedai_arak` (15 × 12 × 13), `gudang` (13 × 10 × 13); `tembok_ujung_makam` (potongan ujung tembok + bukit makam kecil 23 × 10 × 22, 16 nisan); semua rumah memakai interior kit; `kantor_sekte` mendapat titik `rekrut`; sumur alun bertitik `sumur`; 2-6 rumah `dijual` (processor penanda) |
| `tools/gen_tempat_kampung.py` | kavling 2 (`desa`) / 4 (`desa_besar`), makam desa (12 × 6 × 12, 12 nisan), kandang ayam (`ayam_kota` titik), `tambat`, sumur bertitik, interior rumah tani/nelayan, titik `rekrut` di balai |
| `tools/gen_tempat_tema_{pelabuhan,oasis,air,gunung,iblis}.py` | lot `jalan_malam` per tema (kecuali iblis: memakai pasar iblis), 8-12 kavling bergaya tema, makam bertema (pelabuhan: tebing makam laut; oasis: makam gurun berkubah; air: pulau makam; gunung: teras makam), `kantor_rekrut` 2 bilik, `lapak_sewa_<n>` 4-6, `pasar_hantu_<n>` 3-5, interior kit |
| `tools/gen_tempat_jalan.py` (pos) | pos dagang: kedai arak kecil, `tambat` ×4, `pasar_hantu_<n>` ×2 |
| `tools/gen_kota_tumbuh.py` (baru, 42W) | ±110 rencana × 5 tahap kumulatif → `data/kultivator/structure/kota_tumbuh/<gaya>/<jenis>/tahap_<1-5>.nbt` (dibaca server lewat `StructureTemplateManager`, tidak pernah ditaruh lewat `/place`) + sidecar `rencana.json` (palet → bahan, titik, ukuran) |

**Titik baru** (penanda `kultivator_titik` di NBT, dibaca `TitikTemplat`; jenis baru didaftarkan `KotaTitik` 42A ke 38NL):

| Titik | Arti | Jumlah khas (ibu kota / kota / kampung) |
|---|---|---|
| `sumur` | pusat gosip, basuh pakaian | 6 / 1-2 / 1 |
| `catur`, `gantung_sangkar`, `pancing`, `jongkok` | kebiasaan 42.4.12 | 12 / 3 / 1 per jenis |
| `kucing_tidur`, `tambat`, `kandang` | hewan 42.7.5 | 40 / 10 / 6 |
| `cerobong` | asap dapur | per dapur |
| `lentera_gantung`, `panji`, `spanduk_a/_b`, `nama_jalan` | visual 42.9.2-42.9.3 | ±400 / 40 / 12 |
| `ronda_<n>` | rute ronda | 16 / 6 / 3 |
| `pasar_pagi_<n>`, `lapak_sewa_<n>` | pasar pagi, lapak kota | 12 / 4 / 2 |
| `pasar_hantu_<n>` | Pasar Hantu | 5 / 3 / — |
| `nisan_<n>` | makam | 48 / 16 / 12 |
| `lumbung`, `panggung`, `rekrut`, `sidang`, `meja_dadu` | layanan 42 | per lot |
| `kavling` (data `jenis`, `gaya`) | kavling 42.9.4 | 24 / 6 / 2-4 |
| `dijual` | rumah dijual | 8 / 2-6 / 0-2 |
| `pajangan:<jenis>` | pajangan klien | per interior |

Anggaran `cek_tempat.py` dinaikkan: kota_besar 3,0 → 3,6 MB, kota 34 +0,3 MB, kampung 1,5 → 1,8 MB, kota_tema 6,2 → 7,2 MB,
kota_tumbuh 2,4 MB baru. Harness: `scratchpad/final/wg/run_38.sh` (kota besar, kampung, pos), `run_all.sh` (kota 34),
PlanCheckTema; generator menulis pratinjau isometrik (`tools/tempat_iso.py`) untuk tiap lot baru dan tiap tahap rencana; 42W
membuka pratinjau dengan Read dan mencatatnya di `docs/progres/kota42-42W.md`.

#### 42.12.1A Kecocokan dengan mod dunia pengguna (tidak ada tabrakan)

Paket pengguna: **Terralith, Tectonic, Structory, Structory Towers, Dungeons and Taverns, Lithostitched, Amplified Nether,
Nullscape, Yeetus Experimentus**. Agen lain sedang memperbaiki tabrakan worldgen yang sudah ada; 42W mulai **setelah** perbaikan
itu di-commit dan memakai hasilnya (tidak menyunting `mixin/ChunkGeneratorTempatMixin`, `TempatKeepOut` atau tag
`#kultivator:boleh_di_tempat`).

| Risiko | Aturan 42 |
|---|---|
| Struktur mod lain lahir di dalam kota/lot 42 | 42 **tidak menambah jenis struktur, structure set atau placement**. Semua lot baru ibu kota dan kota tema ada di dalam rencana `kultivator:tata_letak` yang sudah dilindungi zona keep-out (`TempatKeepOut.tolak`: start `surface_structures` di kotak zona ditolak, kecuali `#kultivator:boleh_di_tempat`). Elemen pool baru kota 34/kampung (kavling, kedai arak, gudang, makam ujung tembok) adalah potongan jigsaw struktur yang sama, jadi mewarisi set, jarak dan `exclusion_zone`-nya. 42 tidak menambah apa pun ke `boleh_di_tempat`. |
| Kavling tumbuh di atas struktur mod lain atau medannya | Sebelum **setiap tahap** (`PekerjaanTumbuh.periksa`): (1) semua posisi target berada di dalam potongan kavling milik kita (`level.structureManager().getStructureWithPieceAt(pos, strukturKita)` di sudut dan pusat kotak, verifikasi nama 26.3); (2) tidak ada start/potongan struktur **bernamespace lain** yang memotong kotak kavling + 2 blok (`structureManager().getAllStructuresAt(pos)` di 9 titik sampel + `StructureStart.getBoundingBox().intersects`, verifikasi); (3) tidak diklaim pemain (`KlaimPemain`); (4) `expected` = rekaman Cetak, jadi blok asing apa pun tidak pernah ditimpa. Gagal (1)/(2) → kavling **dimatikan permanen** (`TIDAK_LAYAK`, tercatat di `uji kota_hidup tumbuh kavling`), tidak pernah dibangun "di sebelahnya". Pemeriksaan sama berlaku bila suatu saat jalur `BuildPlacer` yang dipakai. |
| Medan kasar Tectonic | Generator menandai kavling hanya pada lot yang putusan medannya (`PutusanTanah`/`PutusanTema`, sidecar `lantai`) beda tinggi ≤ 2 dan tanpa air; lot curam mendapat varian taman (bukan kavling). Saat tumbuh: tanah rekaman Cetak di kotak kavling harus dalam ±2 blok dari `lantai` dan tanpa fluida, selain itu `TIDAK_LAYAK`. Tahap 1 hanya menaruh blok di jejak templat; tidak ada perataan tanah di luar jejak. |
| Bioma Terralith | 42 hanya membaca tag bioma mod yang sudah diperluas paket `kultivator:kompat_bioma` (`tools/gen_kompat_bioma.py`); salju musim dingin dibaca dari suhu bioma (`coldEnoughToSnow`, berlaku untuk bioma Terralith); hewan dan rombongan muncul dari titik, tidak dari bioma. Bila 42W menambah gaya kavling per bioma, tagnya ikut didaftarkan ke `gen_kompat_bioma.py` (baris aditif). |
| Bangunan mod lain dipakai sebagai layanan | Tidak pernah: rumah dijual, toko, gudang, kantor rekrutmen, cabang, distrik malam dan kandang hewan hanya di bangunan/titik **milik kita** di dalam kotak tempat (`TempatZones.inside`). Kedai Dungeons and Taverns bukan `kedai_arak` 42. Klaim rumah ditolak bila kotaknya memotong struktur bernamespace lain. |
| Entitas 42 masuk struktur mod lain | Hewan, lapak, tenda, panggung hanya muncul di titik di dalam kotak tempat; pelancong/rombongan berjalan di rute `RencanaJalan` dan tidak berhenti/menginap di struktur asing. |
| Dimensi lain (Amplified Nether, Nullscape) | 42 hanya overworld (`level.dimension() == Level.OVERWORLD` di semua pintu masuk); tidak ada konten Nether/End, tidak ada perubahan medan dimensi. |
| Mixin worldgen (Lithostitched) | 42 menambah **nol** mixin worldgen; satu-satunya kait tetap `ChunkGeneratorTempatMixin` (pemilik 38W/agen perbaikan). Generator NBT memakai blok vanilla saja. |
| Fitur eksperimental (Yeetus Experimentus) | 42 tidak bergantung pada fitur/flag eksperimental; semua blok, item dan ikon advancement diverifikasi berada di registri 26.3 non-eksperimental (42A). |
| Tinggi dan atap kota | Titik pijak selalu dari jangkar/lantai lot (38.0 butir 12), tidak dari heightmap, jadi menara Structory Towers di luar zona tidak memengaruhi tujuan warga. |

Uji: dunia baru dengan paket mod pengguna, `uji kota_hidup kompat periksa [radius]` di 3 ibu kota, 3 kota tema, 5 kota 34, 10 kampung
→ daftar struktur asing yang memotong kotak lot/kavling/makam/kantor (harus kosong) dan kavling `TIDAK_LAYAK` beserta sebabnya;
harness `run_38.sh`/`run_all.sh` bersih.

#### 42.12.2 Entitas baru (semua `noSave`, didaftarkan selalu, muncul hanya saat dipakai; model/tekstur 42V)

| Entitas | Ukuran (blok) | Pemilik server | Perilaku |
|---|---|---|---|
| `kultivator:kucing_kota` | 0,5 × 0,6 | 42D | 42.7.5; AI: berjalan antar titik, lompat, tidur, ikut; `PathfinderMob` ringan |
| `kultivator:anjing_kota` | 0,6 × 0,8 (penjaga besar 0,8 × 1,0) | 42D | ikut anak, jaga pintu, menggonggong |
| `kultivator:ayam_kota` | 0,4 × 0,7 (anak 0,25 × 0,3) | 42D | patuk, kokok, kabur |
| `kultivator:kawanan_merpati` | 2 × 1 (tak berkolisi) | 42D | satu entitas, boid klien 8-16 burung, keadaan `MENDARAT/TERBANG/BERPUTAR` |
| `kultivator:kuda_tambat`, `unta_tambat`, `keledai_kota` | 1,4 × 1,6 / 1,7 × 2,3 / 0,9 × 1,2 | 42D | diam di tambat; kuda bisa ditumpangi tubuh pengantin (penumpang KultivatorNpc) |
| `kultivator:barongsai` | 1,2 × 1,6 × 2,4 | 42D | tarian singa 40 tick per langkah, kepala membuka mulut, kedip, menggeleng; 2 penari di dalam (kaki terlihat) |
| `kultivator:panggung_keliling` | 5 × 1 × 3 (kolider pola `ColliderEntity`) | 42D | panggung kayu berpanji, dipasang/dibongkar (skala 20 tick) |
| `kultivator:tenda_pasar`, `tenda_lapak` | 2 × 2,4 × 2 | 42D / 42C | kanopi kain bergelombang, peti, keranjang |
| `kultivator:lapak_hantu` | 2 × 1 × 1,5 | 42C | tikar, peti, lentera hijau; lenyap bertahap saat fajar |
| `kultivator:api_kecil` | 1 × 1,2 | 42D | lidah api animasi, asap, menjalar ke titik tetangga, padam bila disiram (klik ember air / teknik air) |
| `kultivator:barang_tercecer` | 0,4 × 0,3 | 42D | sayur/guci/karung di jalan, klik = kumpul |
| `kultivator:usungan_peti` | 1 × 1 × 3,6 | 42D | peti mati di galah, 8 pemikul berjalan tersinkron; tenggelam di makam |
| `kultivator:meja_jamuan` | 1,6 × 0,8 | 42D | meja bundar + 8 bangku + hidangan prop |
| `kultivator:tenda_pengungsi` | 1,6 × 1 × 1,8 | 42F | tenda segitiga kain, lentera kecil |
| `kultivator:meja_dadu` | 1,3 × 0,9 | 42D | meja judi, klik = layar `guanpu` |
| `kultivator:layar_bayang` | 2 × 1,6 × 0,2 | 42D | layar wayang bercahaya + bingkai; dalang di belakang |
| `kultivator:batu_uji` | 0,6 × 0,9 | 42E | batu uji akar menyala warna akar |

`GerobakEntity` (38EK) mendapat satu data tersinkron `TERGULING` (aditif, 42D) untuk peristiwa gerobak; `TanduEntity` (38TK)
dipakai apa adanya dengan varian merah (bila varian warna tidak ada: satu data warna aditif, 42D).

**Peran NPC baru** (ditambah di akhir `NpcRole`, 42A): `PENCOPET` (tier 1, PEKERJA, bukan townsfolk untuk Nama Baik), `PENJAGA_MALAM`
(1, PENJAGA), `MAK_COMBLANG` (0, TETUA), `PENDONGENG` (1, CENDEKIA, stationary), `PENGAMEN` (0, PEDAGANG), `AKROBAT` (1, PEKERJA),
`PENJUAL_OBAT` (1, PEDAGANG), `GURU_SEKOLAH` (1, CENDEKIA), `PENGURUS_DUKA` (1, RAHIB), `PENJAGA_GUDANG` (1, PEDAGANG, stationary),
`PIALANG` (2, PEDAGANG, stationary), `PENARI` (0, PEKERJA), `PEMUSIK` (0, PEKERJA). Rupa di `resep.json` (40K) lewat
`tools/gen_sosok_resep.py` (42V aditif).

#### 42.12.3 Blok dan item baru (42C/42D/42E; model + tekstur 42V)

| Jenis | Id | Isi |
|---|---|---|
| Blok | `kultivator:rak_dagang` | rak kayu 3 tingkat, block entity 3 slot + harga, `BlockEntityRenderer` item tampil + label harga |
| Blok | `kultivator:meja_kasir` | meja kasir lak merah dengan sempoa dan lonceng |
| Blok | `kultivator:papan_nama_toko` | papan gantung, block entity teks nama toko (≤ 16 huruf) |
| Blok | `kultivator:papan_cabang` | papan berlambang sekte (block entity: pemilik, lambang dari atlas `Lambang`) |
| Item | `akta_rumah`, `kunci_rumah` | 42.6.1 |
| Item | `topeng_pasar_hantu` | topeng kertas putih bergaris merah (dipakai di kepala: model kepala) |
| Item makanan | `telur_merah`, `kue_pengantin`, `sup_hangat`, `bubur_laba`, `kue_bulan`, `kue_keranjang`, `air_plum`, `permen_gula`, `tanghulu` | efek di 42.7.6/42.7.7; `tanghulu` (manisan buah tusuk) kenyang + kecepatan I 30 s |
| Item cerita | `kentongan_tua`, `kwitansi_sumur`, `surat_utang`, `wayang_kulit` (4 varian), `resep_sup` | 42.10 |
| Item | `lencana_pejabat` | komponen `kultivator:jabatan` (situs, pangkat); tooltip gelar |
| Item | `surat_kontrak` | tooltip kontrak dagang (informasi, terikat) |

#### 42.12.4 Model dan tekstur (42V; `tools/gen_kota_hidup_assets.py`, `tools/png.py`, aturan 38.O.0, pratinjau isometrik)

| Model | Bagian (piksel model) | Tekstur | Animasi |
|---|---|---|---|
| Kucing | badan 4 × 4 × 9, kepala 4 × 4 × 4 + telinga 1 × 1,5 × 0,5 ×2 + moncong 2 × 1 × 1, ekor 2 segmen 1 × 1 × 5, kaki 1 × 4 × 1 ×4 | 64 × 32, 6 corak | jalan (kaki berlawanan), duduk (kaki belakang dilipat), tidur melingkar (badan diputar, ekor menutup), menjilat kaki, meregang (LEAN depan 20°), ekor sinus; kedip |
| Anjing | badan 6 × 6 × 11, kepala 6 × 6 × 5 + moncong 3 × 3 × 3, telinga, ekor melengkung, kaki ×4; varian penjaga berbulu (lapis bulu 7 × 7 × 12) | 64 × 32, 4 varian | jalan, duduk, gonggong (rahang 20°, kepala menyentak), kibas ekor ±35° cepat, tidur |
| Ayam / anak ayam | badan 4 × 5 × 6, leher-kepala 2 × 4 × 2, jengger 1 × 2 × 2, paruh, sayap 1 × 3 × 4 ×2, kaki tipis | 32 × 32, jago/betina/anak | patuk (kepala turun 50° 6 tick), kepak, kokok (leher naik, paruh membuka) |
| Merpati (per burung, digambar ×N) | badan 2 × 2 × 4, kepala 1,5³, sayap 0,5 × 3 × 3 ×2, ekor 2 × 0,5 × 2 | 32 × 16, 3 warna | kepak 6 tick saat terbang, mengangguk saat jalan, boid (kohesi 0,02, pisah 0,6 blok, selaras 0,05) |
| Kuda | badan 10 × 10 × 22, leher 4 × 10 × 6 (miring 30°), kepala 5 × 5 × 10, surai quad 8 lapis, ekor quad, kaki 3 × 12 × 3 ×4 berkuku | 128 × 64, 4 warna + hiasan pengantin (pita merah, bunga) | kibas ekor, angguk, makan (leher turun), hentak kaki, jalan |
| Unta / keledai | unta: badan 10 × 12 × 22 + dua punuk 6 × 6 × 6, leher S 2 segmen; keledai: kuda 0,7× + telinga panjang | 128 × 64 | kunyah, berlutut (kaki depan dilipat), dengus |
| Barongsai | kepala 12 × 10 × 14 (rahang bawah berengsel, kelopak mata 2 bingkai, tanduk, cermin dahi, bulu quad), badan kain 10 × 8 × 24 bergelombang (5 segmen), ekor bulu, 4 kaki berselana | 128 × 128 merah-emas-hijau | 8 gerakan: menyapa, mengendus, menggaruk, tidur, bangun, melompat (kaki depan naik), makan selada (*cai qing*), menggeleng |
| Panggung keliling | lantai 80 × 4 × 48, tiang 2 × 40 × 2 ×4, atap kain, panji | 128 × 64 kayu + kain merah | panji berkibar, dirakit/dibongkar |
| Tenda pasar / lapak / pengungsi | kanopi 32 × 2 × 32 + tiang; lapak tikar 24 × 1 × 16 + peti; pengungsi A-frame 24 × 14 × 28 | 64 × 64 kain belang / abu | kain bergelombang |
| Lapak hantu | tikar 24 × 1 × 16, 2 peti 6³, lentera hijau gantung | 64 × 64 | lentera bergoyang, kabut tipis klien |
| Api kecil | 6 quad silang lidah api (atlas 8 bingkai 16 × 32) + bara 3 kubus pijar | pancar | lidah api 2 bingkai/tick, membesar saat menjalar, mengecil saat disiram |
| Usungan peti | peti 10 × 10 × 28 lak hitam berhias emas + 2 galah 1 × 1 × 56 | 64 × 64 | goyang langkah ±1°, tenggelam 40 tick |
| Meja jamuan | cakram 24 × 1 × 24 (4 kubus diputar 0/22,5/45/67,5), kaki, 8 bangku 4 × 6 × 4, 6 piring prop | 64 × 64 | — |
| Meja dadu | meja 20 × 12 × 14 kain hijau, cawan bambu 4 × 5 × 4, 3 dadu 1³ | 64 × 32 | cawan dikocok 20 tick, dadu berguling (fisika klien) |
| Layar bayang | bingkai 32 × 24 × 2 + kain 30 × 22 pancar | 64 × 64 + 4 atlas siluet wayang (64 × 64, 6 tokoh per naskah, sendi lengan) | siluet bergerak menurut naskah, layar berkedip lilin |
| Batu uji | batu 8 × 12 × 8 berukir glif 5 unsur | 64 × 32 + pancar | glif menyala sesuai akar (abu/hijau/biru/ungu/emas) |
| Lentera kota | bulat merah 6 × 7 × 6 (5 kubus) + rumbai 1 × 4 × 1; panjang putih 4 × 10 × 4; hijau; kelinci (badan 5 × 4 × 6 + telinga); sutra heksagonal 6 kubus diputar 30° | 32 × 32 nyala/padam | ayun angin ±4°, kedip lilin 0,9-1,0 |
| Rak dagang / meja kasir / papan | rak 16 × 16 × 8 tiga tingkat; meja 16 × 12 × 16 + sempoa model; papan gantung 14 × 8 × 1 + rantai | 32 × 32 | — |

**Prop tangan** (model item JSON `kultivator:fx/kota42/<id>`, maks 40 elemen; id 170-199 dan 200-239):

| Id | Prop | Id | Prop | Id | Prop |
|---|---|---|---|---|---|
| 170 | `guci_arak` | 190 | `gong_tangan` | 210 | `keranjang_telur_merah` |
| 171 | `cawan_arak` | 191 | `kentongan` | 211 | `mangkuk_bubur` |
| 172 | `sangkar_burung` | 192 | `suona` | 212 | `kendi_sup` |
| 173 | `guci_jangkrik` | 193 | `balok_kayu` (xingmu) | 213 | `sapu_lidi` |
| 174 | `erhu` | 194 | `genderang_kecil` | 214 | `kotak_obat_keliling` |
| 175 | `bingkai_sulam` | 195 | `potret_almarhum` | 215 | `plester_harimau` |
| 176 | `cermin_tangan` | 196 | `bayi_bedong` | 216 | `wayang_tongkat` |
| 177 | `almanak_merah` | 197 | `payung_merah` | 217 | `cawan_bambu` |
| 178 | `dadu_cawan` | 198 | `angpao` | 218 | `surat_utang` |
| 179 | `selendang` | 199 | `uang_kertas` | 219 | `kwitansi` |
| 180 | `sendok_gula` | 200 | `simpai` | 220 | `cap_pejabat` |
| 181 | `permen_gula` | 201 | `kipas_tari` | 221 | `gulungan_perkara` |
| 182 | `bunga` | 202 | `pipa` (kecapi) | 222 | `pita_merah` |
| 183 | `buntelan` | 203 | `dizi` (seruling) | 223 | `gunting_besar` |
| 184 | `galah_lentera` | 204 | `dacing` (timbangan) | 224 | `papan_patok` |
| 185 | `karung_beras` | 205 | `kuas_besar` | 225 | `balok_pikul` |
| 186 | `ikan` | 206 | `kuplet_merah` | 226 | `genteng_tumpuk` |
| 187 | `tulang` | 207 | `ranting_dedalu` | 227 | `bata_tumpuk` |
| 188 | `kue_bulan` | 208 | `layang_naga` | 228 | `bola_cuju` |
| 189 | `piring_akrobat` | 209 | `ember_api` | 229 | `kerincing_bayi` |
| 230 | `tikar_zhuazhou` (depan kaki) | 233 | `kue_keranjang` | 236 | `tempayan_air` (kepala) |
| 231 | `lentera_kelinci` | 234 | `tanghulu` | 237 | `jaring_ikan` |
| 232 | `layang_kupu` | 235 | `kantong_kue` | 238 | `sangkar_ayam` |
| | | | | 239 | `nampan_teh_besar` |

**Busana dan perlengkapan** (40K, aditif lewat `tools/gen_sosok_bagian_busana.py` dan `tools/gen_tempat_gear.py`): busana
`pengantin_pria` (jubah merah, bunga sutra di dada, topi pejabat merah), `pengantin_wanita` (jubah merah bersulam phoenix +
**mahkota phoenix** *fengguan* kubus 8 × 4 × 8 + rumbai manik + cadar merah quad), `berkabung` (kain kasar putih, topi kain, sabuk
rami), `mantel_kapas` (berlapis, kerah bulu) + `topi_bulu_dingin`, `kostum_akrobat` (ketat merah-kuning, ikat kepala), `penari`
(selendang panjang dengan pegas 40), `topeng_kertas` (wajah), `topi_penjaga_malam` (caping kain + kain leher), `egrang` (kaki
kayu 2 × 16 × 2 di bawah kaki, perlengkapan pita 48-63 atlas `perlengkapan.png` dilebarkan menjadi 64 × 2048; pita 0-47 tidak
diubah). Kunci rupa baru di `resep.json`: `*.pengantin`, `*.berkabung`, `*.dingin`, `akrobat`, `penari`, `topeng_kertas`,
`penjaga_malam`, `pencopet` (baju gelap, ikat kepala, tas kecil).

**Tekstur glif** 16 × 16 (digambar tangan sebagai matriks piksel di generator, tanpa font): 福 寿 春 囍 奠 酒 茶 药 布 铁 饼 书 符 当 客 栈
丰 戒 面; panji 32 × 64 per keadaan; kuplet 8 × 48.

#### 42.12.5 Animasi (klip 350-449 milik 42, 450-459 milik 42S (42.4A.7), cadangan 460-469; 42V `client/dunia/kota42/gerak/KotaPoses` lewat `GerakKatalog.daftar`)

**Suntingan batas** (42V, aditif, satu baris tiap berkas): `GerakId` konstanta `KOTA_HIDUP_FIRST = 350`, `KOTA_HIDUP_LAST = 469`
(menggeser `CADANGAN_FIRST` ke 470); `GerakKatalog.UKURAN` → `GerakId.KOTA_HIDUP_LAST + 1` dan pemeriksaan batas atas `daftar` →
`KOTA_HIDUP_LAST`. `PropId` konstanta `KOTA_HIDUP_FIRST = 170`, `KOTA_HIDUP_LAST = 239`; `PropKatalog.UKURAN` dan batas `daftar`
→ 239. (Bila 41 dibangun lebih dulu dan memakai 300-349/150-169, tidak ada tumpang tindih.) Format klip dan kanal persis 38.N.5
(`PoseLibrary`), pembauran 6 tick, pegas 40 aktif, Minimal = pose statis tanpa mod TREMBLE/BEAT.

| Id | Klip | Durasi | Kunci ringkas (derajat) | Pemakai | Prop |
|---|---|---|---|---|---|
| 350 | `minum_arak` | loop 60 | RAX −40 → −95 (t15, kepala HX −25 mendongak), tahan 10, kembali; LAX −30 memegang guci | pemabuk | 171 kanan, 170 kiri |
| 351 | `jalan_mabuk` | loop 40 | TWIST ±12 sinus, LEAN ±8 kiri-kanan (TILT), HX acak ±10, langkah tidak rata (pengali kaki 0,7-1,2) | pemabuk | 171 |
| 352 | `tidur_bangku` | loop 120 | rebah menyamping di titik `kursi` (renderer memutar tubuh 90° sumbu Z), lengan jatuh RAX 30 | pemabuk | — |
| 353 | `tonton_catur` | loop 80 | berdiri LEAN 15, HX 25, tangan di punggung; t60 `tunjuk` kecil | penonton catur | — |
| 354 | `gantung_sangkar` | once 40 | RAX −150 (meraih dahan) t20, lepas t30 | burung_sangkar | 172 |
| 355 | `main_erhu` | loop 32 | duduk; LAX −60 (leher erhu tegak di paha), RAX −50 ↔ −20 menggesek (RAY ±25), HX 10, kepala bergoyang irama | pemain erhu, Ah Qin | 174 |
| 356 | `menyulam` | loop 24 | duduk; LAX −55 memegang bingkai, RAX −60 → −40 menusuk (t8) → −70 menarik benang (t16) | penyulam | 175 |
| 357 | `bercermin` | loop 60 | LAX −90 cermin di depan wajah, RAX −120 merapikan rambut (t20-t40), TWIST ±8 | pesolek | 176 |
| 358 | `hitung_koin` | loop 20 | RAX/LAX −55, jari RAZ ±6 bergantian, HX 20 | pelit | 29 |
| 359 | `beri_koin` | once 24 | LEAN 20, RAX −70 → −40 (meletakkan) | dermawan | 29 |
| 360 | `jalan_baca` | loop 40 | `jalan_santai` lambat + RAX/LAX −60, HX 25 (kitab) | kutu buku | 11 |
| 361 | `beri_makan_kucing` | once 40 | jongkok LEAN 50, LIFT −0,4, RAX −40 → −20 | teman kucing | 186 |
| 362 | `lempar_dadu` | once 30 | RAX −80 menggoyang cawan (RAZ ±20 cepat t0-t16), t18 membanting RAX −40 | penjudi, meja dadu | 217 |
| 363 | `menepuk_jidat` | once 20 | RAX −150 ke dahi t6, HX 15, LEAN 8 | penjudi kalah | — |
| 364 | `tari_yangge` | loop 40 | langkah maju-mundur 3+1, RAX/LAX −110 ↔ −60 bergantian, kipas/selendang berputar, TWIST ±20 | tari kipas | 201 / 179 |
| 365 | `lukis_gula` | loop 30 | LEAN 25, RAX −60 menuang garis (RAY −20 → +20 sinus) | pelukis gula | 180 |
| 366 | `intip` | loop 40 | berjinjit LIFT 0,08, LEAN 10, HX −5, tangan di pagar RAX/LAX −80 | pengintip | — |
| 367 | `kagum` | once 30 | HX −15, RAX/LAX −40 terangkat sedikit, LIFT 0,05 lompat kecil | pemuja kultivator, anak | — |
| 368 | `menguap` | once 40 | RAX/LAX −170 meregang t10-t25, HX −20, mulut terbuka (wajah) | pemalas | — |
| 369 | `deklamasi` | once 60 | RAX −130 terbuka ke langit, LAX −40 di dada, HX −10 | penyair, Li Zuixian | — |
| 370 | `nyalakan_lentera` | once 40 | RAX −160 menjulurkan galah t15, tahan t15-t30 (lentera menyala di t25) | penyala lentera | 184 |
| 371 | `dongeng` | loop 60 | duduk; `bicara_tangan` lebar + t0/t30 menepuk balok (RAX −90 → −50 cepat, suara ketuk) | Lao Shuo | 193 |
| 372 | `akrobat_piring` | loop 40 | RAX −170 (tongkat tegak) RAZ ±4 getar, LAX −170, piring berputar 30°/tick | akrobat | 189 ×2 |
| 373 | `akrobat_simpai` | once 30 | lari 10 tick, LEAN 60 → salto (rotasi tubuh 360° sumbu X t12-t24 di renderer), mendarat LIFT −0,2 | akrobat | — |
| 374-376 | `piramid_bawah/tengah/atas` | loop 60 | bawah kuda-kuda RLZ 30 LLZ −30 RAX/LAX −150 menahan; tengah berdiri di bahu (LIFT +1,6) satu kaki; atas LIFT +3,0 tangan terbentang, goyang ±3° | 3 akrobat tersinkron | — |
| 377 | `egrang_jalan` | loop 30 | langkah panjang lambat (pengali 0,6), lengan terbentang penyeimbang | akrobat | gear egrang |
| 378-379 | `barongsai_kepala/ekor` | loop 40 | penari di dalam: berjongkok-melompat menurut gerak barongsai (badan tersembunyi; hanya kaki digambar) | barongsai | — |
| 380 | `pimpin` | loop 160 | `tai_chi_pagi` dengan amplitudo 1,2 + menghadap barisan | pemimpin tai chi | — |
| 381 | `ketuk_pintu` | once 30 | RAX −80 → −70 ×3 (t6, t12, t18) | penjaga malam, mak comblang | — |
| 382 | `pukul_gong` | once 20 | LAX −80 memegang gong, RAX −110 → −60 (t6, easeOutBack, suara gong) | penjaga malam | 190 |
| 383 | `pukul_kentongan` | loop 10 | LAX −70, RAX −90 ↔ −60 cepat | penjaga malam | 191 |
| 384 | `seru_jaga` | once 40 | RAX −110 tangan di mulut, HX −15, LEAN −6 | penjaga malam | 28 kiri |
| 385 | `tabrak` | once 10 | TWIST 30 menyamping, LIFT 0,03, RAX −40 (tangan masuk kantong korban) | pencopet | — |
| 386 | `lari_copet` | loop 10 | LEAN 25, HY menoleh ke belakang tiap 20 tick, satu tangan memegang kantong | pencopet | 29 |
| 387-388 | `tinju_kedai_a/b` | loop 40 | dua klip cermin: ayunan liar RAX −120 → −30 (t10), menghindar TWIST ±25, terhuyung t30 | perkelahian | — |
| 389 | `lerai` | loop 30 | kedua lengan terbentang ke samping RAZ ±80, HY bergantian ke dua pihak | warga/penjaga | — |
| 390 | `oper_ember` | loop 24 | terima dari kiri (TWIST −40, LAX −60) t0-t10 → serahkan ke kanan (TWIST +40) t12-t20 | rantai ember | 209 |
| 391 | `siram_api` | once 20 | RAX/LAX −60 → −120 mengayun ember (t8), partikel air | warga | 209 |
| 392 | `angkat_gerobak` | loop 30 | LEAN 30, RAX/LAX −100, LIFT −0,2 → 0 menegang (TREMBLE) | warga | — |
| 393 | `meratap` | loop 40 | berlutut, tangan di kepala RAX/LAX −160, LEAN 30 ↔ 10 | pemilik gerobak, keluarga duka | — |
| 394 | `pikul_tandu` | loop 30 | RAX −160 galah di bahu, langkah tersinkron (`gerakSinkron` t0 sama untuk 4 pemikul), LIFT bob 0,02 | pemikul tandu | — |
| 395 | `sembah_nikah` | once 120 | tiga kali: tangan terkatup di dada → LEAN 60 (t10-t30) → tegak; menghadap arah berbeda per sembah (yaw dari data upacara) | pengantin | — |
| 396 | `bersulang` | once 30 | RAX −100 mengangkat cawan, t15 minum | tamu | 171 |
| 397 | `tunggang_kuda` | loop 30 | RLX −45, LLX −45, RLZ 25, LLZ −25 (mengangkang), LIFT +0,9 di punggung kuda, bob sesuai langkah kuda | pengantin pria | — |
| 398 | `tiup_suona` | loop 20 | RAX/LAX −80 suona di mulut, HX −10, pipi (wajah) menggembung, BEAT kepala | pemusik | 192 |
| 399 | `tabuh_genderang_kecil` | loop 12 | genderang di pinggang, RAX/LAX bergantian −90 ↔ −50 | pemusik | 194 |
| 400 | `pikul_usungan` | loop 36 | `pikul_tandu` dengan LEAN 6, kecepatan 0,5 | pemikul peti | — |
| 401 | `tabur_uang_kertas` | once 30 | RAX −120 → −160 menebar (RAZ +40), partikel kertas | pengurus duka | 199 |
| 402 | `ratap_duka` | loop 60 | berdiri membungkuk LEAN 25, RAX −140 menyeka mata, bahu BEAT (terisak) | keluarga duka | — |
| 403 | `merangkak` | loop 20 | tubuh bayi: LEAN 80, LIFT −0,7, tangan-kaki berselang | bayi *zhuazhou* | — |
| 404 | `pilih_benda` | once 30 | duduk bayi RAX −60 → −90 meraih (t15), mengangkat benda t25 | bayi | benda pilihan |
| 405 | `gendong_bayi` | loop 60 | LAX −70 RAX −60 memeluk di dada, ayun TWIST ±6 | ibu | 196 |
| 406 | `bagi_telur` | once 30 | LAX −50 keranjang, RAX −70 menyerahkan | ayah bayi | 210 |
| 407 | `peluk` | once 60 | dua tubuh berhadapan: RAX/LAX −80, RAZ 40/LAZ −40 melingkar, LEAN 10; disinkron dua tubuh | pamit calon murid, keluarga | — |
| 408 | `pamit_lambai` | once 40 | berbalik HY 150 lalu `lambai` | calon murid | 183 |
| 409 | `uji_akar` | once 60 | kedua telapak di batu RAX/LAX −75, HX 20; t30 aura naik (FLAG_AURA) | pelamar | — |
| 410-411 | `huaquan_a/b` | loop 20 | RAX −90 → −60 cepat dengan jari (gambar tangan 3 bentuk) t0/t10, kepala maju HX −10 berteriak | kedai arak | — |
| 412 | `nyanyi_mabuk` | loop 40 | satu tangan di bahu tetangga (RAZ 60), lain mengayun cawan, TWIST ±15 | kedai arak | 171 |
| 413 | `dalang` | loop 40 | di balik layar: RAX/LAX −120 memegang tongkat wayang, RAY/LAY ±30 bergantian | dalang | 216 ×2 |
| 414 | `gosok_dinding` | loop 20 | RAX −70, RAY ±30 menggosok, LEAN 10 | warga (K7) | 36 |
| 415 | `bagi_bubur` | loop 40 | LAX −50 mangkuk, RAX −70 → −90 menyendok | Nenek Tang, rahib | 212, 211 |
| 416 | `menimbang` | loop 40 | RAX −120 mengangkat dacing, HX −20 membaca | Pengawas Pasar, pedagang | 204 |
| 417 | `bersaksi` | loop 40 | `bicara_tangan` + menunjuk pihak lawan t20 | saksi perkara | — |
| 418 | `palu_putusan` | once 30 | `palu_hakim` + HX −10 | Hakim/pejabat | 22 |
| 419 | `sodor_amplop` | once 30 | LEAN 20, RAX −60 menyodorkan diam-diam, HY menoleh kiri-kanan | pihak licik | 198 |
| 420 | `pasang_patok` | loop 24 | `mencangkul` dengan RAX −140 → −60 memukul patok | mandor/tukang kavling | 224 |
| 421 | `pikul_balok` | loop 30 | `angkat_papan` dengan balok besar, LEAN 8 | tukang | 225 |
| 422 | `susun_genteng` | loop 20 | di atas perancah: LEAN 35, RAX −80 → −40 menaruh | tukang | 226 |
| 423 | `potong_pita` | once 40 | RAX −90 gunting (RAZ menutup t20), sorak | peresmian | 223, 222 |
| 424 | `sapu_makam` | loop 24 | `sapu` pendek LEAN 30 | Qingming | 213 |
| 425 | `bakar_uang_kertas` | once 60 | jongkok, RAX −40 → −20 memasukkan kertas ke pembakar, asap | Qingming, duka | 199 |
| 426 | `tancap_dedalu` | once 20 | RAX −140 menyelipkan ranting di atas pintu | Qingming | 207 |
| 427 | `tempel_kuplet` | once 40 | RAX/LAX −150 menempel, mengusap t25 | Tahun Baru | 206 |
| 428 | `main_cuju` | loop 20 | menendang kaki RLX −60 t5, bola melambung (prop dilempar klien), LIFT 0,05 | anak, pemuda | 228 |
| 429 | `makan_tanghulu` | loop 40 | RAX −80 → −95 menggigit, HX −5 | anak | 234 |
| 430 | `pikul_tempayan` | loop 30 | tempayan di kepala, LAX −170 menahan, langkah lembut | wanita kampung | 236 |
| 431 | `menjala` | once 60 | RAX/LAX −150 → −60 melempar jala (t20, easeOutBack), tarik t40-60 | nelayan | 237 |
| 432 | `gandeng_cucu` | loop 50 | `jalan_tua` dengan LAX −30 RAZ −20 menggandeng (anak RAX −80 RAZ 20) tersinkron | kakek/nenek + cucu | 13 |
| 433 | `elus_hewan` | once 40 | jongkok, RAX −30 mengusap 3 kali | warga | — |
| 434 | `kejar_ayam` | loop 12 | `lari` + LEAN 30, lengan terbentang meraih | warga, peristiwa ayam lepas | — |
| 435 | `tatap_bulan` | loop | duduk di atap/alun HX −40, LAX di lutut | Pertengahan Musim Gugur | 188 |
| 436 | `angkat_lentera_kelinci` | loop 40 | anak mengangkat lentera, berjalan riang | festival gugur | 231 |
| 437 | `beri_angpao` | once 30 | dua tangan menyerahkan, membungkuk kecil | warga ke anak (Tahun Baru) | 198 |
| 438 | `terima_angpao` | once 30 | anak `hormat` kecil lalu melompat | anak | 198 |
| 439 | `antre` | loop 60 | berdiri, berat badan bergeser RLZ/LLZ ±6, HY menoleh ke depan barisan | lumbung, bubur Laba, sekolah | 185 |
| 440 | `layang_naga` | loop 40 | `main_layangan` dengan tali tegang, mundur langkah | Qingming | 208 |
| 441 | `dorong_sangkar_ayam` | loop 30 | membawa sangkar ayam ke pasar | petani | 238 |
| 442 | `sajikan_teh_besar` | loop 30 | nampan besar di dua tangan, `jalan_santai` | pelayan kedai | 239 |
| 443 | `tiru` | (varian) | klip guru diputar dengan jeda 10 tick dan amplitudo 0,8 | murid magang | — |
| 444 | `mengigau` | loop 80 | `tidur` + HX berputar pelan, gumam | sakit | — |
| 445 | `batuk` | once 20 | LEAN 20 → 5 ×2, RAX −110 ke mulut | sakit | — |
| 446 | `pincang` | loop 30 | `jalan_tua` asimetris kaki kanan ×0,5 | PULIH | 13 |
| 447 | `main_kucing` | loop 30 | anak jongkok mengayun benang di depan kucing | anak | — |
| 448 | `peregangan_pagi` | once 60 | tangan ke atas, membungkuk ke kiri-kanan | pagi | — |
| 449 | `ucap_selamat` | once 30 | `hormat` dengan tangan terkatup mengayun 2 kali (*gongxi*) | Tahun Baru, peresmian | — |

Klip pemain tidak ditambah (pemain memakai klip vanilla/40 di upacara). Semua klip tersinkron dua tubuh atau lebih memakai
`SosokApi.gerakSinkron(e, klip, t0)` dengan `t0` sama.

#### 42.12.6 Efek klien dan suara buatan (42V; patuh `EffectQuality`, `EpicBudget`, tidak menggambar saat shadow pass)

| Efek | Isi | Min / Nor / Maks |
|---|---|---|
| Lentera kota | 42.9.2 | 16 / 128 / 512 lentera, halo 0 / 64 / 256 |
| Asap cerobong | 42.9.2 | 1 per 2 s ≤ 32 blok / 2/s ≤ 64 / 4/s ≤ 96 |
| Panji/spanduk | `KainKota` 42.9.3 | 8 / 24 / 64 per tempat |
| Petasan | rangkaian 20-60 ledakan kecil (partikel percik merah + kertas), suara `entity.firework_rocket.blast` volume kecil + suara baru `kota42.petasan`; **tanpa** `FireworkRocketEntity` | 0 / 30 / 60 per rangkaian |
| Uang kertas, kertas merah, kelopak, daun gugur, uap kukusan | partikel sprite (atlas 42.12.4) | 25 % / 100 % / 200 % |
| Merpati | boid 42.12.4 | 4 / 8 / 16 burung per kawanan |
| Wayang | siluet sprite pada quad layar, 6 tokoh × 4 naskah | ya / ya / ya (Minimal 10 fps) |
| Bercak cahaya tanah | decal radial aditif di bawah lentera | 0 / 32 / 128 |
| Bekas jelaga (K7) | quad gelap translusen di dinding | 0 / 32 / 64 |
| Papan billboard | nama jalan, nisan, papan harga, kavling (pola 38.U.4) | ≤ 12 / 24 / 32 bersamaan, ≤ 24 blok |
| Melodi prosedural | not demi not dari `data/kultivator/kota/melodi/*.json` (pentatonik gong-shang-jue-zhi-yu) dengan sampel instrumen baru | — |
| Ambience | `RiuhKota` mendapat suasana baru `malam_meriah` (distrik malam) dan `upacara` (suona jauh) | — |

**Sampel instrumen** (42V, disintesis `tools/gen_kota_hidup_suara.py` dengan pipa `tools/gen_riuh_kota.py` — tulis berblok,
libsndfile; OGG mono 22 kHz): `erhu` (gergaji + vibrato 5,5 Hz + derau busur, 12 nada), `suona` (pulsa sempit + formant hidung,
12 nada), `pipa` (Karplus-Strong petik, 12 nada), `dizi` (sinus + derau napas + membran, 12 nada), `gong` (parsial inharmonik
meluruh 4 s, 3 ukuran), `kentongan` (klik kayu 3 nada), `genderang_kecil`, `balok_kayu`, `petasan` (6 varian),
`merpati_peluit` (dengung naik-turun). Takarir Minecraft untuk setiap suara (`subtitles.kultivator.kota42.*`). Kredit: buatan
sendiri (CREDITS.md, agen finish).

#### 42.12.7 Suara Jepang (VOICEVOX; 42V menambah ke `tools/gen_voices.py` `WORLD`, grup `KOTA42` di `VoiceEvents`; generasi hanya F42)

Ejaan kana dipakai bila kanji berisiko salah baca; kata yang masih salah baca ditambahkan ke kamus `READ` di `gen_voices.py`.

| Kunci | Suara | Jepang | Teks id | Emosi |
|---|---|---|---|---|
| `kota42.sapa_pagi` | f1 | おはようございます！今日もいい天気ですね。 | Selamat pagi! Cuacanya bagus hari ini. | joy |
| `kota42.sapa_kenal` | m2 | おや、旦那！また来てくださったんですね。 | Wah, Tuan! Datang lagi rupanya. | joy |
| `kota42.sapa_asing` | m3 | 見かけない顔だな。旅のお方かい？ | Wajahmu asing. Pengembara, ya? | normal |
| `kota42.sapa_akrab` | f2 | あら、いらっしゃい！お茶でも飲んでいって。 | Wah, mampir! Minum teh dulu. | joy |
| `kota42.terima_kasih` | f1 | 本当にありがとうございます。このご恩は忘れません。 | Terima kasih sungguh. Budi ini takkan kulupakan. | joy |
| `kota42.diselamatkan` | m2 | あの火事のとき、あなたがいなければ…！ | Waktu kebakaran itu, kalau bukan karena Tuan...! | sad |
| `kota42.anak_kagum` | f1 | すごい！お兄さん、空を飛べるの？ | Hebat! Kakak bisa terbang? | joy |
| `kota42.tanya_jalan` | m1 | ああ、それなら東へまっすぐ、橋を渡った先ですよ。 | Oh, itu lurus ke timur, di seberang jembatan. | normal |
| `kota42.tidak_tahu` | f3 | ごめんなさい、私も来たばかりで…井戸の奥さんたちに聞いてみて。 | Maaf, aku juga baru datang... tanyakan ibu-ibu di sumur. | normal |
| `kota42.gosip_bisik` | f2 | ねえ、聞いた？張さんの息子と李さんの娘がね… | Hei, sudah dengar? Putra keluarga Zhang dan putri keluarga Li... | normal |
| `kota42.gosip_tawa` | f1 | まあ、大げさなんだから！ | Ah, dia memang suka melebih-lebihkan! | joy |
| `kota42.duka` | m2 | 父が…昨夜、亡くなりました。 | Ayah... wafat semalam. | sad |
| `kota42.bayi_lahir` | m1 | 生まれた！男の子だ！みんな、赤い卵だよ！ | Sudah lahir! Laki-laki! Semua, ini telur merah! | joy |
| `kota42.nikah_undang` | f2 | 明日、娘が嫁ぐんです。ぜひいらしてください。 | Besok putriku menikah. Datanglah. | joy |
| `kota42.takut_serangan` | f3 | 早く中へ！獣が城壁の外まで来てる！ | Cepat masuk! Binatang sudah sampai di luar tembok! | fear |
| `kota42.marah` | m3 | なんだと！もう一度言ってみろ！ | Apa katamu! Ulangi sekali lagi! | angry |
| `kota42.mabuk` | m1 | ひっく…月が…二つ…いや、一つか… | Hik... bulannya... dua... bukan, satu... | normal |
| `kota42.catur_skak` | elder | 王手！どうだ、参ったか！ | Skak! Bagaimana, menyerah? | joy |
| `kota42.catur_kalah` | elder | むう…今日は風が悪い。 | Hmm... anginnya sedang buruk hari ini. | sad |
| `kota42.pedagang_seru` | m2 | 安いよ安いよ！今朝採れたての野菜だよ！ | Murah, murah! Sayur baru dipetik pagi ini! | joy |
| `kota42.harga_naik` | f2 | お米がまた上がったのよ。困ったわねえ。 | Beras naik lagi. Repot sekali. | sad |
| `kota42.ronda_geng1` | elder | てんかわき、ものかわく。ひのようじん！ | Udara kering, hati-hati api dan lilin! | normal |
| `kota42.ronda_geng3` | elder | 戸締まり用心、泥棒に気をつけよ！ | Kunci pintu, waspada pencuri! | normal |
| `kota42.ronda_geng4` | m3 | 四更でございます。安らかにお休みなされ。 | Geng keempat. Tidurlah dengan tenang. | calm |
| `kota42.ronda_geng5` | elder | 五更！夜明けは近いぞ。 | Geng kelima! Fajar sudah dekat. | calm |
| `kota42.copet_teriak` | f1 | スリだ！誰か捕まえて！ | Copet! Tolong tangkap! | fear |
| `kota42.copet_tabrak` | m3 | おっと、失礼！ | Ups, maaf! | normal |
| `kota42.copet_tertangkap` | m3 | ま、参った！返します、返しますから！ | A-ampun! Kukembalikan, kukembalikan! | fear |
| `kota42.kebakaran` | m1 | 火事だ！水を持ってこい！ | Kebakaran! Bawa air! | fear |
| `kota42.api_padam` | m2 | 消えた…助かった…ありがとうございます！ | Padam... selamat... terima kasih! | joy |
| `kota42.gerobak` | m2 | ああ、荷が！誰か手を貸してくれ！ | Aduh, muatanku! Tolong bantu! | sad |
| `kota42.lerai` | m3 | やめろ！二人とも、やめるんだ！ | Hentikan! Kalian berdua, hentikan! | angry |
| `kota42.lao_shuo_buka` | elder | さてさて、今日お話しするのは… | Nah, nah, yang akan kuceritakan hari ini... | calm |
| `kota42.lao_shuo_tutup` | elder | 続きは、また明日のお楽しみ！ | Lanjutannya, nantikan besok! | joy |
| `kota42.akrobat_buka` | f1 | さあさあ、お立ち会い！三日月一座の妙技、とくとご覧あれ！ | Ayo, ayo, saksikan kehebatan Rombongan Bulan Sabit! | joy |
| `kota42.penjual_obat` | m2 | この膏薬ひと塗りで、腰の痛みもたちまち消える！ | Sekali oles plester ini, sakit pinggang langsung hilang! | joy |
| `kota42.sekolah_hafal` | f1 | じんしのしょ、せいほんぜん。せいあいちかく、ならいあいとおし。 | Manusia pada awalnya berwatak baik; wataknya dekat, kebiasaannya menjauhkan. | normal |
| `kota42.guru_panggil` | m2 | さあ、みんな並んで。学堂へ行くよ。 | Ayo semua berbaris. Kita ke sekolah. | normal |
| `kota42.taichi` | elder | ゆっくり息を吸って…吐いて… | Tarik napas pelan... hembuskan... | calm |
| `kota42.comblang` | f2 | いいご縁がありますよ。任せてちょうだい！ | Ada jodoh yang bagus. Serahkan padaku! | joy |
| `kota42.nikah_jemput` | m1 | 花嫁のお迎えだ！道を開けてくれ！ | Penjemputan pengantin! Beri jalan! | joy |
| `kota42.nikah_sembah` | elder | いっぱい、てんち！にはい、こうどう！ふうふ、たいはい！ | Sembah langit dan bumi! Sembah orang tua! Saling bersembah! | joy |
| `kota42.bersulang` | m2 | 新郎新婦に、乾杯！ | Untuk kedua mempelai, bersulang! | joy |
| `kota42.makam_pimpin` | elder | 故人を偲び、皆で送りましょう。 | Mari kita kenang almarhum dan mengantarnya bersama. | sad |
| `kota42.makam_ratap` | f2 | どうして…どうして先に逝ってしまったの… | Kenapa... kenapa pergi lebih dulu... | sad |
| `kota42.zhuazhou_kuas` | f1 | 見て！筆を掴んだわ！きっと学者になるわね！ | Lihat! Dia meraih kuas! Pasti jadi sarjana! | joy |
| `kota42.zhuazhou_pedang` | m1 | 剣を掴んだぞ！この子は仙人になるかもしれん！ | Dia meraih pedang! Anak ini mungkin jadi kultivator! | joy |
| `kota42.rekrut_terima` | m2 | おめでとう。今日から君は我が宗門の候補だ。 | Selamat. Mulai hari ini kau calon murid sekte kami. | joy |
| `kota42.rekrut_pamit` | f1 | お父さん、お母さん…行ってきます！ | Ayah, Ibu... aku berangkat! | sad |
| `kota42.restu_tolak` | m3 | 息子は店を継ぐんだ。仙人なんぞにはやらん！ | Putraku akan mewarisi toko. Takkan kuserahkan jadi kultivator! | angry |
| `kota42.pelindung_sapa` | m1 | 守護宗門の方だ！ありがたい！ | Itu orang dari sekte pelindung! Syukurlah! | joy |
| `kota42.bela_panggil` | m2 | 獣の群れが町に迫っています！どうかお助けを！ | Kawanan binatang mendekati kota! Tolonglah kami! | fear |
| `kota42.bela_menang` | m2 | 町は守られた！宗門万歳！ | Kota terselamatkan! Hidup sekte! | joy |
| `kota42.panji_naik` | elder | 今日よりこの町は、貴宗の庇護を受けます。 | Mulai hari ini kota ini di bawah naungan sekte Tuan. | calm |
| `kota42.mandor` | elder | 礎がしっかりしてこそ、家は百年もつ。 | Hanya bila pondasinya kokoh, rumah bertahan seratus tahun. | calm |
| `kota42.kavling_selesai` | m1 | 新しい家が建ったぞ！祝いだ！ | Rumah baru sudah berdiri! Mari rayakan! | joy |
| `kota42.bangun_kembali` | m2 | 壊れても、また建てればいい。みんなでな。 | Kalau hancur, kita bangun lagi. Bersama-sama. | calm |
| `kota42.sandi_malam` | demon | 月のない夜、石の下で水が眠る… | Malam tanpa bulan, air tidur di bawah batu... | calm |
| `kota42.lentera_hijau` | demon | 顔は見ない、名は聞かない。それがここの決まりだ。 | Tak melihat wajah, tak bertanya nama. Itu aturan di sini. | calm |
| `kota42.razia` | m1 | 役人だ！逃げろ！ | Petugas! Lari! | fear |
| `kota42.dadu_besar` | m3 | 大だ！大が来たぞ！ | Besar! Keluar besar! | joy |
| `kota42.dadu_kecil` | m3 | くそっ、また小か… | Sial, kecil lagi... | sad |
| `kota42.huaquan` | m1 | ごかいしゅ！ろくろくじゅん！ | Lima juara! Enam-enam lancar! | joy |
| `kota42.wayang_buka` | m2 | 影絵芝居の始まり始まり！ | Pertunjukan wayang bayang dimulai! | joy |
| `kota42.sup_nenek` | f2 | 寒かったろう。熱いスープをお飲み。 | Dingin, kan? Minumlah sup panas ini. | calm |
| `kota42.laba` | elder | ろうはちの粥だ。誰でも並びなされ。 | Ini bubur Laba. Siapa saja silakan antre. | calm |
| `kota42.tahun_baru` | f1 | 新年おめでとう！お年玉ちょうだい！ | Selamat tahun baru! Minta angpao! | joy |
| `kota42.pindah` | m2 | 水の都に仕事があるそうで…家族で引っ越します。 | Katanya ada kerja di Kota Air... sekeluarga kami pindah. | normal |
| `kota42.pejabat_sapa` | m1 | これは、お役人様！ | Wah, Tuan Pejabat! | normal |
| `kota42.putusan` | elder | 判決を下す！ | Putusan dijatuhkan! | angry |
| `kota42.suap` | m3 | まあまあ、これはほんの気持ちで… | Sudahlah, ini sekadar tanda terima kasih... | normal |
| `kota42.pajak_keluh` | m3 | また税が上がるのか… | Pajak naik lagi?... | sad |
| `kota42.cucu_cerita` | elder | おじいちゃんの若い頃にな、空飛ぶ剣士が町を救ってくれたんじゃ。 | Waktu Kakek masih muda, seorang pendekar terbang menyelamatkan kota ini. | calm |
| `kota42.ah_qin` | f2 | この曲は、亡き師匠の曲です。 | Lagu ini lagu almarhum guruku. | sad |
| `kota42.lao_geng_pamit` | elder | わしの拍子木も、今夜で最後か… | Kentonganku pun, malam ini yang terakhir... | sad |
| `kota42.xiao_bang` | m3 | 師匠、見ててください。僕が町の夜を守ります！ | Guru, lihatlah. Aku yang akan menjaga malam kota! | joy |
| `kota42.xiao_yu` | f1 | 葉っぱがね、私の手の上で浮かぶの！ | Daunnya melayang di atas tanganku! | joy |

78 baris + 24 kata seru 42.5A.8 = **102 klip**; caption `en` ditulis 42V (contoh `kota42.ronda_geng1`: "Dry skies, dry things, mind the fire!"). Sebelum klip dibuat:
cadangan `kotabesar.*`, `tempat.*` dan gumam 73 (`ObrolanSuara`) yang sudah ada, teks tetap di gelembung.

---------------------------------------------------------------------------------------------------------------------

### 42.13 Data, sinkron dan kinerja

#### 42.13.1 Data (SavedData di overworld; kunci baru; tidak ada migrasi)

| SavedData | Pemilik | Isi | Perkiraan ukuran |
|---|---|---|---|
| `kultivator:kota_warga` (`WargaData`) | 42A | tabel `pemain` (UUID → indeks); per situs: `warga[]` (compound kunci pendek, tabel di bawah), `arsip[]` (≤ 400), `gosip[]` (≤ 64), `stat` (populasi, kemakmuran 14 hari, suasana rata), `hariTerakhir`, `nextId` global | ±330 B per warga; 3.000 warga ≈ 1 MB mentah / ±250 KB gzip; arsip 400 × 40 B per tempat |
| `kultivator:kota_ekonomi` (`KotaEkonomiData`) | 42C | rumah (situs → id → pemilik, jenis, sewaSampai, segelSampai, tunggakan), toko (pemilik, situs, rak, harga, laci, pegawai, reputasi, laporan 7 hari), usaha (situs → id → jenis, lot, pemilikWarga, modal, laba 8 minggu, saham UUID→NB), gudang (UUID+situs → `ItemStack` codec, ≤ 108), kontrak, `namaDagang` per UUID, pajak (tarif, riwayat 8 minggu), jabatan (situs → UUID → pangkat, hadir, tugas), perkara aktif, stok kota (12 kategori), jenuh pemain, barang curian Pasar Hantu (`ItemStack`, pemilik, hari) | ±40-200 KB per pemain aktif |
| `kultivator:kota_acara` (`KotaAcaraData`) | 42D | kalender per situs (upacara terjadwal: jenis, warga, jam), rombongan (6: posisi rute, kota berikut), jeda peristiwa, hari raya yang sudah jalan | < 30 KB |
| `kultivator:kota_sekte` (`KotaSekteData`) | 42E | kantor (situs → pemilik, sewa, penjaga, pelamar ≤ 12), pakta (situs → pemilik, mulai, gagal, upeti, serbuan berikut), cabang (situs → pemilik, rumah, jenis, sejak), tugas murid (soul → situs, tugas, sampai) | < 20 KB |
| `kultivator:kota_tumbuh` (`KotaTumbuhData`) | 42F | kavling (situs → id, pos, hadap, gaya, jenis, tahap, bahan terpakai, penyumbang, terhalang), stok bahan (5), kemakmuran, proyek bangun kembali (gugus, pengungsi, penyumbang, tugu) | < 40 KB |
| `kultivator:kota_cakap` (`CakapData`) | 42K | per warga sifat (6 B), PAD jarang, fakta ≤ 24, riwayat percakapan + janji per pemain, anti-ulang; per pemain cincin 256 kunci; tabel gosip per tempat (42.5A.12) | ±0,6 KB per warga aktif (≈ 400 KB gzip untuk 3.000) |
| `kultivator:kota_ragam` (`RagamData`) | 42S | per tempat: peta zona (cache, dibangun ulang saat aktif), riwayat kegiatan per warga 3 hari (≤ 24 short/hari), metrik harian 7 hari; reservasi titik hanya di memori | ±150 B per warga |

**Kunci compound Warga** (satu atau dua huruf): `i` id, `s` situs-slot, `k` kelamin, `l` lahir (hari dunia × 10 untuk pecahan
Tahun Fana), `m` marga, `n` nama, `g` generasi, `a`/`b` ayah/ibu, `p` pasangan, `c` anak (int[]), `r` rumah tangga, `h` rumah
(slot), `j` peran (`NpcRole.id()`), `o` look, `v` suara, `w` watak, `x`/`y` kebiasaan, `z` akar, `q` ranah, `e` ahli, `d`
kesehatan, `u` suasana dasar, `t` status (`HIDUP/JALAN/SEKTE/MATI`), `T` hari status, `L` hubungan (long[]), `I` ingatan (list:
`P` indeks pemain, `k`, `n`, `s`, `b`, `u`, `h`, `e` int[]), `G` gosip (int[]), `tk` tokoh, `Z` zhuazhou, `S` sikap keluarga,
`J` tujuan perjalanan (situs, tiba).

#### 42.13.2 Sinkron

| Payload | Arah | Isi | Kapan | Batas |
|---|---|---|---|---|
| `kultivator:kota_suasana` | S2C | situs, bit acara (32), kemakmuran, suasana rata, musim + hari raya, lambang pelindung (packed) atau −1, mode lentera, ≤ 16 rumah khusus (id rumah → nikah/duka/tahun baru) | masuk ≤ 160 blok, saat berubah | ≤ 128 B |
| `kultivator:kota_titik` | S2C | titik visual potongan (lentera, cerobong, panji, spanduk, kucing_tidur, nisan + nama, nama_jalan, pajangan): posisi relatif (short × 3), jenis (byte), data (short) | sekali per potongan per sesi saat pemain ≤ 128 blok | ≤ 8 KB per potongan, ≤ 32 KB/s |
| `kultivator:kota_upacara` | S2C | id, jenis, simpul jalur (≤ 32 × 12 B), id entitas peserta (≤ 24), fase | mulai/fase berubah | ≤ 600 B |
| `kultivator:kota_kavling` | S2C | kotak perancah (≤ 16 × 6 short), tahap, rencana | ≤ 96 blok, berubah | ≤ 256 B |
| `kultivator:kota_kejar` | S2C | id copet, sisa tick | selama kejar, 4 Hz | 12 B |
| `kultivator:kota_irama` | C2S/S2C | ketukan HUD irama | selama, ≤ 10/s (`TempatLayar.AKSI_PER_DETIK`) | 16 B |
| `kultivator:kota_ujaran` | S2C | id entitas pembicara, `Component` ujaran (pohon translatable), id gestur, durasi, kunci suara seru/VOICEVOX atau kosong | tiap ujaran NPC-NPC/selingan yang didengar pemain ≤ 12 blok | ≤ 512 B, ≤ 8/s per pemain |
| `kultivator:kota_peta_panas` | S2C (debug, op) | sel 16 × 16: hunian/target (byte) untuk `uji … sebaran peta` | 1 Hz selama debug | ≤ 4 KB |
| layar | `TempatLayar` 38 | lembar per layar | buka/segarkan | ≤ 32 KB |

Usia, suasana dan rupa tubuh memakai data tersinkron Sosok yang sudah ada (`setUsia`, `setSuasana`, `setRupa`) — tidak ada
payload per warga. Hewan memakai data entitas biasa.

#### 42.13.3 Kinerja

* Server: Hari Warga satu tempat per tick (< 1 ms ibu kota), simulasi toko/usaha per jam dunia per toko (O(toko)), harga
  di-cache per tempat per jam (12 float), peristiwa jalan diperiksa tiap 200 tick per tempat aktif, rombongan/pelancong tiap 20
  tick. `uji kota anggaran` mencetak waktu per subsistem (sasaran: rata-rata < 0,5 ms/tick semua 42 dengan 1 ibu kota + 3 kota
  aktif).
* Entitas: `DuniaBudget.setCap("kota42", 96)` (hewan ≤ 48, mekanisme ≤ 48); per tempat hewan 24, mekanisme 12; tubuh tambahan
  42 (pencopet, bayi *zhuazhou*, penari barongsai, anggota rombongan ≤ 5, pemusik ≤ 4) memakai `HidupApi.tubuhStaf` hanya saat
  acaranya; peserta upacara adalah tubuh penduduk biasa (topi 38). MSPT guard 38 tetap berlaku untuk hewan.
* Mesin percakapan 42K dan sebaran 42S: angka di 42.5A.12 dan 42.4A (Penyeimbang O(titik sejenis) per pergantian kegiatan, pemilih
  tubuh O(warga terdekat) per siklus, rencana harian sekali per warga per hari).
* Klien: anggaran 42.12.6; animasi hewan penuh ≤ 32 blok, 32-64 tiap 2 bingkai, > 64 pose dasar; wayang dan boid berhenti di luar
  frustum; tidak menggambar saat `ShaderCompat.shadowPass()`.

---------------------------------------------------------------------------------------------------------------------

### 42.14 Config, perintah, panduan, advancement, lang

#### 42.14.1 Config (`config/kultivator-dunia.json`, `DuniaConfig.section(nama, Kelas, Kelas::new, Kelas::clamp)`; registri tetap
terdaftar walau dimatikan)

| Bagian | Field | Bawaan | Rentang | Arti |
|---|---|---|---|---|
| `kotaWarga` | `aktif` | true | – | sakelar 42A (mati = perilaku 38 persis) |
| | `hariPerTahun` | 2 | 1-8 | hari dunia per Tahun Fana (K1) |
| | `kematianUsia` | true | – | warga wafat karena usia (K3); mati = hanya pensiun dan pindah |
| | `umurMaks` | 98 | 80-120 | — |
| | `peluangLahir` | 0.25 | 0-0.6 | per tahun pasangan |
| | `peluangLamaran` | 0.40 | 0-1 | — |
| | `migrasi` | true | – | pindah antar-kota |
| | `rasioPerempuan` | 0.5 | 0.2-0.8 | — |
| | `peluangAkar` | 0.15 | 0.02-0.5 | total peluang berakar saat lahir (sebaran 42.4.2 diskalakan) |
| | `arsipMaks` | 400 | 100-2000 | per tempat |
| | `kejarHari` | 60 | 10-365 | — |
| | `ingatanMaks` / `hubunganMaks` / `gosipMaks` | 6 / 8 / 64 | 2-16 / 4-16 / 16-256 | — |
| | `tokohAbadi` | true | – | tokoh cerita tidak menua (kecuali yang ditandai fana) |
| | `sebaranAktif` | true | – | 42.4A (mati = urutan lama Pemanggil, titik pertama) |
| | `kapZona` / `lantaiZona` | 1.25 / 0.5 | 1-3 / 0-1 | topi dan lantai zona |
| | `jarakMin` | 1.5 | 0.8-4 | jarak antar-tubuh tak berinteraksi |
| | `belokanPeluang` / `belokanMaks` | 0.12 / 3 | 0-0.5 / 0-10 | 42.4A.6 |
| | `selinganDetik` | 8-30 | 4-120 | rentang selingan mikro |
| | `ragamPenaltiKemarin` | 0.6 | 0-1 | 42.4A.4 |
| | `kelompokPeluang` | 0.2 | 0-0.6 | jalan keluarga dan kelompok lain |
| `kotaBicara` | `aktif`, `semuaWarga` | true, true | – | dialog untuk semua orang sipil |
| | `ulangHari` | 3 | 0-14 | jeda baris yang sama |
| | `permintaanPerHari` / `PerTempat` | 3 / 6 | 0-10 / 0-20 | — |
| | `pinjaman` | true | – | 42.5.7 |
| | `mesinLanjut` | true | – | 42.5A (mati = sapaan 38 saja) |
| | `obrolanNpc`, `obrolanTampilJarak`, `obrolanMaksTampil` | true, 12, 4 | –, 4-32, 0-12 | 42.5A.9 |
| | `tampilPeluang` | false | – | tampilkan persen berhasil nada |
| | `tikPeluang` | 0.25 | 0-0.6 | tik bicara |
| | `dialek`, `registerKuno`, `suaraSeru` | true, true, true | – | 42.5A.8 |
| | `faktaMaks`, `lupaSkala`, `antiUlangJam` | 24, 1.0, 24 | 8-64, 0.25-4, 1-168 | 42.5A.4 / 42.5A.8 |
| `kotaEkonomi` | `aktif` | true | – | sakelar 42C |
| | `rumah`, `rumahMaks`, `kunciJedaMenit` | true, 4, 30 | –, 1-12, 5-240 | 42.6.1 |
| | `sewaPersen` / `pajakProperti` | 3 / 1 | 1-10 / 0-5 | % per 7 hari |
| | `toko`, `tokoBatas` | true, 60 | –, 10-300 | batas NB/hari × (b+1) |
| | `hargaDinamis`, `hargaMin`, `hargaMaks` | true, 0.6, 1.6 | –, 0.3-1, 1-3 | 42.6.3 |
| | `investasi`, `investasiMaks` | true, 200 | –, 50-1000 | NB × (b+1) per usaha |
| | `gudang`, `kontrak` | true, true | – | 42.6.5 |
| | `pajak`, `pajakBawaan`, `jabatan`, `gajiSkala` | true, 8, true, 1.0 | –, 5-20, –, 0.25-4 | 42.6.6 |
| | `pasarHantu`, `peluangRazia` | true, 0.10 | –, 0-0.5 | 42.6.7 |
| | `copetAmbilBarang` | false | – | K2 |
| `kotaAcara` | `aktif`, `jarakTampil` | true, 96 | –, 48-160 | — |
| | `rombongan`, `rombonganJumlah` | true, 6 | –, 0-12 | — |
| | `rutinitasPagi`, `ronda`, `musim`, `tahunBaru`, `upacara`, `distrikMalam` | true | – | — |
| | `peristiwa`, `peristiwaPerHari` | true, 3 | –, 0-8 | — |
| | `copet`, `perkelahian`, `apiKecil` | true | – | masing-masing |
| `kotaHewan` | `aktif`, `perTempat` | true, 24 | –, 0-64 | — |
| | `kucing`, `anjing`, `ayam`, `merpati`, `ternak` | true | – | — |
| `kotaSekte` | `aktif`, `kantor`, `pakta`, `paktaMaks`, `serbuanHari`, `murid`, `cabang` | true, true, true, 3, 15, true, true | –, –, –, 1-8, 5-60, –, – | 42.8 |
| `kotaTumbuh` | `aktif`, `ambang`, `serentak`, `kecepatan` | true, 55, 1, 1.0 | –, 20-95, 1-4, 0.25-4 | 42.9.4 |
| | `bangunKembali`, `ambangProyek` | true, 200 | –, 50-2000 | 42.9.5 |
| | `jelaga` | true | – | K7 |
| | `serbuanRusakNyata` | false | – | K6 (bila true: elit penyerang memakai `WorldBreaker` dalam radius 6 di luar zona aman inti kota) |

Klien (`config/kultivator-client.json`, grup Tampilan): `kota42.lentera` (true), `kota42.asap` (true), `kota42.panji` (true),
`kota42.petasan` (true), `kota42.merpati` (true), `kota42.wayang` (true), `kota42.jelaga` (true), `kota42.melodi` (100 %);
Pengaturan > Suara: "Musik jalanan" 100/75/50/25/Mati.

#### 42.14.2 Perintah pemain (`DuniaCommands.player("kotaku", ...)`, 42A; `bicara` sudah dipakai `Pengembara`)

`/kultivasi kotaku buku [kota] | warga <nama> | rumah | toko | investasi | gudang | kontrak | jabatan | perkara | kantor | pakta
| cabang | kavling | acara | pulang` (pulang = pakai kunci rumah terdekat). Semua membuka layar yang sama atau menjalankan aksi
yang sama dengan tombol (satu penangan); layar yang butuh NPC dibuka dari jarak jauh hanya baca (`npcId = -1`).

#### 42.14.3 Perintah uji (`DuniaCommands.test("kota_hidup", KotaUji::node)`, op 2; chip `kota_hidup` di `DuniaCommands.CHIPS` +
grup `command/CheatHelp` "kota hidup")

| Sub | Fungsi |
|---|---|
| `warga info [npc\|nama]` | kartu warga di chat (id, umur, keluarga, hubungan, ingatan, suasana, kebiasaan) |
| `warga daftar [situs]` | jumlah per tahap umur, rumah tangga, kasur, penganggur, berakar |
| `warga tahun <n>` | majukan umur data semua warga tempat terdekat n Tahun Fana (lewat siklus penuh, peristiwa tercatat) |
| `warga hari <n>` | jalankan n Hari Warga sekarang |
| `warga nikah <a> <b>` / `lahir <a>` / `wafat <a>` / `pindah <a> <situs>` / `sakit <a>` | paksa peristiwa (upacara terjadwal hari ini, `--sekarang` memulai segera) |
| `warga kesan <a> <n>` / `kenangan <a> <jenis>` / `utang <a> <n>` | set ingatan penguji |
| `warga suasana <a> <kode>` / `kebiasaan <a> <id>` | pratinjau |
| `warga pohon <a>` | buka Pohon Keluarga |
| `bicara uji <topik> [syarat=nilai…]` | tampilkan ujaran terpilih dan semua kandidat dengan skor |
| `cakap info <npc>` | sifat HEXACO, PAD sekarang + label, 8 fakta paling menonjol (R, yakin, sumber), 5 topik dengan utilitas |
| `cakap fakta <npc> [tambah <predikat> <subjek> [objek] / lupa]` / `cakap emosi <npc> <peristiwa>` | atur pengetahuan/emosi |
| `cakap uji <niat> [fitur=nilai…] [n]` | rangkai n ujaran (id dan en), laporkan jumlah berbeda |
| `cakap kombinasi [niat]` | jumlah realisasi berbeda per niat (sasaran ≥ 10⁴ untuk niat umum) |
| `cakap obrol <npcA> <npcB> [jenis]` | paksa obrolan NPC-NPC, cetak keadaan sebelum/sesudah |
| `cakap jejak` | catat keadaan FSM, kandidat topik dan cek nada di chat selama sesi penguji |
| `cakap golden [tulis]` | jalankan/tulis ulang uji emas 42.5A.12 |
| `sebaran peta` / `zona` / `titik` / `simulasi <hari>` | 42.4A.8 |
| `ragam <warga>` / `ragam laporan [hari]` | 42.4A.8 |
| `kompat periksa [radius]` | struktur asing yang memotong lot/kavling/makam/kantor dan kavling `TIDAK_LAYAK` (42.12.1A) |
| `gosip daftar` / `gosip buat <templat>` / `gosip sebar <hari>` / `gosip jejak <id>` | gosip |
| `jalan <tujuan>` | jawaban Tanya Jalan dari NPC terdekat |
| `tolong buat <jenis> [warga]` / `tolong selesai` | permintaan |
| `ekonomi rumah [daftar\|beli <id>\|segel\|lepas]` | rumah |
| `ekonomi toko [buat\|jual <jam>\|laporan]` | simulasi penjualan n jam |
| `ekonomi harga [kategori] [stok <n>\|acara <id>]` | faktor dan sebab |
| `ekonomi investasi [minggu <n>]` / `gudang` / `kontrak [buat\|kirim\|tiba]` | — |
| `ekonomi pajak <tarif>` / `jabatan <pangkat>` / `perkara [buat <templat>\|putus <n>]` | — |
| `ekonomi pasar_hantu [buka\|tp\|razia]` | buka sekarang, teleport ke lokasi malam ini, razia |
| `ekonomi simulasi <pita> <hari>` | pendapatan/pengeluaran pemain aktif memakai keran 38 + 42 (harus ±25 % sasaran 38.M.1) |
| `acara rombongan [<id> sini]` / `pagi` / `ronda [geng <n>]` | panggil acara sekarang |
| `acara copet` / `tawuran` / `gerobak` / `api` / `ayam` / `anak_tersesat` | peristiwa acak di dekat penguji |
| `acara nikah` / `makam` / `lahir` / `zhuazhou` | upacara uji dengan warga acak |
| `acara musim <semi\|panas\|gugur\|dingin> [hari_raya]` | **pratinjau** suasana musim (tanpa mengubah waktu/cuaca) |
| `acara malam` / `wayang <naskah>` / `guanpu` | distrik malam |
| `hewan [semua\|<jenis>]` | barisan semua hewan 30 s (uji model) |
| `sekte kantor [buat\|pelamar <n>\|ujian]` / `pakta [buat\|serbuan\|gagal]` / `murid <tugas>` / `cabang <jenis>` | 42.8 |
| `tumbuh kavling [daftar\|tp <n>\|pilih <rencana>]` / `tahap [lanjut\|semua]` / `bahan isi` / `makmur <n>` | 42.9.4 |
| `tumbuh rusak_proyek` | gabungkan gugus nyata yang ada menjadi proyek (tidak membuat kerusakan) |
| `visual lentera` / `asap` / `panji <keadaan>` / `titik` | pratinjau klien (tanpa mengubah waktu) |
| `anggaran` | waktu per subsistem, jumlah entitas 42, ukuran data |
| `reset [warga\|ekonomi\|acara\|sekte\|tumbuh\|semua] [situs]` | hapus data 42 |

#### 42.14.4 Kitab Panduan bab 23 "Kota yang Hidup" (42V, `guide/GuideChapters`; 22 bila 42 dibangun sebelum 41, K10)

Langkah ditambah di akhir daftar (`step(22, nama, petunjuk, cek)`; `GATES`/`DISPLAY_ORDER` diperpanjang; tampil setelah bab 18-20
tempat): `sapa` (bicara dengan 5 warga berbeda), `kenal` (`adv:tempat/kota42_kenal`: warga menyebut namamu), `kabar`
(telusuri satu gosip ke sumbernya), `tolong` (`adv:tempat/kota42_tolong`), `upacara` (hadiri pernikahan atau pemakaman),
`rumah` (`adv:tempat/kota42_rumah`), `toko` (jual 10 barang lewat toko sendiri), `investasi`, `pasar_hantu`
(`adv:tempat/kota42_pasar_hantu`), `ronda`, `copet` (tangkap pencopet), `jabatan` (pangkat 2), `kantor` (terima satu calon dari
Kantor Rekrutmen), `pakta`, `kavling` (`adv:tempat/kota42_kavling`), `generasi` (`adv:tempat/kota42_generasi`: anak yang kau kenal
menjadi dewasa), `nada` (`adv:tempat/kota42_lidah_perak`: berhasil dengan 4 nada berbeda), `nguping` (`adv:tempat/kota42_nguping`:
mendapat kabar dari obrolan NPC-NPC), `janji` (`adv:tempat/kota42_janji`: menepati janji yang diingat warga); teks bab menjelaskan
bahwa warga punya watak, perasaan dan ingatan, bahwa nada bicaramu berakibat, dan bahwa mereka bergosip satu sama lain. Teks `kultivator.guide.c23.<nama>.title|body`, `kultivator.guide.chapter.23` + `.desc` di
`lang-src/kota_hidup_panduan.*`. Bab PANDUAN.md baru "74. Kota yang Hidup" (agen finish, CRLF).

#### 42.14.5 Advancement (`data/kultivator/advancement/tempat/kota42_*.json`, pola `docs/ADVANCEMENT_TEMPLATE.json`, parent
`kultivator:root`, `minecraft:impossible`, `Advancements.grant(player, "tempat/kota42_<nama>")`)

| Nama | Ikon | Kapan |
|---|---|---|
| `kota42_sapa` | `minecraft:bell` | bicara dengan 5 warga berbeda |
| `kota42_kenal` | `minecraft:name_tag` | warga menyapamu dengan nama |
| `kota42_sahabat` | `minecraft:poppy` | kesan +60 dengan satu warga |
| `kota42_gosip` | `minecraft:paper` | menelusuri gosip ke sumbernya |
| `kota42_tolong` | `minecraft:bundle` | menyelesaikan 10 permintaan |
| `kota42_nikah` | `minecraft:red_banner` | hadir di pernikahan dan memberi angpao |
| `kota42_makam` | `minecraft:white_banner` | hadir di pemakaman |
| `kota42_zhuazhou` | `minecraft:writable_book` | menyaksikan *zhuazhou* |
| `kota42_generasi` | `minecraft:clock` | anak yang kau kenal tumbuh dewasa |
| `kota42_cucu` | `minecraft:cake` | cucu warga mewarisi kesannya padamu |
| `kota42_rumah` | `minecraft:oak_door` | membeli rumah |
| `kota42_toko` | `minecraft:emerald` | toko sendiri menjual 100 barang |
| `kota42_investor` | `minecraft:gold_ingot` | bagi laba investasi pertama |
| `kota42_saudagar_kontrak` | `minecraft:chest_minecart` | menyelesaikan kontrak dagang 4 kali |
| `kota42_pejabat` | `minecraft:lectern` | pangkat jabatan 3 |
| `kota42_wali_kota` | `minecraft:golden_helmet` | menjadi Wali Kota |
| `kota42_hakim_jujur` | `minecraft:anvil` | 10 perkara tanpa suap |
| `kota42_pasar_hantu` | `minecraft:soul_lantern` | menemukan Pasar Hantu |
| `kota42_copet` | `minecraft:leather_boots` | menangkap pencopet |
| `kota42_api` | `minecraft:water_bucket` | memadamkan api kecil |
| `kota42_ronda` | `minecraft:lantern` | ikut ronda satu geng penuh |
| `kota42_rombongan` | `minecraft:jukebox` | menonton semua jenis rombongan |
| `kota42_wayang` | `minecraft:item_frame` | menonton teater bayang sampai akhir |
| `kota42_kucing` | `minecraft:cod` | Lao Hua mengikutimu |
| `kota42_kantor` | `minecraft:blue_banner` | menerima calon dari Kantor Rekrutmen |
| `kota42_akar_langit` | `minecraft:nether_star` | merekrut anak berakar HEAVENLY |
| `kota42_pelindung` | `minecraft:shield` | pakta perlindungan pertama |
| `kota42_bela_kota` | `minecraft:iron_sword` | memenangkan Panggilan Bela Kota |
| `kota42_cabang` | `minecraft:brewing_stand` | membuka cabang sekte |
| `kota42_kavling` | `minecraft:scaffolding` | kavling yang kau bantu selesai |
| `kota42_jalan_namaku` | `minecraft:oak_sign` | jalan diberi namamu |
| `kota42_bangun_kembali` | `minecraft:bricks` | menyelesaikan proyek bangun kembali |
| `kota42_dua_keluarga` | `minecraft:pink_petals` | menyelesaikan alur "Dua Keluarga di Jalan Sumur" |
| `kota42_lao_geng` | `minecraft:bell` | menyelesaikan "Lentera Terakhir Lao Geng" |
| `kota42_lidah_perak` | `minecraft:amethyst_shard` | berhasil memakai 4 nada berbeda (42.5A.7) |
| `kota42_nguping` | `minecraft:sculk_sensor` | mendapat kabar dengan menguping obrolan NPC-NPC |
| `kota42_janji` | `minecraft:lead` | menepati janji yang diingat dan ditagih warga |

(Semua ikon diverifikasi terhadap registri 26.3 oleh 42A.)

#### 42.14.6 Lang (fragmen 42; kunci identik id_id/en_us; Indonesia utama)

`kota_hidup` (umum, Sumber, pesan, config, perintah), `kota_hidup_warga` (42A: tahap, kebiasaan, wajah, kenangan, status;
nama warga **tidak** di lang: suku kata pinyin netral bahasa di `data/kultivator/kota/nama.json`), `kota_hidup_cakap` (42K: terminal tata
bahasa ≥ 2.400 kunci, dibuat `gen_kota_hidup_cakap.py`), `kota_hidup_cakap_lex` (42K: leksikon, dialek, tik), `kota_hidup_ragam`
(42S: nama kegiatan, debug), `kota_hidup_cerita` (42B: tokoh, alur, kisah Lao Shuo, perkara, tolong), `kota_hidup_ekonomi`
(42C), `kota_hidup_acara` (42D), `kota_hidup_sekte` (42E), `kota_hidup_tumbuh` (42F), `kota_hidup_ui` (42U),
`kota_hidup_suara` (dibuat `gen_voices.py`, 42V), `kota_hidup_panduan` (42V), `kota_hidup_subtitle` (42V).

---------------------------------------------------------------------------------------------------------------------

### 42.15 Modul, kepemilikan berkas, kontrak dan urutan

#### 42.15.1 Sebelas modul (paket baru `dev.kultivator.dunia.kota42`; berkas baru milik penuh, tidak bertumpuk)

| Label | Lingkup | Berkas baru (milik penuh) |
|---|---|---|
| **42A** Inti Warga | 42.0, 42.4, 42.13.1 `WargaData`, 42.13.2 kerangka payload, 42.14.1-42.14.3 kerangka, 42.14.5 JSON | `dunia/kota42/{KotaHidup (init), KotaConfig (7 bagian), KotaBus, KotaPayloads, KotaHidupCommands, KotaUji (akar + `daftar(sub, node)`), KotaTitik}`, `dunia/kota42/api/{WargaApi, WargaInfo, Kategori, KodeWajah, Kenangan, JenisUpacara, JenisHubungan, KotaGerakId, PenimpaJadwalKota}`, `dunia/kota42/warga/{Warga, WargaData, WargaGen, HariWarga, SiklusHidup, Hubungan, Ingatan, Suasana, Kebiasaan, Pekerjaan, PendudukTambahan, Migrasi, PerjalananWarga, ArsipWarga, NamaWarga, Berbakat, PenimpaWarga}`, data `kota/nama.json`, `tempat/kebiasaan/*.json`, `client/dunia/kota42/KotaHidupClient` (init + stub init modul klien lain), lang `kota_hidup`, `kota_hidup_warga`, semua advancement JSON |
| **42B** Bicara & Cerita | 42.5, 42.10 (data cerita) | `dunia/kota42/bicara/{BicaraWarga, TanyaJalan, PermintaanWarga, Selera, Pinjaman, TujuanKota (tujuan quest baru), TokohKota}`, data `tempat/bicara/*.json`, `tempat/tolong/*.json`, `tempat/selera.json`, `tempat/perkara/*.json` (isi; mesin 42C), `tempat/quest/kota42_*.json`, `tempat/dialog/kota42_*.json`, `tempat/tokoh/kota42.json`, `tempat/kabar/kota42.json`, `tempat/wayang/*.json` (naskah; pemutar 42V), `tools/cerita_isi_kota42.py`, lang `kota_hidup_cerita` (kalimat percakapan dari 42K) |
| **42K** Mesin Percakapan | 42.5A, penyimpanan gosip 42.5.4 | `dunia/kota42/cakap/{Cakap (fasad), CakapData, CakapReload, Kepribadian, Emosi, Penilaian, Pengetahuan, Fakta, Predikat, Lupa, GosipWarga, Topik, PerencanaTopik, KeadaanPercakapan, Giliran, Kilasan, Niat, Nada, PenilaiNada, TataBahasa, TipeSlot, Realisasi, Ujaran, AntiUlang, Dialek, Tik, ObrolanWarga, CakapUji}`, `client/dunia/kota42/cakap/{UjaranClient, GelembungUjaran}`, data `tempat/percakapan/**` (kepribadian, emosi, predikat, topik, niat, nada, tata/*, dialek, tik, obrolan/*, golden/*), `tools/kota_cakap_sumber/**`, `tools/gen_kota_hidup_cakap.py`, `tools/cek_cakap.py`, lang `kota_hidup_cakap`, `kota_hidup_cakap_lex` |
| **42S** Sebaran & Ragam | 42.4A | `dunia/kota42/ragam/{RagamData, PetaZona, Kapasitas, Penyeimbang, PemilihTubuh, RencanaHarian, KumpulanKegiatan, Selingan, Belokan, KegiatanKelompok, IramaMingguan, MetrikRagam, RagamUji}`, `client/dunia/kota42/ragam/PetaPanasDebug`, data `tempat/ragam/{zona.json, selingan.json, belokan.json, peran/*.json}`, lang `kota_hidup_ragam` |
| **42C** Ekonomi Kota | 42.6 | `dunia/kota42/ekonomi/{KotaEkonomiData, RumahKota, KunciRumah, TokoPemain, LapakKota, RakDagangBlock, RakDagangBlockEntity, MejaKasirBlock, PapanNamaTokoBlock, HargaKota, StokKota, UsahaWarga, Investasi, GudangKota, KontrakDagang, RumahDagang, PajakKota, Jabatan, Perkara, PasarHantu, LapakHantuEntity, TendaLapakEntity, KotaEkonomiItems, KotaEkonomiBlocks}`, lang `kota_hidup_ekonomi` |
| **42D** Kehidupan & Acara | 42.7, 42.9.6 (server), 42.10 jadwal tokoh | `dunia/kota42/acara/{KotaAcaraData, KalenderKota, Rombongan, RutinitasPagi, Ronda, PeristiwaJalan, Copet, Perkelahian, GerobakTerguling, ApiKecilEntity, BarangTercecerEntity, MusimKota, Upacara, UsunganPetiEntity, MejaJamuanEntity, PanggungKelilingEntity, BarongsaiEntity, TendaPasarEntity, DistrikMalam, MejaDaduEntity, Guanpu, LayarBayangEntity, TeaterBayang, KotaAcaraEntities, KotaAcaraItems}`, `dunia/kota42/hewan/{HewanKota, KucingKotaEntity, AnjingKotaEntity, AyamKotaEntity, KawananMerpatiEntity, KudaTambatEntity, UntaTambatEntity, KeledaiKotaEntity, HewanEntities}`, lang `kota_hidup_acara` |
| **42E** Sekte & Kota | 42.8 | `dunia/kota42/sekte/{KotaSekteData, KantorRekrut, BatuUjiEntity, PaktaLindung, SerbuanKota, TugasKotaMurid, CabangKota, PapanCabangBlock}`, lang `kota_hidup_sekte` |
| **42F** Tumbuh & Bangun Kembali | 42.9.4-42.9.5 (server) | `dunia/kota42/tumbuh/{KotaTumbuhData, Kavling, Kemakmuran, RencanaTumbuh (membaca NBT tahap lewat `StructureTemplateManager`), PekerjaanTumbuh, BahanKota, BangunKembali, TendaPengungsiEntity, TuguKenangan}`, lang `kota_hidup_tumbuh` |
| **42W** Worldgen | 42.9.1, 42.12.1 | `tools/kota_hidup_kit.py`, `tools/gen_kota_tumbuh.py`, NBT/JSON baru dan dibuat ulang di `data/kultivator/structure/**`, `worldgen/template_pool/**`, `data/kultivator/structure/kota_tumbuh/**` |
| **42U** Layar | 42.11 | `dunia/kota42/ui/{KotaLembar, LembarWarga, LembarPohon, LembarBuku, LembarRumah, LembarToko, LembarInvestasi, LembarGudang, LembarJabatan, LembarPerkara, LembarPasarHantu, LembarGuanpu, LembarKantor, LembarPakta, LembarCabang, LembarKavling, LembarUpacara}` (pembangun lembar; penangan aksi memanggil API modul), `client/dunia/kota42/ui/**` (layar, `KotaHub` kartu hub, HUD kejar dan irama), `tools/gen_kota_hidup_ui.py`, `textures/gui/kota42/**`, lang `kota_hidup_ui` |
| **42V** Visual, Suara, Panduan | 42.9.2-42.9.3, 42.12.4-42.12.7, 42.14.4 | `client/dunia/kota42/{fx/LenteraKota, fx/AsapDapur, fx/PanjiKota, fx/Petasan, fx/Jelaga, fx/BercakCahaya, fx/WayangSiluet, fx/MelodiJalan, model/**, render/** (semua renderer entitas/blok 42), gerak/KotaPoses, KotaSuasanaClient}`, `tools/gen_kota_hidup_assets.py`, `tools/gen_kota_hidup_suara.py`, `assets/kultivator/{models,textures,sounds}/**/kota42/**`, `data/kultivator/kota/melodi/*.json`, lang `kota_hidup_panduan`, `kota_hidup_subtitle` |

#### 42.15.2 Pemilik berkas lama (satu pemilik selama 42; semua suntingan aditif kecuali penyatuan yang disebut)

| Berkas lama | Pemilik | Suntingan |
|---|---|---|
| `dunia/tempat/hidup/{PendudukData, Jadwal, TempatHuni, Pemanggil, TubuhPenduduk, HidupCommands}` | 42A | kait 42.4.14 (`PendudukData` penyatuan) — **hanya setelah** `perbaikan-palsu` di-commit |
| `dunia/kota/NpcRole.java` | 42A | 13 peran di akhir enum (42.12.2) |
| `dunia/DuniaContent.java`, `dunia/DuniaCommands.java`, `command/CheatHelp.java` | 42A | `KotaHidup.init()`, chip `kota_hidup`, grup cheat |
| `KultivatorClient.java` (CRLF) | 42A | `KotaHidupClient.init()` |
| `dunia/tempat/cerita/TempatDialog.java` | 42B | satu baris `BicaraWarga.boleh` (42.5.1) + satu baris tablet tambahan di lembar sapa |
| `dunia/tempat/cerita/KabarTempat.java` | 42K | satu baris `GosipWarga.dariKabar(...)` di `pemicu` |
| `dunia/tempat/cerita/Kalimat.java`, `dunia/kota/NpcLife.java` (obrolan 4 baris) | 42K | `Kalimat.sapa` → `Cakap.sapa` bila `mesinLanjut`; obrolan `NpcLife` diserahkan ke `ObrolanWarga` (42.5A.11) |
| `dunia/DuniaMarkers.java` (+ ikon) | 42B | jenis `kota/tujuan` (sementara 5 menit) bila belum ada |
| `dunia/kota/{Toko, PasarGelap, KedaiTeh}.java`, `dunia/tempat/ekonomi/{TokoSenjata, Hidangan, Penginapan, LapakSekte, KasKota}.java`, `dunia/tempat/kampung/PasarDesa.java`, `dunia/tempat/tema/bersama/Dagang.java` | 42C | satu pengali `HargaKota.faktor` per harga; `LapakSekte.peluangLaku` publik; `PasarGelap.dagangan` publik; `KedaiTeh` token → sandi malam; `KasKota.pajak(...)` |
| `dunia/tempat/ekonomi/GerobakEntity.java`, `dunia/tempat/tema/bersama/TanduEntity.java`, `dunia/tempat/kotabesar/KotaBesarKalender.java` | 42D | data `TERGULING`; varian warna tandu; daftar acara 42 |
| `dunia/tempat/kotabesar/{GelombangBinatang, HariGerbang}.java`, `dunia/tempat/kampung/AnakBerbakat.java`, `sekte/mandiri/api/MandiriApi.java` | 42E | `GelombangBinatang.mulai(level, situs, kekuatan, jenis)` publik; `HariGerbang`/`AnakBerbakat` membaca `WargaApi.berbakat`; `MandiriApi.daftarTugas` bila katalog 39 tertutup |
| `dunia/tempat/perbaikan/{PerbaikanApi, PekerjaanPerbaikan, Cetak}.java` | 42F | `PerbaikanApi.kerjakan(Pekerjaan)`, `PerbaikanApi.terimaKotak(level, BoundingBox)` (memanggil `Cetak.terima`), mesin regu menerima pekerjaan non-perbaikan — **hanya setelah** `perbaikan-palsu` di-commit |
| `tools/gen_tempat_kota_besar.py`, `tools/kota_besar_kit.py`, `tools/gen_dunia_kota.py`, `tools/gen_tempat_kampung.py`, `tools/gen_tempat_tema_*.py`, `tools/gen_tempat_jalan.py`, `tools/cek_tempat.py` | 42W | 42.12.1 |
| `client/tempat/ui/kit/TempatWarna.java` | 42U | satu konstanta hijau Pasar Hantu |
| `sosok/GerakId.java`, `client/sosok/gerak/{GerakKatalog, PropKatalog}.java`, `sosok/PropId.java` | 42V | batas 42.12.5 |
| `tools/gen_sosok_resep.py`, `tools/gen_sosok_bagian_busana.py`, `tools/gen_tempat_gear.py`, `client/.../KotaGear.java` | 42V | busana, rupa, pita gear 48-63 |
| `guide/GuideChapters.java` | 42V | bab 23 |
| `tools/gen_voices.py`, `voice/VoiceEvents.java` | 42V | 102 klip (78 baris + 24 kata seru), grup `KOTA42` (generasi hanya F42) |
| `client/dunia/tempat/hidup/RiuhKota.java` | 42V | suasana `malam_meriah`, `upacara` |
| `docs/DESIGN.md`, `docs/PANDUAN.md`, `docs/CREDITS.md`, `README` | F42 | ringkasan 42, bab 74, kredit suara buatan |

Berkas 36/37/38/39/40 lain baca saja; yang dibutuhkan masuk "Butuh dari modul lain" di berkas progres.

#### 42.15.3 Kontrak antarmodul (nama tetap; stub di langkah 1 42A)

* **C42.A1 `WargaApi`** (statis, server): `@Nullable Warga warga(long situs, int pendudukId)`, `@Nullable Warga dari(KultivatorNpc)`,
  `@Nullable Warga byId(int)`, `List<Warga> wargaTempat(long situs)`, `Penduduk terapkan(long situs, Penduduk p)`, `int keadaan(long
  situs, int id, long now)`, `Component namaPapan(long situs, Penduduk p)`, `int umur(Warga)`, `int usiaSosok(Warga)`, `int wajah(Warga,
  long now)`, `Ingatan ingatan(Warga, UUID)`, `void ubahKesan(Warga, ServerPlayer, int delta, String sebab, @Nullable Kenangan
  kuat)`, `void peristiwaPemain(ServerPlayer, long situs, String jenis, Object... isian)`, `List<Warga> berbakat(long situs)`,
  `Halls.CandidateSpec jadikanCalon(Warga, UUID owner, String sumber)`, `@Nullable Warga keluargaMurid(UUID soul)`, `int
  suasanaTempat(long situs)`, `void daftarPenimpa(PenimpaJadwalKota)`, `void pekerjakan(Warga, UUID majikan, String tugas)`.
* **C42.A2 `KotaBus`**: `LAHIR`, `NIKAH`, `WAFAT`, `PINDAH(asal, tujuan)`, `KERJA_BERUBAH`, `GOSIP_LAHIR`, `UPACARA(fase)`,
  `HARGA_BERUBAH`, `KAVLING(tahap)`, `SERBUAN(mulai/akhir, menang)`, `PAKTA_BERUBAH`, `RUMAH_BERUBAH`.
* **C42.B1** `BicaraWarga.lembar(player, npc)`, `PermintaanWarga.buat(warga, jenis)`, `TanyaJalan.jawab(npc, tujuan)`.
* **C42.K1** `Cakap.{sapa(player, npc), sesi(player, npc) → SesiCakap, pilih(sesi, niat, nada) → Hasil, ujaran(warga, niat, konteks)
  → Ujaran, peristiwa(warga, jenisPeristiwa, sasaran, intensitas), fakta(warga) → List<Fakta>, tahu(warga, Fakta, sumber),
  janji(warga, player, predikat, batasHari), obrol(wargaA, wargaB, jenis?)}`; `GosipWarga.lahir(level, situs, templat, subjek…)`;
  `Penilaian.daftar(jenis, ΔPAD)`; format data 42.5A.8 dibekukan di langkah 1.
* **C42.S1** `Penyeimbang.pilihTitik(huni, warga, jenis) → Tujuan`, `PemilihTubuh.pilih(huni, calon, anggaran)`,
  `RencanaHarian.hari(warga, hari)`, `MetrikRagam.laporan(situs, hari)`; kait 42A di `Pemanggil`/`TempatHuni`/`TubuhPenduduk` (42.4.14).
* **C42.C1** `HargaKota.faktor(server, situs, Kategori)`, `Kategori.dari(ItemStack)`, `RumahKota.{milik(UUID), diklaim(level, pos),
  rumahDi(level, pos)}`, `TokoPemain.di(level, pos)`, `UsahaWarga.usaha(situs)`, `Jabatan.pangkat(UUID, situs)`,
  `PasarHantu.lokasi(situs, hari)`, `KotaEkonomi.kasKota(situs)`.
* **C42.D1** `KalenderKota.daftar(situs, Acara)`, `Upacara.jadwalkan(JenisUpacara, Warga…)`, `PeristiwaJalan.picu(level, situs, jenis)`,
  `HewanKota.panggil(level, titik)`.
* **C42.E1** `PaktaLindung.pelindung(situs)` (UUID atau null), `KantorRekrut.kantor(situs)`, `TugasKotaMurid.tugas(soul)`,
  `CabangKota.cabang(situs)`. Bila 41 ada: `GabungApi.sekte(uuid)` dibaca lewat `try`-kelas (kontrak lunak) untuk menolak pakta
  atas nama anggota tanpa aula.
* **C42.F1** `Kemakmuran.nilai(situs)`, `Kavling.daftar(situs)`, `BangunKembali.aktif(situs)`, `BahanKota.stok(situs)`.
* **C42.U1** kunci lembar `KotaLembar` (javadoc per layar) dibekukan di akhir langkah 1.
* **C42.V1** id klip 350-449, 450-459 (42S; cadangan 460-469) dan prop 170-239 dibekukan di `dunia/kota42/api/KotaGerakId` (aman di dua sisi).

#### 42.15.4 Ketergantungan dan urutan kerja

```
Prasyarat: perbaikan-palsu (38PR + kedai/pasar ramai) selesai dan di-commit; 38/38.T, 39/39.F, 40 sudah ada (ya)
  |                                     (41 opsional; bila dibangun dulu, 42 = bab 23)
G0  42A langkah 1: semua api/*, stub init semua paket server+klien, WargaData kosong, payload terdaftar, config 7 bagian,
    advancement JSON, KotaLembar kunci, KotaGerakId, NpcRole +13, kait hidup (PenimpaJadwal, sumber penduduk/titik/penolak)
    -> F42-0: build B0
  |
G1  42A (sisa) | 42W | 42V | 42C | 42K | 42S   (paralel; 42C/42K/42S memakai WargaApi stub; 42W mulai setelah perbaikan
    tabrakan worldgen agen lain di-commit, 42.12.1A)
  |  42W: generator + cek_tempat + pratinjau; 42V: aset, suara buatan, pratinjau PNG; F42-1: build B1 + harness wg
  v
G2  42B | 42D | 42E | 42F | 42U               (paralel; butuh Warga nyata dari 42A, NBT tahap dari 42W, rumah dari 42C)
  v
F42 finish: "Butuh dari modul lain", semua generator, VOICEVOX 102 klip, cek_tempat/cek_sosok, harness run_38/run_all/
    PlanCheckTema, mergeLang tanpa ganda, build B2 (`compileJava compileClientJava jar --no-daemon`), docs (DESIGN 42 ringkas,
    PANDUAN bab 74, CREDITS, README, docs/uji/FASE-42.md)
  |
D42 detail pass (opsional, 5 agen: D-42A generasi 200 hari simulasi + keseimbangan, D-42B baris dialog & gosip, D-42D upacara
    & rombongan di layar, D-42W interior dan kavling per gaya, D-42V model & animasi hewan/barongsai,
    D-42K korpus tata bahasa + uji emas, D-42S simulasi sebaran 7 hari di ibu kota dan kota tema)
```

Ketergantungan keras: 42B/42D/42E/42K/42S butuh `WargaApi` (42A); 42B butuh `Cakap` (42K) untuk lembar; 42E butuh `RumahKota` (42C) untuk cabang; 42F butuh NBT tahap (42W)
dan kait perbaikan; 42U butuh lembar 42A-42F (kunci dibekukan G0); 42V hanya butuh 40. Berkas progres:
`docs/progres/kota42-42A0.md`, `kota42-42A.md`, …, `kota42-F42.md`.

---------------------------------------------------------------------------------------------------------------------

### 42.16 Keputusan terbuka (saran bawaan dicetak tebal)

| # | Pertanyaan | Pilihan | Saran bawaan |
|---|---|---|---|
| K1 | Kecepatan generasi (`hariPerTahun`) | 1 (sangat cepat, ±76 hari per generasi) / **2 (±150 hari)** / 4 (±300) / 8 | **2**: anak yang kau kenal dewasa dalam ±32 hari, kota berganti generasi dalam satu dunia main |
| K2 | Pencopet boleh mengambil barang selain batu roh? | **hanya batu roh (kecil, bisa dikejar/ditebus)** / juga satu tumpukan barang tak terpakai | hanya batu roh |
| K3 | Warga wafat karena usia? | **ya (dengan pemakaman)** / tidak (hanya pensiun dan pindah) | ya |
| K4 | Rumah dijual di distrik istana/awan ibu kota | **ya (× 3 harga)** / tidak | ya |
| K5 | Jabatan tertinggi pemain | **Wali Kota di kota 34/tema; ibu kota hanya Menteri Kehormatan** / juga ibu kota / tanpa jabatan | seperti kiri |
| K6 | Serangan sekte NPC ke kota boleh merusak blok nyata lewat `WorldBreaker`? | **tidak (hanya suasana, blokade, pengungsi)** / ya, terbatas radius 6 di luar zona inti | tidak |
| K7 | Bekas jelaga visual (klien, tanpa blok) setelah serangan | **ya, digosok warga 3 hari** / tidak | ya |
| K8 | Pemain menikahi warga | **tidak di 42 (rancangan terpisah nanti)** / ya | tidak |
| K9 | Pasangan sesama jenis di antara warga (anak lewat anak angkat) | ya / **tidak** | **DIPUTUSKAN 2026-10-24: tidak** (pengguna) |
| K10 | Urutan bangun dengan 41 | **41 dulu, 42 = bab 23** / 42 dulu (bab 22) | 41 dulu |
| K11 | Kota tumbuh hanya di kavling yang direncanakan worldgen | **ya (aturan blok; kota "penuh" setelah semua kavling)** / boleh meluas ke luar tembok lewat rencana tambahan | ya |
| K12 | Pasar Hantu di kampung | **tidak (kota ke atas dan pos dagang)** / ya | tidak |
| K13 | Upeti perlindungan | **NB dari Kas Kota + barang surplus** / hanya barang / hanya renown | NB + barang |
| K14 | Copet dan perkelahian juga terjadi saat pemain tidak dekat (hanya gosip) | **ya, sebagai data + gosip** / hanya saat pemain dekat | ya |
| K15 | Tampilkan peluang berhasil nada bicara | **tidak, hanya isyarat raut wajah dan kalimat** / tampilkan persen | tidak (lebih alami; `tampilPeluang` untuk yang mau) |
| K16 | Pemilihan tubuh di sekitar pemain | **proporsional per zona (merata)** / terdekat dulu (perilaku lama) | proporsional |
| K17 | Gelembung obrolan NPC-NPC | **tampil ≤ 12 blok, maks 4 obrolan per tempat** / semua obrolan dalam 32 blok / mati | seperti kiri |

---------------------------------------------------------------------------------------------------------------------

### 42.17 Daftar uji (Indonesia; disalin ke `docs/uji/FASE-42.md` oleh F42)

**Uji terima (harus terlihat oleh penguji):**
1. Dunia baru, ibu kota: `uji kota_hidup warga daftar` → 650 warga bernama dengan rumah tangga bermarga sama, umur sesuai peran,
   2-4 hubungan; papan nama tubuh = nama warga; anak berskala anak, tetua berpostur tua.
2. Klik kanan **WARGA kota 34, PENJAGA, petani kampung, kuli**: semua membuka dialog warga dengan baris yang cocok jam, cuaca
   (uji hujan alami atau `bicara uji`), suasana; peran lama 34 tetap membuka lapak lamanya lewat tablet Jasa.
3. Perkenalkan diri → besok warga itu menyapa dengan nama; selamatkan dari api → baris kenangan; beri teh ke pecinta teh (+8) dan
   ke pemabuk (−5).
4. `warga tahun 20` di kampung: pernikahan terjadwal (arak-arakan tandu, tiga sembah, jamuan), kelahiran (telur merah, pesta
   sebulan, *zhuazhou* dengan bayi merangkak memilih benda), wafat (rumah duka putih, usungan 8 pemikul, nisan bernama di makam);
   Pohon Keluarga 3 generasi benar.
5. `warga tahun 40`: anak yang dikenal pemain dewasa, mengambil slot kerja orang tua atau magang; cucu mewarisi ½ kesan
   (`kota42_cucu`).
6. Rumah sesak → pasangan muda pindah ke kota lain: tubuh pelancong dengan buntelan di jalan; di kota tujuan mereka mengenali
   pemain.
7. Gosip: tolong warga di pasar → 2 hari kemudian Tiga Bibi Sumur membicarakannya; `[Tanya siapa yang bilang]` menelusuri rantai;
   satu lompatan "dibumbui".
8. Tanya Jalan: menunjuk (klip `tunjuk`) ke Rumah Uang dengan arah dan patokan; `[Tandai]` memberi panah kompas; anak menjawab
   "tanya Bibi Wang".
9. Kebiasaan: pemabuk di kedai arak malam lalu tidur di bangku; dua kakek main catur dengan penonton; ibu-ibu di sumur; kakek
   menggantung sangkar; tai chi pagi berbaris tersinkron.
10. Suasana: festival → wajah senang; Gelombang Binatang → takut dan masuk rumah; pemakaman → keluarga sedih berbaju putih.
11. Rumah: beli rumah sedang (harga sesuai faktor), taruh dan bongkar blok di dalamnya **tanpa** tagihan dan tanpa perbaikan 38.R;
    tukang tidak masuk; kunci rumah teleport dengan kanal 3 s; sewa habis → segel 14 hari → isi wadah ke Barang Sitaan, furnitur
    pemain tetap (cetak biru menerima).
12. Toko: rak dagang menampilkan barang dan harga; pegawai warga di kasir; pembeli bertubuh datang, melihat rak, membayar,
    pergi membawa barang; batas harian dan pajak 5 % di laporan.
13. Harga: jual 64 barang sejenis → harga turun (banjir pasar); serangan → senjata/obat naik; gugur → pangan turun; tooltip sebab
    benar di semua toko 34/38/42; beli-kembali ≤ 0,5 harga jual.
14. Investasi: tanam di bakmi, `ekonomi investasi minggu 4` → laba bervariasi dalam batas; pemilik wafat → ahli waris lanjut atau
    saham dibayar.
15. Gudang + kontrak: kafilah gerobak terlihat berangkat dari gudang, tiba, bayaran masuk, Nama Dagang naik; laba ikut batas Dagang.
16. Jabatan: naik ke Hakim Pembantu, sidang perkara dengan saksi dan bukti gosip; suap → Infami +2 dan audit; Wali Kota mengubah
    pajak → suasana dan Kas berubah.
17. Pasar Hantu: sandi dari kedai teh menunjuk lokasi yang benar malam itu (01:00-05:00), lapak bertopeng, barang curian pemain
    bisa ditebus, razia membuat lapak lenyap.
18. Rombongan: akrobat (piramid 3 tubuh), barongsai, Lao Shuo bercerita dengan balok kayu, Ah Qin bermain erhu (melodi
    terdengar), penjual obat; rombongan berpindah ke kota lain sesuai gosip.
19. Pagi: ayam berkokok, pasar pagi dengan tenda, anak berbaris di belakang guru dan menghafal (suara); ronda malam memukul gong
    sesuai geng dan berseru (suara), pemain ikut satu geng.
20. Peristiwa: dicopet di kerumunan → kejar 30 s → tangkap; perkelahian dilerai; gerobak terguling diangkat; api kecil menjalar
    lalu dipadamkan rantai ember — **tidak ada blok berubah** (cek `uji tempat cetak periksa` 0 perbedaan).
21. Hewan: kucing tidur di atap, anjing menggonggong malam, ayam kabur, merpati terbang serempak, kuda di tambat, unta di oasis;
    `uji kota_hidup hewan semua` menampilkan semua model dengan animasi.
22. Musim (`acara musim … hari_raya`): Tahun Baru (kuplet, 福 terbalik, petasan, angpao anak), Qingming (sapu makam), Pertengahan
    Musim Gugur (menatap bulan), Laba (bubur dibagikan), warung sup musim dingin.
23. Kantor Rekrutmen: pelamar dari warga berakar, uji akar dengan batu bercahaya, uang restu, adegan pamit memeluk orang tua; calon
    masuk daftar calon aula dengan nama dan akar yang sama.
24. Pakta: rebut dari sekte NPC lewat tanding utusan; panji lambang sekte pemain di gerbang; Panggilan Bela Kota dengan gelombang
    nyata; murid `JAGA_KOTA` berdiri di gerbang dan ikut ronda; cabang Toko Pil dengan rak/kasir aktif.
25. Kota tumbuh: `tumbuh makmur 70` + `bahan isi` → kavling dibangun 5 tahap oleh regu tukang dengan perancah; blok hanya lewat
    `WorldBreaker.submitRestore` (log alasan "tumbuh"); blok pemain di kavling tidak pernah ditimpa; peresmian barongsai; keluarga
    baru masuk; 38.R kemudian merawat bangunan itu.
26. Bangun kembali: kerusakan nyata > 200 blok (pemain membongkar) → proyek, tenda pengungsi, donasi, tukang pendatang, tugu
    kenangan; serangan tanpa kerusakan nyata **tidak** membuat proyek perbaikan.
27. Distrik malam: lentera merah, kedai arak dengan suit jari, rumah judi Guanpu (EV tertulis jujur), teater bayang dengan siluet
    bergerak dan suara.
28. Lentera kota menyala 18:00 bergelombang dari gerbang, asap cerobong pada jam makan, panji berubah saat pernikahan (囍) dan
    pemakaman (奠).
29. Semua layar 42.11 pada skala GUI 2/3/4 tanpa tumpang tindih dan bisa dipakai dengan keyboard saja.
30. `uji kota_hidup anggaran`: rata-rata < 0,5 ms/tick semua 42; entitas 42 ≤ 96; Hari Warga ibu kota < 1 ms.
31. Semua sakelar `aktif = false`: perilaku berhenti, blok/item/entitas tetap terdaftar, perilaku 38 kembali persis.
32. **Mesin percakapan**: dua warga berwatak berbeda (penakut vs sombong) menjawab topik sama dengan register, kata dan gestur
    berbeda; `cakap uji SAPA 200` menghasilkan ≥ 150 kalimat berbeda tanpa pengulangan berturut; semua teks berasal dari kunci
    fragmen lang (tidak ada teks buatan model bahasa).
33. Emosi: hina warga → wajah marah dan nada ketus, meluruh dalam ±2 jam dunia; hadiah → senang; serangan → takut.
34. Pengetahuan: warga saksi copet menceritakannya; warga yang hanya mendengar menyebut sumbernya ("kata Bibi Zhang"); fakta yang
    tidak diulang terlupa setelah beberapa hari (`cakap info`).
35. Kilas balik: janji `[Aku akan membawa obat]` → hari berikut warga menagih dengan ungkapan waktu yang benar ("kemarin"); menepati →
    terima kasih + kesan; ingkar → kecewa + gosip.
36. Nada: sopan/akrab/merayu/mengancam/menyuap/bercanda masing-masing berhasil dan gagal sesuai sifat; ancaman yang disaksikan di
    ibu kota menambah poin kejahatan; suap ke warga ber-H tinggi ditolak dan digosipkan.
37. Obrolan NPC-NPC: gelembung bergantian dengan gestur; gosip benar-benar berpindah (`cakap info` pendengar), debat menurunkan
    hubungan, merayu menaikkan; menguping memberi catatan kabar; menyela obrolan memulai sesi dengan topiknya.
38. Dialek: warga Kota Pelabuhan memakai sapaan pelaut, Kota Gunung "Dermawan"; register kuno saat pemain ≥ 3 ranah di atas.
39. Uji emas `cakap golden`: ≥ 40 berkas lulus; mengulang dengan benih sama menghasilkan keluaran identik.
40. **Sebaran**: `sebaran peta` di ibu kota jam 09, 12, 20, 23 menunjukkan pasar/rumah makan/kedai/rumah sebagai pusat sesuai jam
    sementara distrik lain tetap berpenghuni; `ragam laporan 7` memenuhi semua sasaran 42.4A.8 (≤ 35 % tubuh di satu zona, Gini
    ≤ 0,35, zona kosong ≤ 10 %).
41. Pemain berdiri di satu sudut kota: tubuh tersebar ke semua zona yang terlihat (tidak menumpuk di dekat pemain); tidak ada dua
    tubuh tak berinteraksi berjarak < 1,5 blok.
42. **Ragam**: satu warga diikuti 3 hari (`ragam <warga>`): ≥ 5 kegiatan berbeda per hari, rencana hari ke-2 berbeda dari hari ke-1,
    selingan (seka keringat, menoleh, melirik pemain) terlihat, satu belokan (jajan/elus kucing/tonton pengamen), keluarga jalan sore.
43. **Kompat**: dunia baru dengan paket mod pengguna: `kompat periksa` kosong di 3 ibu kota, 3 kota tema, 5 kota 34, 10 kampung;
    kavling dekat struktur asing ditandai `TIDAK_LAYAK` dan tidak dibangun.

**Daftar centang (untuk FASE-42.md):**
- [ ] Warga tersimpan: identitas, nama generasi, akar, watak, kebiasaan; slot `PendudukGen` + slot tambahan
- [ ] Siklus hidup: menikah, lahir, anak angkat, sakit, wafat, pensiun, warisan; arsip dan nisan
- [ ] Hubungan 6 jenis + perilaku terlihat; keluarga terlihat (gandeng cucu, makan bersama)
- [ ] Pekerjaan, magang, ganti kerja; pindah antar-kota dengan tubuh pelancong
- [ ] Anak berbakat → Kantor Rekrutmen / HariGerbang / AnakBerbakat dari satu sumber
- [ ] Ingatan per pemain + kesan + kenangan kuat + warisan kesan
- [ ] Suasana 7 kode wajah; akibat di harga, dialog, pindah
- [ ] 32 kebiasaan sebagai penimpa jadwal data
- [ ] Dialog untuk semua orang sipil; Tanya Jalan; Kabar; Tolong 16 jenis; Beri dan pinjaman
- [ ] Mesin percakapan tanpa LLM: HEXACO, PAD + penilaian, fakta + kurva lupa + keyakinan, topik utilitas, FSM + kilas balik + janji,
      6 nada, tata bahasa bertipe (register, dialek, tik, anti-ulang) ≥ 900 bingkai / ≥ 2.400 terminal, 8 jenis obrolan NPC-NPC
      yang mengubah keadaan, uji emas ≥ 40, `cek_cakap.py` bersih
- [ ] Sebaran: zona + kapasitas + target per jam, Penyeimbang + reservasi + luapan, pemilih tubuh proporsional, metrik lulus
- [ ] Ragam: ≥ 10 kegiatan per peran umum, rencana harian, selingan, belokan, kelompok, irama mingguan/musim
- [ ] Kompat mod dunia 42.12.1A: tanpa struktur/placement baru, keep-out dihormati, periksa struktur asing sebelum tiap tahap
- [ ] Gosip lahir dari peristiwa nyata, menyebar lewat hubungan dan titik kumpul, dibumbui, ditelusuri
- [ ] Rumah klaim pemain (tanpa perbaikan/tagihan di dalam), kunci rumah, segel, cetak biru menerima
- [ ] Toko pemain: blok rak/kasir/papan, pegawai, pembeli bertubuh, batas keran
- [ ] `HargaKota` dipakai semua penjual; 12 kategori; lumbung penstabil
- [ ] Investasi, gudang, kontrak (batas Dagang bersama), pajak, 5 jabatan, perkara, suap/audit
- [ ] Pasar Hantu berpindah tiap malam; barang curian; razia
- [ ] 6 rombongan; rutinitas pagi; ronda 5 geng; 6 peristiwa acak tanpa blok; 7 hewan; 4 musim + hari raya; 3 upacara
- [ ] Kantor Rekrutmen, pakta + Panggilan Bela Kota, murid JAGA/KERJA_KOTA, cabang 3 jenis
- [ ] 24 kit interior, titik baru, kavling, makam, distrik malam; TplCheck/cek_tempat bersih; harness hijau
- [ ] Kavling 5 tahap lewat `WorldBreaker.submitRestore` saja; tidak menimpa blok pemain; `Cetak.terima` saat selesai
- [ ] Bangun kembali hanya untuk kerusakan nyata; pengungsi; tugu kenangan klien
- [ ] Lentera, asap, panji, petasan (tanpa `FireworkRocketEntity`), jelaga (K7), melodi prosedural, wayang
- [ ] 17 entitas, 4 blok, ±20 item, 13 peran, klip 350-449, prop 170-239, busana/rupa baru
- [ ] 102 klip suara Jepang (78 baris + 24 kata seru), bab Kitab Panduan 23, 37 advancement, fragmen lang identik
- [ ] Sibuk di semua peristiwa; tidak ada blok runtime selain `WorldBreaker`; tidak ada LightningBolt/cuaca/waktu/tick-rate
