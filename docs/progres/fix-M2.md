# fix:M2 Sibuk, Tempat Aman & Dunia Tersambung

Rencana: `audit-integrasi.md` bagian 3 M2. Temuan: T3, T4, T7, T12 (dunia), S2, S18, S19, S20, S21 (payload), S22 (dunia), R15.
Keputusan pengguna "Ubah angkanya": hadiah baru S22 dan batas tekanan harian disetujui.

## Checklist
- [x] 1a `util/Sibuk.java` (register/registerEnemy/why/whyExcept/addProvider/enemyRefusal/enemyTaken, DAILY_ENEMY_CAP 3, RETRY_TICKS 200; daftar bawaan lazy)
- [x] 1b pendaftar Sibuk (bawaan di Sibuk.ensureDefaults; Ambushes 'sergapan', Saingan 'saingan' via registerEnemy; SectRaids 'serbuan' enemy) (Breakthrough, Tribulation, HeartDemons, DuelRules, DuelSessions, Sparring, Alam, Purba, StarCalamity, Abadi, InheritanceTrials, Creation, serbuan, jiwa, Menara) - didaftarkan dari berkas milik M2 (Sibuk sendiri memanggil API publik)
- [x] 1c pemakai: HeartDemons (tunda 200), Ascension.canBegin, Purba.busy, Batin.busy, TreasureBirths.busy, Saingan (tunda, maks 30x), Ambushes (RETRY 200), SectRaids.canStartLive. WildDragons (EARLY retry), HeavenlyWrath (public; RETRY malam + challenge())
- [x] 1d uji tekanan pindah dimensi -> CANCELLED
- [x] 1e tekanan harian bersama (Ambushes, Saingan, SectRaids enemyTaken; cap 3/hari) (maks satu peristiwa musuh sekaligus + batas harian)
- [x] 2 `DuniaApi.safeGround` (provider di Saingan.init: Towns.inTown || anchor kota/ dalam 80) dipakai Ambushes.refusal, Saingan, Natives.trySpawn+groundSpot; [x] kunci musuh.ambush.refuse.town (dunia_sambung)
- [x] 3 Penjara: Prisons.MIN_REALM/RISE_REALM/mayPull/mayWake; ShrineRun.refuse di warn+pull (testPull lewati); CoreRun anyoneMayWake (raja.lemah); generator + 5 structure_set jauh_dari_pusat (6; penjara & gua_kuno 12). [x] teks lang ranah (dunia_sambung + help penjara_iblis)
- [x] 4 Penunjuk/Kabar (kode): DuniaSites.locate jenis nyata (REAL_KINDS lewat ChunkGenerator.findNearestMapStructure Pair), cleanCaves + `uji peta situs bersihkan` (PetaTests), PetaItems kind 6 kota_abadi (hanya Alam Abadi), DuniaMarkers sekte/gerbang (ikon 19), ArrivalIsland.cityHint (extra sambung_kota_abadi). [x] lang (dunia_sambung, usage dunia_peta)
- [x] 5 Saingan (band perlombaan raceBand, KotaNet.SainganAsk -> SCREEN_SAINGAN/TAB_SAINGAN, SainganData.regionOf(seed,i[,count]) + regionOf(server,i)); [x] PetaDomains nama Diplomasi (sectNameIn gerbang dalam domain; tanpa saingan domain dari sekte D): band perlombaan, payload tab Saingan, SainganData.regionOf publik statis, PetaDomains nama dari DiplomasiService
- [x] 6 (kode) Sobekan=2 jimat (SHARD_AS_BLANKS), Pahala 5 -> Giok Warisan universal (jongkok+klik Jantung), Gua Kuno Giok kedua; [x] Lencana rumor gratis KedaiTeh (KotaData lencana_hari, view 'lencana'); [x] lang (dunia_sambung; tooltip Sobekan, help gua/penjara di dunia_reruntuhan). Hadiah yatim: Sobekan Segel di Perkuat Segel, Pahala di Jantung Segel, Lencana rumor gratis, Kitab Formasi -> Giok Warisan kedua
- [x] 7 Disciples.addFollowVeto/followVeto (onChangeLevel + call), SoulJourney veto (bawah), Menara veto (TOWER_LEVEL)
- [x] 8 R15 kalimat Makam vs Menara (help menara_seratus; + murid menunggu)
- [x] 9 PANDUAN "Perubahan keseimbangan" catatan -> needsFromOthers (M1 pemilik PANDUAN), daftar di laporan akhir
- [x] 10 swa-periksa: javac 25 penuh (main+client, scratchpad m2fix, tanpa Gradle) 0 error; CRLF utuh di semua berkas Java; kunci en/id identik (dunia_sambung 38 kunci); semua kunci baru ada

## Berkas disentuh
- util/Sibuk.java (baru), dunia/api/DuniaApi.java (SafeGround/addSafeGround/safeGround)
- musuh/Ambushes, musuh/SectRaids, dunia/naga/WildDragons, purba/HeavenlyWrath, abadi/Natives, trial/HeartDemons, ascension/Ascension, purba/Purba, batin/Batin, dunia/langit/TreasureBirths
- dunia/kota/Saingan (safeGround provider kota di init), KotaNet, SainganData
- penjara/Prisons, ShrineRun, CoreRun, gua/HallRun, tools/gen_dunia_reruntuhan.py, 5 structure_set json (LF)
- helper edit CRLF: scratchpad ed.py (rep(path, old, new))
- langkah 4-8: DuniaSites, DuniaMarkers, PetaItems, PetaTests, PetaDomains, ArrivalIsland, KedaiTeh, Disciples, SoulJourney, Menara
- lang: dunia_sambung.{id_id,en_us} (baru), dunia_peta (usage), dunia_reruntuhan (tooltip Sobekan, help gua/penjara/menara)

## Langkah berikutnya
SELESAI. Laporan akhir dikirim (needsFromOthers: M1 PANDUAN + c14.kota_abadi.body, M3 tooltip Lencana, M5 kartu Saingan + tanda gratis Kedai Teh, M6 regionOf/marker sekte.gerbang/Sibuk.addProvider, M4/M6 kunci kultivator.sibuk.<id> untuk id mereka).

## Masalah terbuka
