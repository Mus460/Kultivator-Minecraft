## 38. Kota Besar dan Tempat untuk Semua Fitur

Permintaan pengguna (2026-10-22, bahasa Indonesia, mengikat): "Apakah nanti akan ada kota juga? Sekarang sudah banyak
peningkatan; aku ingin ada kota juga yang penuh NPC dan tempat-tempat, karena semuanya sekarang lebih hidup." dan
"Pastikan semua yang butuh tempat punya area atau lokasinya sendiri, bukan cuma event biasa, jadi ada world generation-nya
juga." Tambahan mengikat (koordinator, atas nama pengguna): "Aku ingin WORLD GENERATION-nya sangat detail, karena banyak
peristiwa bisa terjadi: kota, desa, sekte, gua, atau apa pun." Artinya: desa fana dan desa kultivator sebagai struktur
nyata, kompleks sekte NPC yang utuh, banyak jenis gua, jalan + pos + jembatan + kuil + tonggak, tabel kepadatan per bioma
dan jarak dari spawn, dan anggaran performa; modul boleh lebih dari enam (maksimal sepuluh, jumlahnya disebut).

Permintaan susulan yang sama mengikatnya (pengguna lewat koordinator, 2026-10-22): (a) semua NPC punya **kehidupan
harian dan animasi yang detail** (38.N); (b) tempat yang diserang atau rusak **diperbaiki pelan-pelan oleh NPC**, dengan
tagihan ganti rugi bila pemain penyebabnya, atau perbaikan lebih lama karena takut bila pemain jauh lebih kuat (38.R);
(c) **lingkar uang** yang utuh: beli senjata di pasar atau lelang dengan batu roh, cari uang lewat misi, dan dapat
penghasilan dari menjalankan sekte (38.M); (d) **GUI baru yang segar** untuk semua layar baru (38.U); (e) bukan sekadar
ide atau event: **model 3D, animasi dan cerita** yang nyata, dengan uji terima yang membuktikan konten itu terlihat di
game (38.O, 38.S, 38.E.4).

Bagian ini adalah kontrak implementasi lengkap untuk **sepuluh modul** (38W, 38KS, 38EK, 38NL, 38MD, 38ST, 38PR, 38UI,
38DS, 38GU; 38.E). Urutan bagian: 38.0-38.1 aturan dan fakta, **38.A** audit tempat, **38.B** Kota Besar, **38.C**
tempat lain dan world generation, **38.N** kehidupan NPC dan animasi, **38.O** model 3D, **38.S** cerita dan quest,
**38.R** perbaikan dan ganti rugi, **38.M** lingkar uang, **38.U** UI baru, **38.D** performa, keamanan, simpanan, config,
perintah, panduan, peta, **38.E** modul, kontrak dan daftar uji. Semua nama kelas, field, metode, id struktur, id jangkar,
kunci lang, perintah dan angka di bawah adalah nama yang **harus** dipakai. Yang ditandai "(verifikasi)" sudah dicek
polanya di kode atau di `butterbot-fabric/reference/mcsrc` tetapi nama/perilaku persisnya wajib dicocokkan lagi sebelum
dipakai. Semua angka permainan adalah desain game (baris Sumber hanya menjelaskan ilham). Bagian ini berdiri sendiri;
`docs/DESIGN.md` tidak diubah oleh modul (pemimpin proyek boleh menambah satu baris rujukan nanti).

---------------------------------------------------------------------------------------------------------------------

### 38.0 Ruang lingkup dan aturan mengikat (tambahan atas DESIGN 0, 34.0, 36.0 dan 37.0)

1. **Blok.** Saat permainan berjalan, blok hanya berubah lewat `kehancuran/WorldBreaker` dan
   `sect/build/engine/BuildPlacer`. Bagian 38 **tidak menambah jalur ketiga**. Semua tempat baru (Kota Besar, kampung,
   kompleks sekte, gua, puncak kesengsaraan, pos, jalan, jembatan) lahir **hanya dari world generation di chunk baru**:
   struktur data-driven (`structure` + `template_pool` + `structure_set` + `processor_list` JSON, NBT dari
   `tools/nbt.py` + `tools/structgen.py`, generator Python per modul). Pintu, gerbang kota yang menutup malam, jeruji
   penjara kota, papan pengumuman hidup, panggung lelang, lentera langit, kembang api: **entitas atau gambar klien** (pola
   `KotaPintuEntity`, `reruntuhan/ColliderEntity`, `DisplayKey`), tidak pernah `setBlock`, `FallingBlockEntity`,
   `Explosion`, `LightningBolt`, `FireworkRocketEntity` atau blok api.
2. **Hanya chunk baru (W4 DESIGN 34).** Tidak ada `/place`, tidak ada penempatan struktur saat runtime. Perintah uji
   **mencari** (locate) lalu teleport, atau menampilkan pratinjau hantu klien (`uji struktur lihat`, 34.4.6). Untuk dunia
   lama, Kota Besar dan kompleks sekte (tempat multi-start) memakai penjaga dunia lama 38.D.3 agar tidak pernah lahir
   setengah.
3. **Fitur tetap jalan di mana saja.** Tidak ada fitur lama yang **dikunci** ke tempat baru. Tempat memberi **tambahan**
   (bonus terbatas, konten ekstra, layanan, cerita, NPC). Contoh: alkimia tetap di kuali mana pun, Gilda Alkemis memberi
   kompetisi, peringkat dan resep; kesengsaraan tetap di mana pun, Puncak Petir memberi faktor 0.90 dan penonton.
4. **Faktor berbatas.** Setiap bonus tempat dikalikan ke faktor lama dan dibatasi: faktor situs meditasi lewat
   `SpiritSites.addSiteProvider` (hasil tetap tunduk `CAP 4.0` dan `min(3.5, ...)` Meditation), diskon toko ≤ 15 %,
   faktor kerusakan kesengsaraan ≥ 0.85, peluang lain ≤ +10 poin persen. Angka lama tidak berubah (DESIGN 36.0 butir 8).
5. **Ekonomi tanpa cetak uang.** Harga dalam Nilai Batu dengan kurs perbaikan M3 (`docs/progres/fix-M3.md` langkah 6,
   disetujui pengguna): Rendah 1, Menengah 9, Tinggi 81, Agung 729 (`NilaiBatu.VALUE`), `NilaiBatu.give` tidak pernah
   mengeluarkan Agung, 1 PK = 2 NB (satu konstanta M3), acuan 1 Batu Asal Abadi ≈ 150 NB (audit ekonomi S4c).
   Setiap beli-kembali ≤ ½ harga jual; bank tidak memberi bunga; judi batu bernilai harapan 0.85-0.92 dari harga; hadiah
   kompetisi berasal dari biaya pendaftaran + barang tabel terbatas per hari dunia. Batu roh hanya keluar dari sumber
   yang sudah ada (bijih, misi, lelang) atau dari barang yang dijual pemain.
6. **Laptop lemah (V1-V4 DESIGN 34).** Semua tampilan "shader berat" dipalsukan (quad aditif, tekstur glow, overlay HUD,
   `SkyDirector`); tanpa post chain; `EffectQuality` Minimal/Normal/Maksimal; LOD jarak; entitas dibatasi; AI ditipiskan;
   penduduk jauh hanya **data**. Kamera 7b: adegan ≤ 4 s, bisa dilewati, patuh Penuh/Singkat/Mati, `ShotGuard`.
7. **Klien/server terpisah, lang, akhir baris (C1-C6 DESIGN 34).** Kode server tidak mereferensikan kelas klien;
   ItemStack malas; lang di `lang-src/<fragmen>.en_us.json` + `.id_id.json` dengan kunci identik (Indonesia utama); file
   baru LF; file lama mempertahankan akhir barisnya (`KultivatorClient.java`, `musuh/SectRaids.java` dan sebagian
   fragmen lang CRLF). Gradle hanya langkah akhir (`compileJava`, `compileClientJava`, `jar`, `--no-daemon`); modul
   **tidak** menjalankan Gradle, tes, `runClient` atau `gradlew build`.
8. **Satu pemilik per file lama** (38.E.2). File modul DESIGN 36 (`sekte/<id>/**`) dan modul 37 tidak disunting; yang
   dipakai hanya API publik (`SekteApi`, `DiplomasiService`, `SekteBus`, `TurnamenService`, `Halls.addCandidates`,
   `DuniaApi.*`, `HubCards.register`, `SpiritSites.addSiteProvider`, `ProtectedZones.addZoneProvider`).
9. **Sumber.** Setiap fitur punya baris Sumber (`Sumber: ...` bila sumber menyatakannya, `Terinspirasi dari ...` bila mod
   mengembangkannya), kunci `kultivator.<modul>.<fitur>.source|.inspired`. Yang tidak terverifikasi tidak dikutip dengan
   nomor bab.

Pelajaran dari `docs/progres/audit-integrasi.md` (putaran 2) yang **mengikat** semua modul 38 (nomor temuan dalam
kurung):

10. **Satu pintu "sibuk" (T3).** Setiap peristiwa 38 yang bisa menimpa pemain (Gelombang Binatang, begal jalan, razia,
    penangkapan buronan, uji Gua Cermin Hati, uji Makam Warisan, adegan festival, kamera judi) memanggil
    `util/Sibuk.why(player)` (dibuat modul integrasi M2) sebelum mulai dan **menunda** 200 tick bila sibuk, tidak
    membuang. Keadaan 38 yang membuat pemain sibuk didaftarkan dengan `Sibuk.register(id, Predicate)`: `tempat.sel`
    (di sel penjara), `tempat.warisan`, `tempat.cermin`, `tempat.pertahanan` (pemain ikut membela tembok),
    `tempat.teleport` (kanal 3 s). Peristiwa musuh 38 ikut **tekanan harian bersama** M2 (maks satu peristiwa musuh
    sekaligus).
11. **Tempat aman (T4).** Kota Besar, kampung, desa klan, pos singgah dan halaman depan kompleks sekte Lurus/Netral
    mendaftar ke `DuniaApi.safeGround(level, pos)` (layanan M2) lewat satu provider 38W (`TempatZones.safe`). Tidak ada
    penyergapan, sergapan Saingan, Natives, naga liar, Murka atau spawn musuh 38 di dalamnya. Gelombang Binatang hanya
    datang **di luar** tembok (titik muncul ≥ 24 blok di luar kotak tembok).
12. **Tidak pernah memakai heightmap sebagai tanah di dalam tempat (T4, atap Kota Abadi).** Di atas struktur, heightmap
    `MOTION_BLOCKING*` adalah atap. Semua kode 38 yang mencari titik pijak memakai `TempatApi.groundSpot(level, x, z,
    nearY)` (38W): titik dari **jangkar** atau dari data lot (`lantai` di sidecar lot, 38.B.2.4), atau, di luar
    tempat, heightmap yang lalu ditolak bila kolomnya berada di dalam kotak tempat mana pun (`TempatZones.inside`).
    `spawn_overrides` mengosongkan `monster` di semua tempat berpenghuni; atap tinggi ibu kota (istana, dermaga) diberi
    `HIDDEN_LIGHT` cahaya ≥ 8 di bawah genteng agar mob alami tidak muncul di atap.
13. **Gerbang ranah dan jarak dari spawn (T7).** Setiap tempat berbahaya punya **ranah disarankan** (kartu peringatan saat
    masuk, `TitleCard.banner`) dan gerbang ranah pada aksinya yang berbahaya (membangunkan bos, membuka segel, memulai
    uji). Semua set 38 memakai `kultivator:jauh_dari_pusat` atau `kultivator:cincin_tempat` (38.D.1) dengan `min_chunks`
    menurut tingkat bahaya (tabel 38.C.9); tidak ada `random_spread` polos.
14. **Musuh dunia berskala ranah (T5).** Setiap entitas musuh 38 yang berskala ranah (penjaga yang bermusuhan, kultivator
    iblis sarang, bos sarang binatang, monster Gelombang Binatang yang diperkuat, begal jalan) memanggil
    `TechniquePower.markRealmFoe(mob)` (dibuat M4) di `setup`/saat dimunculkan.
15. **Satu sumber sekte NPC (T12).** Kompleks sekte 38 hanya ada untuk entri `GerbangPlan` (nama, aliran, lambang dari
    `DiplomasiService`); tidak ada daftar sekte keempat. Lapak perekrutan, Turnamen Agung dan Balai Pertemuan memakai
    sekte D yang sama. Situs kompleks diterbitkan sebagai `sekte/kompleks` dengan `data.sekte = <id>`.
16. **Ekonomi sejalan (K2, T14, S23).** Semua beli/jual 38 memakai `NilaiBatu.pay/give` dan penyaring jual yang sama
    dengan `Toko` setelah perbaikan M3 (cincin penyimpanan dan wadah berisi **ditolak**); titipan lelang dan pesanan
    tempa **disimpan di SavedData** sejak awal (tidak hanya memori); kurs batu hanya dari `NilaiBatu.VALUE` (tidak ada kurs
    sendiri yang bisa diarbitrase).
17. **Registry tanpa saklar (K1).** Item, entitas, payload, jenis struktur, jenis placement dan komponen 38 **selalu**
    didaftarkan; saklar config hanya mematikan perilaku dan pack worldgen. Nama bagian config tidak bertabrakan dengan
    yang ada (`kota`, `langit`, `sekteLangit` sudah dipakai): 38 memakai `tempat`, `kotaBesar`, `kampung`,
    `kompleksSekte`, `gua`, `jalan`.
18. **Murid tidak ikut ke tempat tertutup (S2).** Sel penjara, ruang uji Makam Warisan dan Gua Cermin Hati memveto murid
    pengikut lewat `Disciples.addFollowVeto` (M2); murid menunggu di pintu masuk.

### 38.1 Fakta dari kode dan sumber 26.3 (audit 2026-10-22; kode adalah kebenaran)

**Kota yang sudah ada** (`dunia/kota/**`, DESIGN 34.5.1-34.5.4 dan 34.12):
- `kota/kota_kultivator` (+ `_salju`, `_sabana`, `_sakura`): jigsaw `size 6`, `max_distance_from_center 72`, `beard_thin`,
  start pool `kota/gerbang`, kota bertembok ±130 × 130 blok; satu set `kota/kota_kultivator` dengan placement
  `kultivator:jauh_dari_pusat` (`dunia/JauhDariPusatPlacement.java`) spacing 64, separation 26, salt 340531,
  `min_chunks` 6, exclusion `minecraft:villages` 8 chunk; pack bawaan `kultivator:dunia_kota`.
- Rantai jigsaw lurus: `gerbang` (23×22×13) → `jalan_utama_a/b` (9×8×22) → `alun` (33×16×33) → empat jalan samping
  (`jalan_lurus_1..5`, `jembatan`, `jalan_silang`) → `tembok_ujung_1..3`; bangunan unik `balai_lelang` 23×30×17,
  `kedai_teh` 23×18×23, `toko_peti`, `paviliun_pil/pusaka`, `penginapan` 15×22×13, `kantor_sekte` 15×26×15; rumah dan
  hiasan 7×9×7 sampai 15×14×15 (`tools/gen_dunia_kota.py`, pools :2572-2599, processor `kota/lapuk`, `kota/utuh`).
- NPC `kultivator:kultivator_npc` (`KultivatorNpc`, `PathfinderMob`, `Duelist`, `noSave`, tracking 8) dengan 15 peran
  `NpcRole` (`dunia/kota/NpcRole.java:12-40`); tubuh hanya dari jangkar dalam 48 blok (`KotaAnchors.person` :152),
  `npcPerTown` 10 (4-14), `DuniaBudget` `umum.npcCap` 36 / `npcPerPlayer` 12 dalam 96 blok, MSPT guard 45 ms;
  rutinitas `NpcLife` (malam 13000-23000 pulang, pagi latihan, siang istirahat, obrolan 4 baris, penjaga patroli,
  membungkuk ke yang 2 ranah di atas). **Tidak ada simulasi penduduk jauh**: tubuh lenyap 30 s setelah tidak ada pemain
  dalam 64 blok (`setLonely(64, 600)`).
- Layanan: `Toko` (6-9 tawaran/hari, beli-kembali 0.35-0.40), `BalaiLelang` (senja 12000-14000, 5 lot, penawar NPC
  dari `Saingan`), `KedaiTeh` (teh 2/9/20 NB, kabar 1/9/80 NB, token pasar gelap 60 NB), `PasarGelap` (markup 1.5,
  razia, suap), `PapanMisi` (3 misi/hari: buru/kemah/herba/kawal), `Pengembara`, `Saingan`, `KotaDuel`; harga `Harga`
  (`BANDS 9`, `ARTIFACT {20,60,200,700,2400,8000}`).
- **Nama Baik** per kota (−100..100, `KotaData.reputation/addReputation(long town, UUID)` :67/:71): −10 melukai NPC,
  −30 membunuh, +5 misi, +1 menang lelang, −5 tidak membayar; ≤ −30 toko menolak dan penjaga bermusuhan; ≥ 50 diskon
  10 %. **Tidak ada** catatan kejahatan, status buronan, denda atau penjara.
- `Towns` (`dunia/kota/Towns.java`): id kota = id situs `kota/kota` atau `kota/kota_abadi` (:28-31), `SAFE_RADIUS 80`
  (tanpa penyergapan), pusat dari jangkar `alun`/`abadi_gerbang` (`remember` :38).
- Jangkar `kota/*` (`situs`, `alun` protect 72, `penjaga`, `papan`, `warga`, `lelang`, `panggung`, `teh`,
  `kursi_pengembara`, `kolam`, `gelap`, `gerbang_gelap`, `pil`, `pusaka`, `lentera`); **struktur lain boleh memakai
  jangkar `kota/*`** dan modul K melayaninya (Pulau Pasar modul D sudah memakai `kota/teh` dan `kota/lentera`).

**Spine Dunia** (DESIGN 34.4): `DuniaAnchorEntity` (`kind`, `data`, `state`, `site`; `DEFAULT_PROTECT 8`, maks 128),
`DuniaAnchors.register/loaded/nearest/protectedNear` (64 jangkar per tick, kunjungan 20 tick, 60 saat MSPT tinggi),
`DuniaSites.publish/publishLive/discover/locateFor` (SavedData `kultivator:dunia_sites`), `DuniaMarkers` (23 jenis di
`:21-43`, ikon di `textures/dunia/peta/markers.png`), `DuniaBudget.setCap/track/count/mayAdd/throttled/overloaded`
(jenis bebas: `setCap(String kind, int cap)` :48), `DuniaConfig.section(name, type, defaults, clamp)` (:52),
`DuniaPacks.worldgen(module, on)` (:39, pack `kultivator:dunia_<module>`), `DuniaCommands.test/player` (:122/:129) +
`CHIPS` (:77), `DuniaCommands.findAndTeleport` (:274), `DuniaTickets.touch` (≤ 4 tiket, 100 tick), `DuniaEvents`
(TREASURE_BORN, SKY_EVENT, SITE_DISCOVERED, VOYAGE, ...), `DuniaApi.routes().registerDestination(...)`.

**Sekte NPC** (DESIGN 36.2.9): `sekte/diplomasi/GerbangPlan` (rencana murni dari seed: 8 sekte di cincin `jarakMin` 1500
sampai +1400 blok, `MIN_APART 700`, pita kecil 45 % / sedang 35 % / besar 20 %, cache volatile), `GerbangSektePlacement`
(`kultivator:gerbang_sekte`, field `band`), tiga struktur **satu potongan** `sekte_gerbang_kecil|sedang|besar` (25×20×33,
37×26×45, 47×38×48; jigsaw `size 1`, `max_distance 80`, `beard_box`, `start_height absolute 0` +
`WORLD_SURFACE_WG`), salt 360911-360913, spacing 24, pack `kultivator:sekte_gerbang`; anggota tubuh RIVAL ≤ 6 per
gerbang lewat jangkar modul `"sektenpc"` (`AnggotaSekte`). **Belum ada kompleks sekte**: hanya gerbang. API
`sekte/api/DiplomasiService` (`sects()`, `name`, `emblem`, `tier`, `standing`, `stance`, `adjust`, `gate`). Sekte
langit NPC (37.7.6): `LangitNpcPlan` memilih entri `tier ≥ 7` dari `GerbangPlan`, istana di atas chunk gerbang, salt
360921-360922.

**Batas world generation 26.3** (mcsrc, `net/minecraft/world/level/levelgen/structure/...`):
- `JigsawStructure`: `MAX_TOTAL_STRUCTURE_RANGE = 128`, `size` 0-20 (:33-41); `max_distance_from_center.horizontal`
  1-128, dan `verifyRange` (:64-72) menuntut `horizontal + 12 ≤ 128` bila `terrain_adaptation` bukan `none` (praktis
  **116**, atau 128 tanpa adaptasi). `JigsawStructure` adalah kelas **final**.
- `JigsawPlacement.addPieces` (:51-130): rotasi potongan awal **acak** (`Rotation.getRandom(random)` :70, panggilan acak
  pertama karena `start_height` konstan tidak memakai acak), tanpa batas jumlah potongan; potongan diterima bila kotaknya
  di dalam ruang bebas (VoxelShape) batas ±horizontal.
- `ChunkGenerator.createReferences` (:616-633) memakai `range = 8` chunk: potongan yang > ±8 chunk dari chunk awal
  **tidak pernah ditempatkan**. Satu start ≈ ≤ 256 × 256 blok, praktis ≤ 232 × 232 dengan adaptasi.
- Templat: `StructureBlockEntity` membatasi 48/sumbu hanya di UI; `StructureTemplate.load` tidak membatasi; `tools/nbt.py`
  (`Template.__init__` :331-334) menolak x/z > 48 → **potongan kita ≤ 48 × 48**, tinggi ≤ 384.
- `StructureSet` memilih satu struktur per set per chunk (`ChunkGenerator.createStructures` :483-570); antar-set **tidak
  ada** jaminan bersebelahan; `exclusion_zone` hanya menolak (`chunk_count` 1-16, satu `other_set`); `locate_offset`
  hanya menggeser hasil `/locate`.
- `FeaturePoolElement` = daun 1 blok (tidak bisa merantai); **tidak ada fitur jalan vanilla**.
- `Structure.GenerationContext` (record :203) membawa `chunkGenerator`, `biomeSource`, `climateSampler`, `biomeResolver`,
  `randomState`, `seed`, `chunkPos`, `heightAccessor`; `ChunkGenerator.getBaseHeight(x, z, Heightmap.Types,
  LevelHeightAccessor, RandomState)` (:662) dan `getFirstFreeHeight` (:666) **deterministik dari seed** → semua start
  Kota Besar bisa menghitung putusan yang sama tanpa berbagi keadaan.
- `ChunkGeneratorStructureState` hanya membuka `randomState()`, `getLevelSeed()`, `possibleStructureSets()`,
  `hasStructureChunkInRange(...)` (:202); `biomeSource` privat → putusan bioma dibuat di **struktur**, bukan di placement.
- `StructureType` adalah antarmuka fungsional (`MapCodec<S> codec()`), didaftarkan `Registry.register(
  BuiltInRegistries.STRUCTURE_TYPE, id, () -> CODEC)`; `Structure.settingsCodec(i)` (:52) untuk field umum.
- `PoolElementStructurePiece(templateManager, element, pos, groundLevelDelta, rotation, box, liquidSettings)` (:36)
  bertipe `StructurePieceType.JIGSAW` → potongan buatan tata letak kita disimpan dengan format vanilla.
- `ChunkGenerator.tryGenerateStructure(...)` (:572, privat) adalah satu-satunya titik yang membuat start; mixin di sana
  dipakai untuk zona bebas-struktur (38.D.4).
- `ServerLevel.findNearestMapStructure(TagKey<Structure>, BlockPos, int, boolean)` (:1536).

**Lain-lain**: `desa/VillageScan.findBell` memakai POI `PoiTypes.MEETING` (lonceng) dalam 24 blok dan menghitung
`Villager` yang `MEETING_POINT`-nya lonceng itu → **kampung buatan kita yang berlonceng dan berpenduduk villager vanilla
otomatis bisa diikrarkan** oleh sistem Desa Bawahan tanpa menyunting `desa/**`. `CaveAbode` (gua kediaman pemain,
`ZONE_RADIUS 16`), `SpiritVeins` (wilayah 64 blok, THIN/NORMAL/RICH/GREAT), `SpiritSites.addSiteProvider(
Function<ServerPlayer, ExtraSite>)` (:124; pemakai sekarang hanya `LangitSites`). `guide/GuideChapters`
`CHAPTER_COUNT = 16` (37 merencanakan bab 17). `HubCards.register(section, id, glyph, lock, action)`
(`client/hub/HubCards.java:30`). Suara: `tools/gen_voices.py` daftar `WORLD` (:440) tuple `(kunci, suara, ja, en, id,
emosi)`, grup di `voice/VoiceEvents.java`. Pemilik file lama 37: `kultivator.mixins.json`, `GuideChapters`, `CheatHelp`
→ 37G (37.9.6); 38 menjadwalkan suntingannya sesudah 37G (38.E.2).

---------------------------------------------------------------------------------------------------------------------

### 38.A Audit tempat: fitur yang sekarang hanya event, menu, perintah atau "di mana saja"

Kolom: **Sekarang** (file:baris titik masuk, awalan `src/main/java/dev/kultivator/` dihilangkan); **Cara** (EV = terjadi
di dekat pemain, MENU = kartu hub/tablet, CMD = perintah, ITEM = pakai barang, BLOK = blok buatan pemain, STRUK =
struktur worldgen); **Tempat baru** (id struktur/distrik); **Kaitan** ("tetap" = fitur tetap jalan di tempat lama;
"+" = tambahan di tempat baru); **Frekuensi** (worldgen; per km² = per 1000 × 1000 blok, lihat 38.C.9).

| # | Fitur | Sekarang | Cara | Tempat baru | Kaitan | Frekuensi |
|---|---|---|---|---|---|---|
| 1 | Lelang kota | `dunia/kota/BalaiLelang.java:149` | STRUK (kota) | Kota Besar, distrik Pasar: **Balai Lelang Agung** (jangkar `kotabesar/lelang_agung`) | tetap di kota biasa; + Lelang Agung mingguan 8 lot (2 lot pita tinggi), konsinyasi 4, penawar sekte NPC (38.M.2.3) | 1 per ibu kota |
| 2 | Lelang Abadi | `abadi/Auction.java:197` | MENU + dimensi | tetap di Kota Abadi | tetap; ibu kota hanya punya **Papan Kabar Lelang Abadi** (jadwal, tanpa tawar) | — |
| 3 | Alkimia (kuali di api unggun) | `alchemy/AlchemyFurnace.java:206` | BLOK di mana saja | **Gilda Alkemis** + Panggung Pertemuan Pil | tetap; + ujian Tingkat Alkemis 1-9, Pertemuan Pil 7-harian, toko herba bergating tingkat, Tungku Gilda | 1 per ibu kota |
| 4 | Lomba/penilaian pil | — (tidak ada) | — | Panggung Pertemuan Pil | baru | 1 per ibu kota |
| 5 | Penempaan (Tungku Bumi) | `tempa/EarthfireForgeBlock.java:31` | BLOK | **Gilda Pandai Pusaka** | tetap; + pesanan tempa ke Empu (1-3 hari dunia), uji Empu, perbaikan, penilaian pusaka | 1 per ibu kota |
| 6 | Pusaka Natal Diri | `tempa/NatalTreasureSelf.java:214` | MENU/CMD di mana saja | Altar Jiwa Pusaka di Gilda Tempa | tetap; + ikat di altar: 30 s → 20 s | — |
| 7 | Turnamen sekte (aula) | `sect/event/Tournament.java:367`, `sekte/turnamen/Turnamen.java:114` | STRUK (aula pemain) | **Arena Agung** ibu kota | tetap di aula; + Turnamen Agung Antar-Sekte 14-harian | 1 per ibu kota |
| 8 | Tantangan antar-sekte | `sekte/turnamen/AntarSekte.java:46` | EV → chat/hub | Arena Agung atau lapangan latihan kompleks lawan | tetap; + tempat tanding netral berpenonton | — |
| 9 | Diplomasi sekte NPC | `sekte/diplomasi/Simulasi.java:88`, `Aksi.run:122` | MENU | **Balai Pertemuan Sekte** (distrik Istana) + kompleks sekte | tetap di hub; + Pertemuan Sekte 30-harian (pakta disahkan di sini +5 standing), kunjungan ke kompleks | 8 kompleks per dunia |
| 10 | Gerbang sekte NPC | `sekte/diplomasi/GerbangSekte.java` (worldgen 1 potongan) | STRUK | **Kompleks Sekte** di belakang gerbang (38.C.3) | gerbang tetap; + aula, lapangan latihan, paviliun, asrama, puncak ketua | 8 per dunia (+2 langit, 37) |
| 11 | Perang sekte, tantang ketua | `sekte/diplomasi/Perang.java`, `KetuaSekte.challenge:151` | STRUK (gerbang) | Aula Puncak di kompleks | tetap; + ketua menunggu di Aula Puncak bila kompleks ada | — |
| 12 | Papan misi kota | `dunia/kota/PapanMisi.java:160` | STRUK (kota) | **Gilda Kultivator** + papan pos dagang | tetap; + Lencana Bintang 1-9, misi berperingkat (gua, buronan, kawal kafilah), Daftar Naga Muda | 1 per ibu kota; pos dagang |
| 13 | Kantor Misi aula, Ekspedisi | `sect/hall/Contribution.java:191`, `sect/event/Expeditions.java:208` | MENU (aula) | — | tetap (milik aula) | — |
| 14 | Misi cerita sekte | `sekte/misi/TimMisi.java:231` | MENU | tujuan nyata: gua, kampung, kota (data) | tetap; misi X tidak disunting, 38 hanya menyediakan `TempatApi.nearest` untuk dipakai kelak | — |
| 15 | Perekrutan murid | `disciple/Disciples.java:449`, `sect/hall/Candidates.java:1140`, `sect/event/Envoys.java:67` | ITEM/STRUK (aula) | **Alun-alun Penerimaan** ibu kota + balai kampung | tetap; + Hari Pembukaan Gerbang 10-harian (lapak sekte, 1-3 calon lewat `Halls.addCandidates`), anak berbakat kampung terikrar | 1 per ibu kota; kampung |
| 16 | Teleportasi formasi sekte | `formasi/effect/b/TransportNetwork.java:111` | STRUK (wilayah aula) | **Stasiun Formasi Teleportasi** ibu kota + cakram di pos singgah besar | tetap; + jaringan umum berbayar antar tempat yang sudah ditemukan | 1 per ibu kota |
| 17 | Kapal terbang | `dunia/kapal/LunasItem.java:54`, `KapalRohEntity.sailTo:1235` | ITEM → entitas | **Dermaga Langit** ibu kota | tetap; + tujuan rute `kota_besar` (`DuniaApi.routes()`), bahan bakar, jual lunas, Perjalanan Awan berbayar | 1 per ibu kota |
| 18 | Peta dunia | `dunia/peta/PetaItems.java:67`, `Peta.java:102` | ITEM/CMD/MENU | Kantor Peta (Perpustakaan) + tonggak jalan | tetap; + tonggak memberi penemuan situs, jalan digambar di peta | tonggak ±2/km jalan |
| 19 | Jimat pulang, Penyeberangan | `talisman/TalismanItem.java:50`, `abadi/AbadiItems.java:69` | ITEM | toko jimat ibu kota | tetap | — |
| 20 | Bencana Bintang | `purba/StarCalamity.java:292` | MENU/CMD di tempat pemain | **Panggung Bintang** di puncak + Menara Pengamat Langit ibu kota | tetap; + dimulai di sana: +1 gulir tabel hadiah, kamera lebar | 0.08/km² |
| 21 | Murka Langit | `purba/HeavenlyWrath.java:106` | EV | — (memang acak) | tetap | — |
| 22 | Peristiwa langit | `dunia/langit/SkyEvents.java:346` | EV dimensi | Menara Pengamat Langit, Panggung Bintang | tetap; + menonton di sana: pecahan meteor ×2, Kristal Gerhana 35 % → 50 % (batas harian tetap) | — |
| 23 | Kelahiran harta | `dunia/langit/TreasureBirths.java:120` | EV 300-900 b | condong ke **Gua Urat Roh** dan Panggung Bintang yang dikenal | tetap; + 35 % kelahiran memilih tempat dikenal dalam jangkauan | — |
| 24 | Cuaca roh | `dunia/langit/SpiritWeathers.java:237` | EV sel | — | tetap | — |
| 25 | Kesengsaraan terobosan | `cultivation/Tribulation.java:349` | EV di tempat pemain | **Puncak Petir** | tetap; + faktor kerusakan 0.90, Batu Catatan, penonton NPC | 0.25/km² di bioma puncak |
| 26 | Kesengsaraan Kenaikan | `abadi/Ascend.java:283` | MENU | Puncak Petir | tetap; + nama di Batu Catatan | — |
| 27 | Iblis hati | `trial/HeartDemons.java:358` | EV di mana saja | **Gua Cermin Hati** | tetap; + uji di sana: kenaikan tekanan gagal ×0.5, lulus −20 tekanan | 0.15/km² |
| 28 | Uji warisan (giok) | `technique/source/InheritanceTrials.java:220` | ITEM di mana saja | **Makam Warisan** | tetap; + giok menunjuk makam terdekat; di makam: peluang mutu +10 poin, sisa jiwa | 0.3/km² |
| 29 | Gua Leluhur (R) | `dunia/reruntuhan/gua/HallRun` | STRUK | tetap, ditautkan ke jalan dan papan misi | tetap | 34.2.1 |
| 30 | Binatang purba | `purba/PrimordialEvents.java:738` | EV/ITEM | — (Alam Abadi) | tetap | — |
| 31 | Penyergapan, kemah bandit | `musuh/Ambushes.java:147`, `musuh/Camps.java:66` | EV (titik data) | **Sarang Kultivator Iblis** + kemah di pinggir jalan | tetap; + markas nyata (tawanan, bos, peti) | 0.6/km² (naik dengan jarak) |
| 32 | Serbuan sekte iblis | `musuh/SectRaids.java:308` | STRUK (aula) | asal penyerbu: kompleks sekte Iblis | tetap; + pemain bisa membalas ke kompleks | — |
| 33 | Pengembara | `dunia/kota/Pengembara.java:109`, kursi `:215` | EV + kedai teh | + pos singgah, Gua Pertapa | tetap; + kursi `kota/kursi_pengembara` di pos singgah | — |
| 34 | Kebangkitan binatang roh | `beast/SpiritBeasts.java:307`, `dunia/naga/BeastTribulation.java:134` | ITEM | **Pasar Binatang Roh** + **Sarang Binatang** | tetap; + anak binatang, sesaji, pelatih, XP binatang ×1.25 di sarang | 1 per ibu kota; sarang 1.6/km² |
| 35 | Pasar binatang | — | — | Pasar Binatang Roh | baru | 1 per ibu kota |
| 36 | Batu roh, penukaran | `harta/SpiritStones.java:110` (bijih), resep 9:1 | EV/ITEM | **Rumah Uang** ibu kota | tetap; + simpanan aman, Surat Wesel, tukar Batu Asal | 1 per ibu kota |
| 37 | Judi batu | — | — | **Rumah Judi Batu** | baru | 1 per ibu kota |
| 38 | Penginapan, makan | — (bangunan `penginapan` kota tanpa layanan) | — | Penginapan + Rumah Makan ibu kota, pos singgah | baru | 2 per ibu kota; pos singgah |
| 39 | Kuliah Dao | `dunia/kota/KotaAbadi.java:117` (hanya Kota Abadi) | STRUK | Aula Kuliah (Perpustakaan) + Gua Pertapa | tetap; + kuliah fana harian (ranah ≤ Nascent Soul) | — |
| 40 | Kitab teknik, giok | `dao/TechniqueBooks.java:244`, `technique/source/JadeSlips.java:129` | ITEM/loot | **Perpustakaan Agung** | tetap; + baca berbayar, salin giok, arsip kabar | 1 per ibu kota |
| 41 | Titah | `pusakalangit/HeavenDecreeSealItem.java:36` | ITEM/MENU | — | tetap | — |
| 42 | Duel Pedang | `duel/session/DuelChallenges.java:124` | CMD/MENU di mana saja | Arena Agung, lapangan latihan kompleks, alun-alun desa klan | tetap; + duel di arena: penonton, Nama Baik +1 bila menang | — |
| 43 | Alam Rahasia | `alam/Alam.java:273` | ITEM | — | tetap; + Gilda Kultivator menjual kunci alam (stok 1/hari) | — |
| 44 | Desa bawahan | `desa/Pledge.java:54` | STRUK (lonceng vanilla) | **Kampung Fana** dan **Desa Klan** (38.C.2) | tetap; kampung kita berlonceng + villager → bisa diikrarkan | 1.4 + 0.6 /km² |
| 45 | Meditasi | `cultivation/Meditation.java:756` | di mana saja | Gua Pertapa, Gua Urat Roh, Kuil Langit, Puncak Petir | tetap; + situs ekstra ×1.2-1.6 (dalam CAP) | — |
| 46 | Gua kediaman pemain | `cultivation/CaveAbode.java` | BLOK | Gua Pertapa yang sudah "diwariskan" | tetap; kursi pertapa bisa dijadikan kediaman | — |
| 47 | Tambang roh sekte | `sekte/kebun/**` (36.2.10) | STRUK (aula) | Gua Urat Roh | tetap | — |
| 48 | Kabar dunia | `dunia/kota/KedaiTeh.java:206`, `KabarDunia` | STRUK (kedai) | + Papan Kabar ibu kota, pos singgah | tetap | — |
| 49 | Hukum, kejahatan | `dunia/kota/Kota.java:290-342` (hanya Nama Baik) | STRUK | **Kantor Yamen** + Penjara Kota | baru: Catatan Kejahatan, buronan, denda, sel | 1 per ibu kota |
| 50 | Pertahanan kota | — | — | tembok + gerbang ibu kota | baru: Gelombang Binatang | 1 per ibu kota |
| 51 | Festival | — | — | alun-alun, Jalan Lentera, balai kampung | baru: Festival Lentera, Pasar Malam, Panen | — |
| 52 | Kuil, doa | — | — | Kuil Langit ibu kota, kuil jalan | baru: dupa, lonceng | kuil jalan ±4/km jalan |
| 53 | Perjalanan antar tempat | jalan kaki/kapal | — | **jalan, jembatan, pos, tonggak** (38.C.6) | baru (worldgen) | jaringan per wilayah |
| 54 | Sekte langit NPC (37) | `sekte/langit/npc/*` (rencana 37) | STRUK | Dermaga Langit menyebut jadwal tamu | tetap (37) | 37 |

Yang sengaja **tidak** diberi tempat: Murka Langit, cuaca roh, Dunia Batin, Titah, misi cerita sekte (data murni), karena
ide intinya memang "datang di mana saja" atau terjadi di dunia batin. Fitur yang menyatu dengan aula pemain
(Kantor Misi, Ekspedisi, kebun, tambang, Paviliun Kitab, makam) tetap milik aula.

---------------------------------------------------------------------------------------------------------------------

### 38.B Kota Besar (Ibu Kota Kultivator)

#### 38.B.1 Gambaran, lore dan Sumber

- **Apa.** Ibu kota bertembok seluas **432 × 432 blok** di dalam tembok (± 11 kali luas kota biasa 130 × 130), dengan dua
  distrik di luar tembok (Kampung Kumuh di timur, Lapangan Kafilah di selatan), sembilan distrik di dalam, ±650 penduduk
  data, ±40 layanan, dan kalender acara sendiri. Paling banyak satu ibu kota per sel 4096 × 4096 blok; ibu kota pertama
  dijamin dalam cincin 1800-3200 blok dari pusat dunia bila tanahnya layak (38.B.2.2).
- **Nama.** `KotaBesarNames.of(seed, index)`: "Kota <A> <B>" dengan A ∈ {Awan Giok, Naga Langit, Feniks Merah, Teratai
  Emas, Harimau Putih, Kura Hitam, Bintang Utara, Sungai Perak, Gunung Kencana, Pinus Abadi} dan B ∈ {Agung, Suci, Abadi,
  Timur, Barat, Kencana, Langit, Damai}; penguasa bergelar **Pangeran Penjaga Kota** (bukan kaisar: dunia kultivasi
  diperintah sekte, kota ini milik "Paviliun Pengawal Kota", persekutuan netral para sekte). Kunci
  `kultivator.kota_besar.nama.a.<i>` / `.b.<j>`.
- **Sumber** (`kultivator.kota_besar.kota.inspired`): "Terinspirasi dari Chang'an dinasti Tang: kota berpetak dengan Pasar
  Timur dan Pasar Barat, menara genderang yang menandai jam malam dan gerbang yang ditutup malam hari; dari Kota Kerajaan Jia
  Ma dan Kota Tanah Tengah (Zhongzhou) dengan Menara Pil dalam Battle Through the Heavens; dan dari lukisan 'Sepanjang
  Sungai saat Festival Qingming' karya Zhang Zeduan untuk keramaian jalannya. Tata kota, harga dan angkanya rancangan
  game." Distrik dan layanan punya baris sendiri (38.B.3).

#### 38.B.2 World generation ibu kota (modul 38KS untuk NBT/JSON, 38W untuk jenis struktur dan rencana)

##### 38.B.2.1 Kenapa bukan satu jigsaw

Satu start jigsaw paling jauh ±116 blok (dengan adaptasi) dan rotasinya acak (38.1). Ibu kota 432 + tembok + dua distrik
luar tidak muat. Maka: **11 start terpisah** (satu per distrik) yang semuanya membaca **rencana deterministik** yang sama
(`RencanaKotaBesar`), dengan **jenis struktur sendiri** `kultivator:tata_letak` (`dunia/tempat/TataLetakStructure`)
yang menaruh lot dengan posisi dan rotasi **tetap** (bukan jigsaw), dan satu **putusan tanah** bersama sehingga ke-11
distrik lahir semua atau tidak sama sekali.

##### 38.B.2.2 Rencana (`dunia/tempat/RencanaKotaBesar`, 38W)

- `public static Rencana of(long seed)` (cache volatile seperti `GerbangPlan`); `Rencana.kandidat()` = daftar
  `Kandidat(int index, int pusatX, int pusatZ, int putar, boolean pertama)`; `Rencana.distrikDi(int chunkX, int chunkZ)`
  → `@Nullable Distrik(Kandidat k, String id)`; `Rencana.zona(Kandidat)` → kotak keep-out (38.D.4).
- **Cincin pertama:** 12 kandidat berurutan di sudut `turn + i × 30°` dan jari-jari `1800 + hash × 1400` blok
  (`kotaBesar.jarakPertamaMin` 1800, `jarakPertamaLebar` 1400); kandidat pertama yang **layak** (putusan 38.B.2.4)
  menjadi ibu kota pertama; kandidat lain di cincin itu gugur.
- **Sel jauh:** setiap sel 4096 × 4096 blok selain sel pusat punya satu kandidat di titik hash dalam 60 % tengah sel,
  hidup dengan peluang `kotaBesar.peluangSel` (0.6), layak menurut putusan, dan ≥ `kotaBesar.jarakAntar` (2500) dari
  ibu kota lain (urutan sel spiral dari pusat; yang lebih dulu menang).
- **Menjauh:** ≥ 700 blok dari setiap gerbang `GerbangPlan` (sekte NPC), ≥ 400 dari setiap Kandidat sekte langit 37.
  Bila bertabrakan, kandidat bergeser 300 blok menjauh (maks 3 kali) sebelum dinilai.
- **Putar** = `hash & 3` → rotasi seluruh kota (Rotation `NONE/CLOCKWISE_90/CLOCKWISE_180/COUNTERCLOCKWISE_90`); semua
  ofset distrik dan lot diputar dengan matriks yang sama.
- **Distrik** (koordinat grid sebelum diputar, sel 144 blok, utara = −z, gerbang utama di selatan):

```
             x = -1             x = 0             x = +1            x = +2 (luar)
 z = -1   [cendekia]         [istana]          [awan]
 z =  0   [gilda]            [pusat]           [pasar]           [kumuh]  (di luar gerbang timur)
 z = +1   [hunian_barat]     [gerbang]         [arena]
 z = +2 (luar)               [luar]  (Lapangan Kafilah, di luar Gerbang Selatan Agung)
```

  Pusat distrik `(x, z)` = pusat kota + `(144x, 144z)`; distrik luar menempel pada tembok: `kumuh` di x + 296, `luar` di
  z + 296 (lebar sel 144, jarak tembok 8). Chunk start distrik = chunk pusat distrik.

##### 38.B.2.3 Placement dan set

- Satu jenis placement umum `kultivator:rencana_tempat` (`dunia/tempat/RencanaPlacement extends
  RandomSpreadStructurePlacement`, pola `GerbangSektePlacement`): field `rencana` (`kota_besar`, `kompleks_sekte`) dan
  `distrik` (string). `isPlacementChunk(state, x, z)` = `RencanaRegistry.get(rencana).isStart(state.getLevelSeed(), x, z,
  distrik)`; `getPotentialStructureChunk` mengembalikan chunk start dalam sel `spacing` (untuk `/locate`). Didaftarkan
  sekali di `TempatSpine.init()`.
- 11 set `data/kultivator/worldgen/structure_set/kota_besar/<distrik>.json` di pack bawaan `kultivator:dunia_kotabesar`
  (`resourcepacks/dunia_kotabesar`), salt 380001-380011, `spacing` 256, `separation` 0 (tidak dipakai rencana).
- 11 struktur `data/kultivator/worldgen/structure/kota_besar/<distrik>.json` bertipe `kultivator:tata_letak`:

```json
{
  "type": "kultivator:tata_letak",
  "biomes": "#kultivator:has_structure/kota_besar/tanah",
  "step": "surface_structures",
  "terrain_adaptation": "beard_box",
  "spawn_overrides": {"monster": {"bounding_box": "full", "spawns": []}},
  "rencana": "kota_besar",
  "distrik": "pasar",
  "lot": [
    {"dx": -48, "dz": -48, "pool": "kultivator:kota_besar/pasar/lelang_agung", "putar": 0},
    {"dx": 0, "dz": -48, "pool": "kultivator:kota_besar/pasar/lelang_agung_b", "putar": 0}
  ],
  "gaya": [
    {"biomes": "#kultivator:kota_besar/gaya_salju", "akhiran": "salju"},
    {"biomes": "#kultivator:kota_besar/gaya_gurun", "akhiran": "gurun"},
    {"biomes": "#kultivator:kota_besar/gaya_sakura", "akhiran": "sakura"}
  ]
}
```

  Codec `TataLetakStructure.CODEC`: `Structure.settingsCodec(i)` + `rencana` (string) + `distrik` (string) + `lot`
  (list `Lot(int dx, int dz, int dy = 0, Holder<StructureTemplatePool> pool, int putar)`; `dx/dz` dari pusat distrik ke
  sudut barat laut lot sebelum diputar) + `gaya` (list `Gaya(HolderSet<Biome> biomes, String akhiran)`).
- `findGenerationPoint(context)`: (1) `Distrik d = RencanaRegistry.get(rencana).distrikDi(seed, chunk)`; bukan
  distriknya → `Optional.empty()`; (2) `Putusan p = PutusanTanah.of(context, d.kandidat())` (38.B.2.4); tidak layak →
  empty; (3) gaya = bioma pusat kota (pertama yang cocok, selain itu dasar) → id pool `<pool>_<akhiran>` bila ada di
  registry, selain itu `<pool>`; (4) untuk tiap lot: elemen = `LotPick.pilih(seed, kandidat.index, distrik, i, pool)`
  (hash berbobot, **bukan** `context.random()`, agar simulasi server memilih elemen yang sama), rotasi = `putar` kota +
  `putar` lot, posisi = pusat + rotasi(dx, dz), Y = `p.dasarY() − groundLevelDelta`; (5) `GenerationStub(pusatDistrik,
  builder -> builder.addPiece(new PoolElementStructurePiece(...)))` untuk semua lot (format simpan vanilla `JIGSAW`).
- `type()` → `TempatTypes.TATA_LETAK` (`Registry.register(BuiltInRegistries.STRUCTURE_TYPE, Kultivator.id("tata_letak"),
  () -> TataLetakStructure.CODEC)`).
- Semua potongan satu distrik ≤ ±80 blok dari chunk start (sel 144 + tembok 8 + menara 8): di dalam jangkauan referensi
  ±8 chunk (38.1) dan, dengan `beard_box` (+12), ≤ 128.

##### 38.B.2.4 Putusan tanah (`dunia/tempat/PutusanTanah`, 38W)

- Sampel 7 × 7 titik berjarak 72 blok di kotak ±216 sekitar pusat: `getBaseHeight(x, z, WORLD_SURFACE_WG, ...)` dan
  `getBaseHeight(x, z, OCEAN_FLOOR_WG, ...)` (air bila `surface > floor`); bioma di 9 pusat distrik dalam lewat
  `context.biomeResolver().getNoiseBiome(QuartPos...)`.
- **Layak** bila: ≤ 3 sampel air; selisih tinggi tanah (maks − min) ≤ `kotaBesar.selisihMaks` (20); median tanah di antara
  `seaLevel + 2` dan `seaLevel + 48`; 9 bioma pusat semuanya dalam `#kultivator:kota_besar/tanah` (semua bioma darat
  Overworld **kecuali** laut, laut dalam, padang jamur, `jagged_peaks`, `frozen_peaks`, `stony_peaks`, `ice_spikes`,
  `deep_dark`). `dasarY = clamp(median, seaLevel + 2, seaLevel + 48)`.
- Hasil di cache `ConcurrentHashMap<Long, Putusan>` (kunci `seed ^ index`, maks 64) karena 11 start dan simulasi server
  menghitung hal yang sama. Biaya: ±100 kolom noise per kandidat (sekali per seed per kandidat).
- Server (simulasi, peta, kabar) memanggil `PutusanTanah.of(ServerLevel, Kandidat)` dengan generator dan `RandomState`
  overworld (`level.getChunkSource().getGenerator()` / `.randomState()`, verifikasi) → hasilnya sama dengan worldgen.

##### 38.B.2.5 Lot, potongan dan tembok

- **Lot** = potongan **48 × H × 48** (H ≤ 48 kecuali menara), lantai jalan di `y = 4` lot (4 lapis fondasi batu di
  bawahnya), tepi lot 3 blok jalan → jalan antar-lot 6 blok, jalan antar-distrik 6 blok + talang. Lot `jalan_raya` di
  kolom tengah (`x = 0`) distrik `istana`, `pusat`, `gerbang` memuat Jalan Kekaisaran 16 blok (batu giok pucat
  `smooth_quartz` + `polished_andesite`, lentera batu tiap 8 blok, pohon pinus dalam pot).
- **Tembok** sebagai lot tambahan distrik tepi (ofset di luar sel 144): `tembok_lurus` 48 × 22 × 8 (batu bata batu
  bertingkat, lorong jaga di y 16, merlon tiap 2 blok, celah panah), `tembok_menara` 16 × 30 × 16 di sudut dan tiap 96
  blok, `gerbang_agung` 48 × 30 × 16 (tiga lengkung, rumah gerbang tiga lantai beratap `roof_hip` dua tingkat; jangkar
  gerbang 38.B.4.1), `gerbang_samping` 48 × 24 × 12 (timur dan barat). Utara (belakang istana) tanpa gerbang.
- **Gaya per bioma** (pool berakhiran, NBT sama, processor berbeda): `dasar` (atap `dark_oak`, dinding `stone_bricks` +
  plester `white_terracotta`, tiang merah `red_terracotta`), `salju` (atap `spruce`, `snow_block` di bubungan lewat aturan
  `rule` pada `dark_oak_slab` atas), `gurun` (atap `jungle`, dinding `cut_sandstone` + `smooth_sandstone`), `sakura`
  (atap `cherry`, aksen `pink_terracotta`). Processor `kota_besar/lapuk_ringan` (lumut 4 %, retak 3 %, `protected_blocks
  #kultivator:sect_protected`), `kota_besar/utuh` (istana, gerbang: tanpa pelapukan).
- **Anggaran templat** (dibuat `tools/gen_tempat_kota_besar.py`, 38KS): ±96 NBT lot (9 distrik × 9 lot dengan 1-2 varian
  untuk lot rumah, ditambah tembok), ±20 NBT tambahan (tembok, menara, gerbang, distrik luar). NBT disimpan gzip; target
  ≤ 3 MB untuk seluruh ibu kota (validasi `tools/cek_tempat.py`, 38.D.2).
- **Titik di NBT** (permintaan kehidupan NPC): `tools/nbt.py` mendapat `Template.titik(jenis, x, y, z, hadap, data=None)`
  (38W, aditif) yang menulis daftar ke kunci akar tambahan `kultivator_titik` di NBT yang sama (`StructureTemplate.load`
  mengabaikan kunci yang tidak dikenalnya, verifikasi). Jenis titik: `kasur`, `kursi`, `kursi_lantai`, `meja_makan`,
  `kerja:<alat>` (alat = `palu`, `tungku`, `lumpang`, `teko`, `meja_tulis`, `rak`, `kios`, `sapu`, `cangkul`, `pancing`,
  `kuali`, `timbangan`, `dupa`), `pintu`, `jalan` (simpul jalur), `main` (tempat anak bermain), `obrol`, `jaga`,
  `latihan`, `meditasi`, `lapak`, `peti_milik`, `bangunan` (kotak bangunan untuk 38.R). Maks 96 titik per lot. Dibaca
  server lewat `TitikTemplat.of(MinecraftServer, Identifier template)` (cache, dari `ResourceManager` data
  `structure/<path>.nbt`), diputar dan digeser sesuai potongan.
- **Jangkar entitas** (`kultivator:dunia_anchor`) hanya untuk **layanan** dan situs (maks 6 per lot, maks 40 per distrik,
  aturan 34.2.4); penduduk tidak memakai jangkar entitas (mereka memakai titik NBT).

##### 38.B.2.6 Jangkar ibu kota

| Jenis | Ditaruh di | Pemilik penangan | Data |
|---|---|---|---|
| `kota/situs` (data `situs="kota/kota_besar"`, tier 5, protect 120) | lot `pusat/alun_agung` | K (publish situs) | — |
| `kota/alun` (protect 128) | `pusat/alun_agung` | K (`Towns.remember`: pusat kota) | — |
| `kota/teh`, `kota/kursi_pengembara` ×2 | `gerbang/kedai_teh` | K | — |
| `kota/pil` ×2, `kota/pusaka` | `pasar/kios_*` | K (toko lama tetap jalan) | — |
| `kota/gelap`, `kota/gerbang_gelap` | `kumuh/sarang_tikus` | K (Pasar Gelap) | — |
| `kota/penjaga` ×6, `kota/papan` | gerbang, alun, gilda | K | — |
| `kotabesar/zona` ×11 (protect 100) | pusat tiap distrik | 38W (`TempatZones`) | `distrik` |
| `kotabesar/gerbang` ×3 | `gerbang_agung`, `gerbang_samping` | 38KS (`GerbangKotaEntity`) | `lebar`, `tinggi` |
| `kotabesar/sel` ×6 | `pusat/yamen` | 38KS (penjara) | `nomor` |
| `kotabesar/layanan` ×±40 | tiap bangunan layanan | 38EK/38KS (per `data.layanan`) | `layanan`, `peran` |
| `kotabesar/panggung` ×4 | arena, alkemis, lelang, alun | 38KS/38EK (acara) | `acara` |
| `kotabesar/cakram` | `awan/stasiun` | 38KS (teleport) | — |
| `kotabesar/dermaga` ×4 | `awan/dermaga` | 38KS | `kelas` |
| `kotabesar/rak_senjata` ×6 | `pasar/toko_senjata`, `gilda/tempa_*` | 38MD (pajangan) + 38EK (stok) | `kelas` |
| `kotabesar/tukang` ×3 | `hunian_barat/bengkel`, `kumuh`, `luar` | 38PR (pos tukang) | — |
| `kotabesar/lapak_sekte` ×6 | `pasar/lapak_sekte` | 38EK (lapak sekte pemain) | `nomor` |

Jangkar `kota/*` membuat ibu kota sekaligus "kota" bagi modul K: Nama Baik, penjaga, toko lama, kedai teh, Pasar Gelap
dan Papan Misi lama tetap jalan tanpa kode baru. `Towns` menerima `kota/kota_besar` sebagai jenis kota (38KS menyunting
`Towns.java`, 38.E.2) dengan `SAFE_RADIUS` per jenis (kota 80, ibu kota 320).

#### 38.B.3 Distrik, bangunan, interior, peran dan layanan

Kolom lot: `(x,z)` dalam grid 3 × 3 distrik (0..2, sebelum diputar); "→" = layanan dan modul. Semua bangunan punya interior
lengkap (lantai, perabot vanilla, cahaya, titik NBT); tinggi dalam blok. Nama peran = `NpcRole` baru (38.N.2).

##### `istana` — Distrik Istana (utara tengah)

| Lot | Isi |
|---|---|
| (0,0) `taman_belakang` | taman batu dan kolam teratai, paviliun baca, titik `meditasi` ×6 |
| (1,0)+(1,1) `istana_a/b` | **Istana Pengawal Kota**: aula takhta 32 × 20 × 18 (tiang merah 2×2, langit-langit kayu berpanel, takhta `quartz_stairs` + `gold_block` sandaran), dua sayap kantor; atap `roof_hip` tiga tingkat; peran PANGERAN_KOTA, MENTERI ×2, PENGAWAL_ISTANA ×4 → audiensi, Perintah Istana (38KS) |
| (2,0) `balai_pertemuan` | **Balai Pertemuan Sekte**: meja bundar delapan kursi (titik `kursi` ×8), papan lambang 8 sekte (gambar klien) → Pertemuan Sekte 30-harian (38KS) |
| (0,1) `kantor_arsip` | Kantor Catatan Sipil: catatan Nama Baik, pendaftaran lapak (38EK) |
| (2,1) `barak_pengawal` | barak (kasur ×16), lapangan latihan tombak (titik `latihan` ×8) |
| (0,2),(1,2),(2,2) | `gerbang_istana` (paifang + gerbang dalam ditutup malam: `GerbangKotaEntity` kecil), `jalan_raya_istana`, `rumah_pejabat` |

Sumber (`kultivator.kota_besar.istana.inspired`): "Terinspirasi dari tata sumbu Kota Terlarang Beijing (aula di garis
tengah, gerbang berlapis) dan paviliun para tetua sekte dalam novel kultivasi; rancangan game."

##### `cendekia` — Distrik Cendekia (barat laut)

| Lot | Isi |
|---|---|
| (0,0)+(1,0) `perpustakaan_a/b` | **Perpustakaan Agung**: tiga lantai rak (`bookshelf`, `chiseled_bookshelf`), meja baca (titik `meja_tulis` ×12), Aula Kuliah (bangku ×20), Kantor Peta (meja peta `cartography_table`) → baca kitab, salin giok, arsip kabar, kuliah fana, peta wilayah (38EK harga, 38KS kuliah) |
| (2,0) `menara_pengamat` | **Menara Pengamat Langit** 16 × 46 × 16 dalam lot taman: tujuh lantai pagoda, puncak terbuka (titik `meditasi` ×4) → bonus peristiwa langit (38KS) |
| (0,1)+(1,1) `kuil_langit_a/b` | **Kuil Langit**: halaman batu, pembakar dupa besar (tungku batu `polished_andesite` dengan `soul_lantern` di atasnya; asap dupa = partikel klien), aula Tiga Murni, menara lonceng kecil → dupa, restu, situs meditasi ×1.2 (38KS) |
| (2,1) `sekolah` | sekolah anak (bangku ×12, titik `main` ×6) — anak-anak NPC pagi di sini |
| (0,2),(1,2),(2,2) | `rumah_cendekia` ×2, `taman_tinta` (kolam, batu sastra) |

##### `awan` — Distrik Awan (timur laut)

| Lot | Isi |
|---|---|
| (1,0)+(2,0) `dermaga_a/b` | **Dermaga Langit**: dua menara dermaga 12 × 44 × 12 dengan jembatan kayu di y +32, empat tambatan (jangkar `kotabesar/dermaga`), gudang angkut → rute kapal, bahan bakar, lunas, Perjalanan Awan (38KS, harga 38.M) |
| (0,0) `kantor_pelabuhan` | loket tiket, ruang tunggu (kursi ×10) |
| (1,1) `stasiun_teleport` | **Stasiun Formasi Teleportasi**: cakram batu 13 × 13 berukir (`chiseled_stone_bricks`, `polished_blackstone` pola trigram), empat tiang kristal (`amethyst_cluster` di atas `calcite`) → teleport berbayar (38KS) |
| (0,1),(2,1),(0..2,2) | `gudang` ×2, `penginapan_awak`, `bengkel_kapal`, `rumah` |

##### `gilda` — Distrik Gilda (barat)

| Lot | Isi |
|---|---|
| (0,0)+(1,0) `gilda_alkemis_a/b` | **Gilda Alkemis**: aula pemeriksaan, delapan stasiun kuali di atas api unggun menyala (`cauldron` + `campfire[lit=true]`, panggung bundar 20 blok, tribun 3 undakan) = **Panggung Pertemuan Pil**, gudang herba, ruang ujian → toko herba/pil bergating tingkat, ujian Tingkat Alkemis 1-9, Tungku Gilda (38EK), Pertemuan Pil (38KS acara) |
| (2,0)+(2,1) `gilda_tempa_a/b` | **Gilda Pandai Pusaka**: empat bengkel (`anvil`, `blast_furnace`, `smithing_table`, `grindstone`, cerobong bata), ruang pajang senjata (rak `kotabesar/rak_senjata` ×4), Altar Jiwa Pusaka → toko senjata/zirah/pusaka, pesanan tempa, perbaikan, penilaian (38EK) |
| (0,1)+(1,1) `gilda_kultivator_a/b` | **Gilda Kultivator**: aula papan misi (papan besar 9 × 5 dengan kertas = billboard klien), loket peringkat, batu Daftar Naga Muda (tugu `polished_basalt`), ruang uji duel (lantai `smooth_stone` 15 × 15) → misi E-S, lencana, ujian (38EK) |
| (0,2) `balai_tabib` | **Balai Tabib**: ranjang perawatan ×6, lemari obat → pulihkan Penyimpangan Qi berbayar, sembuhkan murid terluka (38EK) |
| (1,2),(2,2) | `asrama_gilda`, `kedai_bakmi` |

##### `pusat` — Distrik Pusat (tengah)

| Lot | Isi |
|---|---|
| (1,1) `alun_agung` | **Alun-alun Agung** 40 × 40: Tugu Pendiri (patung batu `stone`/`andesite` 12 tinggi), kolam delapan sudut, Papan Kabar (billboard klien), jangkar `kota/situs` dan `kota/alun` |
| (0,0) `menara_genderang` | **Menara Genderang** 16 × 36 × 16 (genderang = `note_block` + gambar klien) → jam malam 22000 / buka 1000 |
| (2,0) `menara_lonceng` | **Menara Lonceng** (lonceng `bell` di lantai atas) → lonceng pagi |
| (0,1) `alun_penerimaan` | **Alun-alun Penerimaan**: panggung, delapan lapak sekte bertenda (tenda = wol + `oak_fence`), jangkar `kotabesar/panggung` → Hari Pembukaan Gerbang (38KS) |
| (2,1) `yamen` | **Kantor Yamen**: ruang sidang (meja hakim, genderang pengaduan di depan), enam sel penjara (`iron_bars`, pintu = `KotaPintuEntity` pola, jangkar `kotabesar/sel`) → denda, buronan, sel (38KS) |
| (0,2) `taman_kota` | taman, kolam ikan, gazebo (titik `obrol` ×8, `pancing` ×3) |
| (1,0),(1,2) | `jalan_raya_pusat` ×2 |
| (2,2) `kantor_pos` | **Kantor Pos dan Kabar**: loket surat, burung merpati (gambar klien) → kirim surat ke kampung terikrar, kabar (38ST) |

##### `pasar` — Pasar Timur (timur)

| Lot | Isi |
|---|---|
| (1,0)+(2,0) `lelang_agung_a/b` | **Balai Lelang Agung**: aula 40 × 22 × 30, panggung berputar (gambar klien), kursi berderet (titik `kursi` ×48), lantai dua bilik VIP ×6 → Lelang Agung terpadu (38EK) |
| (0,0) `rumah_uang` | **Rumah Uang Seribu Tael**: meja loket ×4, brankas (lemari besi = `iron_block` + `barrel`), timbangan → simpanan, Surat Wesel, gadai, tukar (38EK) |
| (0,1) `rumah_judi_batu` | **Rumah Batu Sumber**: rak batu mentah (`stone`, `deepslate`, `tuff` bertumpuk), meja potong (`stonecutter`), kursi penonton → judi batu (38EK) |
| (1,1) `pasar_binatang` | **Pasar Binatang Roh**: kandang (`oak_fence`, `hay_block`), kolam, papan harga → anak binatang, sesaji, pelatih, inti (38EK) |
| (2,1) `toko_senjata` | **Paviliun Seribu Bilah**: rak senjata (jangkar `kotabesar/rak_senjata` ×2), patung zirah (`armor_stand` template entity dengan zirah vanilla) → toko senjata/zirah (38EK) |
| (0,2) `kios_pil` | dua paviliun (jangkar `kota/pil` ×2) + toko jimat |
| (1,2) `lapak_sekte` | **Deret Lapak Sekte**: enam kios sewaan (jangkar `kotabesar/lapak_sekte`) → lapak sekte pemain (38EK) |
| (2,2) `pasar_malam` | jalan kios (12 kios, titik `lapak` ×12) → Pasar Malam (38KS acara + 38EK stok) |

##### `hunian_barat` (barat daya), `arena` (tenggara)

- `hunian_barat`: 9 lot rumah dari pool `kota_besar/hunian/rumah` (rumah halaman siheyuan 20 × 20, rumah toko, rumah
  susun kayu 3 lantai, sumur, kuil kecil, `bengkel` dengan jangkar `kotabesar/tukang`, `rumah_makan_kecil`). Penduduk
  data tinggal di sini (38.N).
- `arena`: (0,0)+(1,0)+(0,1)+(1,1) `arena_agung_a..d` = **Arena Agung** 96 × 96: lei tai 21 × 21 di tengah (batu
  `polished_andesite`, tepi `red_nether_bricks`), tribun 6 undakan di empat sisi (titik `kursi` ×160 untuk penonton
  impostor, 38.N.6), ruang juara, panji sekte (gambar klien) → Turnamen Agung, duel berpenonton (38KS); sisa lot rumah.

##### `gerbang` (selatan tengah), `kumuh`, `luar`

- `gerbang`: (1,2) Gerbang Selatan Agung (lot tembok), (1,0)+(1,1) `jalan_raya_gerbang`, (0,0) `penginapan_awan_singgah`
  (3 lantai, 12 kamar, kasur ×12), (2,0) `penginapan_bulan_perak`, (0,1) `rumah_makan_teratai` (dapur `smoker`,
  `campfire` menyala, meja ×10), (2,1) `kedai_teh_agung` (jangkar `kota/teh`), (0,2)+(2,2) `jalan_lentera` (tiang
  lentera tinggi tiap 6 blok = titik pemasangan lentera festival).
- `kumuh` (di luar gerbang timur): gubuk (`spruce_planks` lapuk, atap `oak_slab` miring), lorong sempit, Pasar Gelap
  (`kota/gelap`), dapur umum, titik `main` anak jalanan, `kotabesar/tukang`. Tanpa tembok sendiri.
- `luar` (di luar gerbang selatan): **Lapangan Kafilah**: kandang kuda (vanilla `horse` sebagai entitas templat, tidak
  dijinakkan, 4 ekor), gerobak (entitas 38MD), Pos Kafilah (jangkar `kotabesar/layanan` `kafilah`), kios jalan, awal
  Jalan Kekaisaran ke luar (sambungan jalan 38.C.6).

Baris Sumber per bangunan layanan: kunci `kultivator.kota_besar.<layanan>.inspired`, ditulis 38ST (contoh: Rumah Uang:
"Terinspirasi dari 'uang terbang' (feiqian) dinasti Tang, surat wesel yang bisa ditukar di kota lain, dan rumah uang
(qianzhuang) dinasti Ming-Qing; rancangan game." Judi batu: "Terinspirasi dari judi batu sumber dalam Shrouded the Heavens
(Zhe Tian) karya Chen Dong; rancangan game." Gilda Alkemis: "Terinspirasi dari Persekutuan Alkemis dan Pertemuan Pil di
Battle Through the Heavens; rancangan game." Gelombang Binatang: "Terinspirasi dari gelombang binatang buas (shou chao)
yang menyerang kota dalam banyak novel xianxia; rancangan game.").

#### 38.B.4 Layanan sipil (38KS)

##### 38.B.4.1 Gerbang kota dan jam malam

- `GerbangKotaEntity` (`dunia/tempat/kotabesar/GerbangKotaEntity`, pola `KotaPintuEntity` + `ColliderEntity`): daun pintu
  kembar (model 38.O), `canBeCollidedWith → true` saat tertutup, kotak = lubang gerbang dari data jangkar; `noSave`
  (dimunculkan ulang dari jangkar); kesehatan 600 hanya untuk Gelombang Binatang (38.B.6.3).
- Genderang malam di 22000 (Menara Genderang, suara `minecraft:block.note_block.basedrum` ×12, `level.playSound(null, …)`):
  gerbang menutup dalam 6 s (animasi 38.O), membuka di 1000. Pemain di luar saat tutup: bicara dengan PENJAGA_GERBANG →
  "Buka" 2 NB (Nama Baik ≥ 0) atau gratis untuk Terhormat; buronan ditolak.
- Melompati tembok saat jam malam dan dilihat penjaga = kejahatan 3 (38.B.5.2).

##### 38.B.4.2 Istana dan Perintah Istana

- Audiensi (PANGERAN_KOTA, Nama Baik ≥ 80, 10000-12000): layar dialog 38.U, gelar "Tamu Kehormatan Istana" (lang), lalu
  **Perintah Istana** 30-harian: tiga tugas berskala ranah (bersihkan `gua/sarang_iblis` dikenal terdekat; kawal kafilah
  ke kota lain lewat jalan; kalahkan bos `gua/sarang_binatang`). Imbalan per tugas = `MisiGilda.imbalan(pangkat A,
  band)` × 1.5 (38.M.3), + barang langka 1 per 30 hari. Nama "Perintah Istana" (bukan "Titah", audit 4 butir 9).
- Balai Pertemuan Sekte (hari `d % 30 == 15`, 6000-9000): wakil 4-8 sekte NPC duduk (tubuh `JUARA_SEKTE` tanpa senjata,
  38.N); pemain boleh mengajukan satu aksi diplomasi (pakta/hadiah) lewat `DiplomasiService.adjust(owner, sekte, +5,
  "pertemuan")` sebagai bonus di atas aksi D yang ada; standing tidak pernah melewati batas D.

##### 38.B.4.3 Teleportasi dan Perjalanan Awan

- Stasiun (`kotabesar/cakram`), pelayan PENJAGA_FORMASI. Tujuan = situs yang **diketahui** pemain (`DuniaSites.known`) dari
  jenis `kota/kota`, `kota/kota_besar`, `kampung/besar`, `kampung/klan`, `pos/singgah_besar`, `sekte/kompleks` (sikap
  ≥ NETRAL, `DiplomasiService.stance`). Titik tiba = `TempatApi.arrival(site)` (jangkar `kota/alun`, lonceng kampung,
  cakram pos, gerbang kompleks; tidak pernah heightmap, 38.0 butir 12).
- Ongkos `ceil(2 + jarak / 250)` NB per orang, maks 120; murid pengikut dalam 8 blok ikut (maks 4) dengan ongkos sama.
  Kanal 3 s (`Sibuk` `tempat.teleport`, batal bila terkena serangan), `DuniaTickets.touch(tujuan, 1)` di awal kanal,
  lalu `teleportTo`. Adegan klien 38.O "Pilar Formasi" (Minimal: kilat layar 0.3 s).
- **Perjalanan Awan** (Dermaga Langit): tujuan diketahui ≥ 600 blok, siang 1000-11000, ongkos `ceil(6 + jarak / 150)`
  NB, adegan perahu awan 6 s (bisa dilewati) lalu teleport seperti di atas. Bedanya dengan stasiun: jarak tak terbatas,
  dan tujuan boleh dermaga istana langit sekutu (37).
- Rute kapal pemain: `DuniaApi.routes().registerDestination("kota_besar_" + index, nama, (p, l) -> diketahui(p),
  (p, ents) -> tibaDiDermaga)` untuk setiap ibu kota yang diketahui.

##### 38.B.4.4 Kuil, perpustakaan, menara pengamat, balai tabib

- **Kuil Langit:** dupa 3 NB (maks 1/jam) → "Restu Kuil" 60 menit: +0.05 pada peluang gulir hadiah 38 (bukan judi),
  sekali per hari dunia tekanan iblis hati −5. Meditasi di halaman: situs `SpiritSites` ekstra "Kuil Langit" ×1.2.
  Lonceng pagi 1000: NPC di distrik membungkuk ke arah kuil (38.N).
- **Perpustakaan:** harga 38.M; kuliah fana harian 9000-10000 (PENCERAMAH_FANA, ranah ≤ Nascent Soul, menyelinap atau
  meditasi dalam 6 blok selama 60 s → +1 pemahaman, sekali per hari; pola `KotaAbadi` kuliah).
- **Menara Pengamat Langit:** selama peristiwa langit, pemain di puncak (kotak jangkar) mendapat: pecahan meteor ×2
  dari `GreatMeteors`, peluang Kristal Gerhana 50 % (batas harian lama tetap) — lewat kait baca `TempatApi.viewing(player,
  "langit")` yang dibaca 38GU di kait L (38.E.2).
- **Balai Tabib:** pulihkan Penyimpangan Qi 20 × (band + 1) NB; sembuhkan murid "Terluka" (`Disciples` away pulih) 15 ×
  (band + 1) NB per murid (verifikasi API publik pemulihan; bila tidak ada, layanan ini hanya untuk pemain).

#### 38.B.5 Nama Baik ibu kota dan hukum (38KS)

##### 38.B.5.1 Pangkat Nama Baik (memakai `KotaData.reputation(townId, uuid)` yang ada)

| Nama Baik | Pangkat (`kultivator.kota_besar.pangkat.<id>`) | Akibat di ibu kota |
|---|---|---|
| ≤ −30 | Musuh Kota | penjaga bermusuhan (K), toko menolak (K), gerbang menolak |
| −29..−1 | Orang Asing | harga +10 % di layanan 38 |
| 0..19 | Tamu | normal |
| 20..49 | Warga Dikenal | brankas 18 slot, penginapan −10 % |
| 50..79 | Terhormat | diskon K 10 %, biaya ujian gilda −20 %, buka gerbang malam gratis |
| 80..100 | Pahlawan Kota | diskon 15 % (dibatasi 38.0 butir 4), audiensi istana, 1 teleport gratis/hari, nama di Papan Pahlawan (billboard klien) |

Penambah: misi gilda +2..+4 (menurut pangkat misi), membela kota +5 (+10 kontributor teratas), teka-teki festival +1
(maks 3 per festival), menang lelang +1, juara turnamen +5, juara Pertemuan Pil +3, membayar tagihan ganti rugi +1
(38.R), donasi perbaikan +1 per 20 NB (maks +10/hari).

##### 38.B.5.2 Catatan Kejahatan, buronan, denda dan sel

- `KotaBesarData` (SavedData `kultivator:kota_besar`) → `kejahatan.<town>.<uuid>` = poin (0-200), turun 2 per hari dunia.
- **Kejahatan dan poin:** melukai warga 5, membunuh warga 20, membunuh penjaga 30, mencuri dari `peti_milik` (membuka
  peti titik `peti_milik` sambil terlihat) 4, merusak bangunan (38.R, per 10 blok) 2, masuk istana dalam 22000-1000 tanpa
  Nama Baik ≥ 50 6, tidur di penginapan tanpa bayar 2, melompati tembok saat jam malam 3, memulai Duel Pedang di dalam
  tembok di luar Arena Agung atau lapangan uji 2, menolak tagihan ganti rugi (38.R) `tagihan / 10`.
- **Saksi:** dihitung hanya bila tubuh PENJAGA dalam 24 blok atau tubuh warga dalam 16 blok dengan `hasLineOfSight`, atau
  penduduk data di lot yang sama (peluang 50 %, 38.N.6). Kejahatan tanpa saksi tidak dicatat (tetapi kerusakan blok tetap
  tercatat sebagai kerusakan oleh 38.R).
- **Ambang:** ≥ 10 **Dicurigai** (penjaga membuntuti 30 s dan memperingatkan, baris suara), ≥ 25 **Buronan** (poster di
  papan gilda dan Yamen = billboard klien bergambar kepala pemain, penjaga menyerang saat melihat, toko 38 / stasiun /
  dermaga menolak), ≥ 60 **Buronan Berat** (KAPTEN_PENJAGA tingkat 4 diutus dan memburu dalam distrik; `markRealmFoe`).
  Setiap poin juga −½ Nama Baik.
- **Penyelesaian:** bayar denda `poin × 3 × (band + 1)` NB di Yamen atau kepada penjaga yang menghentikan (dialog
  "Bayar Denda"), lalu poin 0 dan Nama Baik dipulihkan separuh dari yang hilang; atau **Serahkan Diri**: teleport ke sel
  bebas (`kotabesar/sel`), pintu tertutup, waktu `poin × 6 s` (maks 6 menit, `Sibuk` `tempat.sel`, murid diveto), barang
  tidak disita, lalu poin 0. Kabur (keluar sel dengan cara apa pun) = +20 poin dan Buronan.
- **Tidak ada** buronan antar-pemain (memaksa PvP melanggar aturan 2 DESIGN 0); perburuan hanya oleh NPC.

#### 38.B.6 Acara ibu kota (38KS) — kalender `KotaBesarKalender`

Hari dunia `d = overworldClockTime / 24000`. Paling banyak **satu acara besar** per hari per ibu kota (prioritas:
Pertahanan > Turnamen > Lelang Agung > Pertemuan Pil > Hari Gerbang > Festival); acara yang bertabrakan bergeser ke hari
berikutnya. Acara hanya **hidup** bila ada pemain dalam 256 blok dari pusat; selain itu dijalankan sebagai data (hasil
singkat ke `KabarDunia`). Semua memanggil `Sibuk.why` untuk adegan dan menunda bila sibuk.

| Acara | Jadwal | Isi | Modul |
|---|---|---|---|
| Pasar Malam | setiap hari 12500-18000 | 12 kios `pasar/pasar_malam` buka, 6 tawaran berputar, lentera kios menyala (klien) | 38KS + 38EK stok |
| Pertemuan Pil | `d % 7 == 2`, 6000-9000 | lomba alkimia 38.B.6.2 | 38KS (38EK skor) |
| Lelang Agung | `d % 7 == 5`, 12000-15000 | lelang terpadu 8 lot (38.M.2.3) | 38EK |
| Hari Pembukaan Gerbang | `d % 10 == 4`, 6000-12000 | lapak sekte, calon murid (38.M.4.3) | 38KS |
| Turnamen Agung Antar-Sekte | `d % 14 == 9`, 6000-11000 | 38.B.6.4 | 38KS |
| Festival Lentera | malam bulan purnama (`moonPhase == 0`), 13000-18000; batal saat Gerhana Darah | 38.B.6.1 | 38KS |
| Pertemuan Sekte | `d % 30 == 15` | 38.B.4.2 | 38KS |
| Gelombang Binatang | tiap 20 ± 4 hari (hash), atau malam setelah Gerhana Darah (50 %) | 38.B.6.3 | 38KS |

##### 38.B.6.1 Festival Lentera

- Lentera langit (quad klien naik perlahan, tekstur lentera kertas merah/emas; Minimal 15, Normal 60, Maksimal 150,
  `EpicBudget.takeQuads`), lentera jalan menyala di titik `jalan_lentera`, kembang api quad tiap 20 s 15000-16000 (bukan
  `FireworkRocketEntity`).
- **Teka-teki Lentera:** 6 kios PENJAGA_TEKA; layar pilihan tiga jawaban; 40 teka-teki milik 38ST (ditulis sendiri,
  bukan salinan); benar → Jimat Lentera (item kosmetik, `kultivator:jimat_lentera`) + Nama Baik +1 (maks 3), salah →
  tawa NPC (animasi `tertawa`).
- NPC: semua penduduk data ke `pasar`, `gerbang`, `pusat` (jadwal festival 38.N.3), anak-anak membawa lentera tangan
  (prop 38.O), pasangan berjalan di Jalan Lentera.
- Sumber (`kultivator.kota_besar.festival.source`): "Sumber: Festival Lentera (Yuanxiao) pada malam bulan purnama pertama
  tahun baru Imlek, dengan teka-teki yang digantung di lentera (cai dengmi). Jadwal tiap purnama adalah rancangan game."

##### 38.B.6.2 Pertemuan Pil

- Daftar di meja gilda (biaya `20 × (band + 1)` NB, band = band ranah pemain); dapat **Resep Lomba** (resep yang
  diketahui pemain atau bahan dijual gilda dengan harga pokok); waktu 5 menit nyata di salah satu delapan stasiun
  (titik `kerja:kuali`). Skor = `mutu × 100 + max(0, 300 − detik) / 3 + 50` bila resep berpita ≥ band; dibaca dari kait
  selesai `AlchemyFurnace.addFinishListener` (38EK menambahkannya, 38.E.2).
- 5 pesaing NPC (ALKEMIS, tubuh di stasiun lain, skor `N(μ = 150 + 40 × band, σ = 40)`), papan skor hidup (38.U).
- Hadiah: juara 1 = 4 × biaya + 1 herba langka dari tabel gilda (maks 1 per minggu); juara 2 = 2 × biaya; juara 3 =
  biaya kembali; Nama Baik +3/+2/+1; gelar "Juara Pertemuan Pil"; poin tingkat alkemis +2/+1/+0.

##### 38.B.6.3 Gelombang Binatang (pertahanan kota)

- Pertanda 1 jam dunia sebelumnya: genderang perang, gerbang ditutup awal, PENJAGA ×8 naik ke lorong tembok (titik
  `jaga`), warga lari ke rumah (38.N.5), baris suara `kotabesar.beast_tide`.
- Tiga gelombang selama 3 jam dunia malam di luar satu gerbang (bergilir): monster vanilla yang diizinkan (zombie
  `setCanBreakDoors(false)`, skeleton, spider; **tidak pernah** creeper/enderman, DESIGN 0) + binatang elit (`elite/*`,
  verifikasi API spawn) berskala ranah pemain terkuat yang ikut; jumlah per gelombang `8 + 4 × pemain` (maks 24 hidup),
  muncul ≥ 24 blok di luar kotak tembok dengan `TempatApi.groundSpot`; `markRealmFoe` untuk elit. Target: penjaga,
  pemain, dan `GerbangKotaEntity`.
- Gerbang roboh bila kesehatannya habis (entitas terbuka; **tidak ada** blok berubah): monster masuk distrik `gerbang`;
  kalah = toko 38 tutup 1 hari, kabar "kota terluka". Menang = Nama Baik +5 (+10 kontributor kerusakan terbesar),
  `30 × (band + 1)` NB per pemain dari Kas Kota (sink yang sudah terkumpul dari denda/komisi, 38.M.5), loot monster biasa.
  Kerusakan blok oleh monster (mis. api) diperbaiki 38.R.
- `Sibuk` `tempat.pertahanan` bagi pemain dalam 64 blok dari gerbang yang diserang; masuk tekanan harian M2.

##### 38.B.6.4 Turnamen Agung Antar-Sekte

- Delapan kursi: sekte pemain (pemain sendiri sebagai Wakil, atau murid terbaik pilihan pemain) + sampai 7 sekte NPC dari
  `DiplomasiService.sects()` (yang tidak sedang perang dengan penyelenggara; urut tingkat). Juara sekte NPC = tubuh
  `KultivatorNpc` peran JUARA_SEKTE bernama dan berlambang sekte itu, ranah `tier`-nya (pola `Rivals`).
- Babak pemain/murid melawan juara NPC: Duel Pedang `DuelChallenges.challenge(...)` dengan aturan arena (90 s, berhenti
  10 %, tanpa taruhan; verifikasi `DuelRuleSet`). Babak NPC melawan NPC: satu babak tampil hidup (dua tubuh, AI duelist
  35.7) bila pemain menonton, sisanya data (`peluang = 0.5 + 0.08 × (tierA − tierB)`).
- Hadiah: biaya ikut `50 × (band + 1)` NB; juara = 4 × biaya + trofi **Piala Langit Agung** (item hias, model 38.O) +
  `DiplomasiService.adjust(+10)` dengan setiap sekte yang dikalahkan sportif (maks +20 per turnamen); Nama Baik +5.
  Penonton impostor penuh di tribun (38.N.6).

#### 38.B.7 Penduduk dan anggaran NPC ibu kota (rincian di 38.N)

- **Penduduk data** per ibu kota (dibangkitkan deterministik dari seed + indeks, hanya perubahan yang disimpan):
  480 warga (keluarga 2-5 orang per rumah), 60 pedagang/pemilik layanan, 40 penjaga, 20 pejabat/pelayan istana,
  20 rahib/cendekia, 30 anggota gilda = **650**.
- **Tubuh** (entitas `KultivatorNpc`): `kotaBesar.tubuhMaks` 28 per ibu kota (12-48), per pemain 16 dalam 64 blok, di luar
  topi `DuniaBudget` jenis `npc` (jenis baru `npc_kota_besar` lewat `DuniaBudget.setCap`), ditambah ≤ 14 tubuh peran K
  (`npcPerTown`). Penonton/keramaian jauh = **impostor klien**, bukan entitas.
- LOD: L0 ≤ 24 blok tubuh penuh + animasi penuh; L1 24-48 tubuh dengan jalan lurus antar-simpul, tanpa obrolan; L2 48-128
  impostor klien (≤ 48 per pemain, 2 Hz posisi); L3 > 128 data saja (38.N.6).

#### 38.B.8 Suara baru ibu kota (Jepang; ditambahkan 38ST ke `tools/gen_voices.py` `WORLD`, grup `kotabesar.*`)

| Kunci | Suara | Jepang | English | Indonesia | Emosi |
|---|---|---|---|---|---|
| `kotabesar.gate_welcome` | m1 | ようこそ、都へ。城内での私闘は禁じられている。 | Welcome to the capital. Private fights are forbidden within the walls. | Selamat datang di ibu kota. Bertarung sendiri di dalam tembok dilarang. | normal |
| `kotabesar.curfew` | m1 | 夜鼓が鳴った。門を閉めるぞ！ | The night drum has sounded. Closing the gates! | Genderang malam sudah berbunyi. Gerbang ditutup! | angry |
| `kotabesar.suspect` | m1 | おい、そこの者。妙な真似はするなよ。 | You there. Don't try anything strange. | Hei, kau. Jangan macam-macam. | angry |
| `kotabesar.wanted` | m1 | 手配書の顔だ！捕らえろ！ | That's the face on the wanted poster! Seize them! | Itu wajah di surat buronan! Tangkap! | angry |
| `kotabesar.fine_paid` | m2 | 罰金は納められた。行ってよし。 | The fine is paid. You may go. | Denda sudah dibayar. Silakan pergi. | calm |
| `kotabesar.jail` | m1 | 牢で頭を冷やすがいい。 | Cool your head in the cell. | Dinginkan kepalamu di sel. | calm |
| `kotabesar.bank_welcome` | m2 | 銭荘へようこそ。霊石はしかとお預かりいたします。 | Welcome to the money house. Your spirit stones are safe with us. | Selamat datang di rumah uang. Batu rohmu aman bersama kami. | calm |
| `kotabesar.bank_note` | m2 | この為替手形は、どの都の銭荘でも換金できます。 | This note can be cashed at the money house of any capital. | Surat wesel ini bisa ditukar di rumah uang ibu kota mana pun. | normal |
| `kotabesar.gamble_cut` | m3 | 切るぞ…さあ、天か地か！ | Cutting... now, heaven or earth! | Kupotong… nah, langit atau bumi! | joy |
| `kotabesar.gamble_win` | m3 | 出た！霊光だ！ | It's out! Spirit light! | Keluar! Cahaya roh! | joy |
| `kotabesar.gamble_lose` | m3 | ただの石…これもまた天命よ。 | Just stone... that too is fate. | Cuma batu… itu pun takdir. | calm |
| `kotabesar.grand_auction` | m2 | 大競売会、ただいま開幕でございます！ | The Grand Auction now begins! | Lelang Agung kini dimulai! | joy |
| `kotabesar.alchemy_start` | elder | 丹会を始める。炉に火を入れよ！ | The Pill Gathering begins. Light your furnaces! | Pertemuan Pil dimulai. Nyalakan tungkumu! | normal |
| `kotabesar.alchemy_winner` | elder | 見事な丹だ。今回の首席はそなたである。 | A splendid pill. You stand first this time. | Pil yang indah. Kali ini kaulah yang pertama. | joy |
| `kotabesar.smith_master` | m1 | 良い鉄は、良い火を待つものだ。 | Good iron waits for good fire. | Besi yang baik menunggu api yang baik. | calm |
| `kotabesar.guild_bounty` | f2 | 新しい依頼が出ています。腕に覚えは？ | New commissions are posted. Confident in your skill? | Ada misi baru. Yakin dengan kemampuanmu? | normal |
| `kotabesar.guild_rank` | f2 | おめでとうございます、位が一つ上がりました。 | Congratulations, your rank has risen. | Selamat, pangkatmu naik satu. | joy |
| `kotabesar.recruit_call` | m1 | 我が宗門に入りたい者は、前へ！ | Those who wish to join our sect, step forward! | Yang ingin masuk sekte kami, maju! | joy |
| `kotabesar.beast_market` | m3 | この子は満月の夜に生まれた霊狼だよ。 | This one is a spirit wolf born under a full moon. | Yang ini serigala roh yang lahir di malam purnama. | normal |
| `kotabesar.inn_welcome` | f1 | いらっしゃいませ。お泊まりですか、お食事ですか？ | Welcome! Staying the night, or here to eat? | Selamat datang! Mau menginap atau makan? | joy |
| `kotabesar.library_hush` | elder | 書閣ではお静かに。 | Quiet in the library, please. | Harap tenang di perpustakaan. | calm |
| `kotabesar.temple_bell` | elder | 鐘の音に心を澄ませよ。 | Let the bell clear your heart. | Biarkan lonceng menjernihkan hatimu. | calm |
| `kotabesar.teleport_ready` | f3 | 陣が開きます。目を閉じてください。 | The array is opening. Please close your eyes. | Formasi terbuka. Silakan pejamkan mata. | calm |
| `kotabesar.dock_depart` | m2 | 出航！雲の上で会おう！ | Setting sail! See you above the clouds! | Berangkat! Sampai jumpa di atas awan! | joy |
| `kotabesar.palace_audience` | boss | 面を上げよ。 | Raise your head. | Angkat kepalamu. | calm |
| `kotabesar.lantern_festival` | f1 | 灯籠祭りが始まりました！謎かけはいかが？ | The Lantern Festival has begun! Care for a riddle? | Festival Lentera sudah dimulai! Mau teka-teki? | joy |
| `kotabesar.riddle_right` | f1 | 正解です！お見事！ | Correct! Well done! | Benar! Hebat! | joy |
| `kotabesar.beast_tide` | m1 | 獣潮だ！城壁へ急げ！ | A beast tide! To the walls, quickly! | Gelombang binatang! Cepat ke tembok! | angry |
| `kotabesar.defense_won` | m1 | 都は守られた！ | The capital is saved! | Ibu kota selamat! | joy |
| `kotabesar.tournament_open` | boss | 天下の英傑よ、この擂台で力を示せ！ | Heroes of the realm, prove your strength upon this stage! | Para pahlawan dunia, tunjukkan kekuatan kalian di panggung ini! | normal |

Baris tempat lain (kampung, kompleks, gua, jalan, perbaikan, kehidupan NPC) ada di 38.S.6.

---------------------------------------------------------------------------------------------------------------------

### 38.C Tempat lain dan world generation yang detail

#### 38.C.1 Tiga mekanisme dan kapan dipakai

| Mekanisme | Dipakai untuk | Batas |
|---|---|---|
| **Jigsaw vanilla** (`minecraft:jigsaw`, potongan ≤ 48 × 48, `size` ≤ 7) | kampung, desa klan, gua, situs, pos, kuil jalan, tonggak | radius ≤ 116 (dengan adaptasi), satu start |
| **Tata letak** (`kultivator:tata_letak`, 38.B.2.3) | Kota Besar (11 start), kompleks sekte (1-3 start) | lot 48 × 48 tetap, rencana bersama |
| **Jalan** (`kultivator:jalan`, 38.C.6) | jalan, jembatan, tangga jalan | satu start per chunk yang dilewati |

Semua set baru memakai placement milik 38W: `kultivator:cincin_tempat` (random spread + `min_chunks` + frekuensi per
cincin jarak, 38.D.1), `kultivator:rencana_tempat` (ibu kota, kompleks, titik sepanjang jalan) atau `kultivator:jalan`.
Semua struktur berpenghuni memakai `spawn_overrides` `monster` kosong, processor dengan `protected_blocks
#kultivator:sect_protected`, cahaya dalam ruang ≥ 8 dan `HIDDEN_LIGHT` di bawah atap tinggi (38.0 butir 12), dan titik
NBT `kultivator_titik` (38.B.2.5). Generator: `tools/gen_tempat_kampung.py` + `tools/gen_tempat_kompleks.py` (38DS),
`tools/gen_tempat_gua.py` (38GU), `tools/gen_tempat_jalan.py` (38W), `tools/gen_tempat_kota_besar.py` (38KS); pustaka
bersama `tools/tempat_lib.py` (38W: lot 48, jalan tepi, gaya bioma, helper titik, palet `kampung`, `klan`, `kompleks_lurus`,
`kompleks_netral`, `kompleks_iblis`, `gua`, `jalan` di atas `structgen.Piece`).

#### 38.C.2 Kampung Fana dan Desa Klan (38DS)

##### 38.C.2.1 Ukuran, gaya dan struktur

| Id struktur (`kultivator:kampung/<id>_<gaya>`) | Ukuran | Jigsaw | Potongan | Penduduk (villager / NPC data) |
|---|---|---|---|---|
| `dusun` (dusun kecil) | ±70 × 70 | `size 3`, `max_distance 40`, `beard_thin` | 5-9 | 2 / 6 |
| `desa` (desa) | ±130 × 130 | `size 5`, `max_distance 72`, `beard_thin` | 12-22 | 4 / 18 |
| `desa_besar` (kota kecamatan, pasar mingguan) | ±200 × 200 | `size 6`, `max_distance 100`, `beard_thin` | 22-36 | 6 / 36 |
| `klan` (Desa Klan kultivator, bertembok) | ±150 × 150 | `size 6`, `max_distance 80`, `beard_box` | 18-28 | 0 / 30 |

Gaya (`<gaya>`) menurut bioma, satu struktur per ukuran per gaya di set yang sama (bobot 1, gagal bioma jatuh ke gaya
berikutnya seperti kota 34.12):

| Gaya | Bioma (tag `#kultivator:has_structure/kampung/<gaya>`) | Ciri |
|---|---|---|
| `sedang` | plains, sunflower_plains, meadow, forest, birch_forest, flower_forest | rumah kayu `oak` + plester, atap jerami (`hay_block` + `oak_slab`), sawah teras kecil |
| `salju` | taiga, snowy_plains, snowy_taiga, grove | rumah `spruce` berlantai panggung, tungku `smoker` di tengah rumah, gudang ikan asin |
| `sabana` | savanna, savanna_plateau, windswept_savanna | rumah tanah liat `terracotta`, atap `acacia`, kandang ternak besar |
| `sakura` | cherry_grove | rumah `cherry`, kebun teh (`azalea` berbaris), kolam koi |
| `gurun` | desert | rumah oasis `sandstone`/`smooth_sandstone`, kolam oasis berpagar, pohon "palma" (`jungle_log` + `jungle_leaves` tanpa membusuk: `persistent=true`) |
| `rawa` | swamp, mangrove_swamp | rumah panggung di atas air (`mangrove_planks`, tiang `mangrove_log`), titian papan, perahu tertambat (`oak_boat` entitas templat) |
| `terasering` | windswept_hills, windswept_forest, stony_shore (tanah) | sawah bertingkat (petak air berbatas `mud_bricks`), rumah batu `cobblestone` |

`klan` hanya punya gaya `sedang`, `salju`, `sakura`, `gunung` (`gunung` = meadow, grove, windswept_hills).

##### 38.C.2.2 Potongan (pool `kultivator:kampung/<gaya>/<pool>`)

- `pusat` (start): **lapangan desa** 17 × 17 dengan **lonceng** (`minecraft:bell` di tiang kayu; POI `MEETING` → Desa
  Bawahan bisa mengikrarkan), sumur (`cobblestone_wall` + air + `chain`), **kuil Dewa Tanah** 5 × 5 (altar `polished_andesite`,
  patung kecil `stone_bricks` + `candle`), papan pengumuman (billboard klien). Jangkar `kampung/situs` (data `situs` =
  `kampung/<ukuran>` atau `kampung/klan`), `kampung/lonceng` (protect 48), `kampung/tetua`.
- `jalan`: jalan tanah 3 lebar (`dirt_path`, tepi `coarse_dirt`), simpang, jembatan kayu kecil di atas parit.
- `rumah_petani_kecil|sedang|besar` (5 × 6 × 5 sampai 9 × 9 × 11): ranjang (`*_bed`, titik `kasur`), meja makan (titik
  `meja_makan`), tungku dapur (titik `kerja:kuali`), lemari, tempat kerja vanilla untuk villager (`composter`, `smoker`,
  `barrel`, `loom`, `fletching_table` → profesi villager vanilla); halaman dengan jemuran (`white_carpet` di `oak_fence`).
- `ladang` 11 × 11 dan `sawah` 15 × 15 (`farmland` + `wheat|carrots|potatoes|beetroots` umur acak lewat processor
  `rule`, air irigasi di tengah, orang-orangan sawah `hay_block` + `carved_pumpkin`), titik `kerja:cangkul` ×4.
- `lumbung` 9 × 8 × 9, `kandang` (ayam/babi/sapi vanilla sebagai entitas templat, 2-4), `kolam_ikan` (titik `kerja:pancing`
  ×2), `kincir_air` (roda statis dari `stripped_oak_log` + pita air; putaran = model klien 38.O), `pasar_desa` (4 lapak,
  titik `lapak` ×4; hanya `desa` dan `desa_besar`), `rumah_tabib`, `bengkel_pandai` (`anvil`, `smithing_table`, titik
  `kerja:palu`), `balai_desa` (hanya `desa_besar`: aula pertemuan, titik `kursi` ×12), `makam_desa` (nisan
  `cobblestone_wall` + `candle`), `menara_pengawas` 5 × 14 × 5, `sekolah_desa` (hanya `desa_besar`).
- `klan` (Desa Klan, start `gerbang_klan` 15 × 14 × 11): `tembok_klan` (batu bata 6 tinggi), **Aula Leluhur Klan** 19 × 16 × 21
  (papan arwah = gambar klien, titik `dupa`), **Lapangan Latihan Klan** 21 × 21 (patung kayu, rak senjata, titik
  `latihan` ×8), rumah patriark, rumah tetua ×2, paviliun alkimia klan, perpustakaan kecil, rumah tamu, rumah pelayan,
  ladang di luar tembok. Jangkar `kampung/patriark`, `kampung/tuan_muda`, `kampung/tetua_klan` ×2, `kampung/lonceng`.

##### 38.C.2.3 Penduduk dan kehidupan

- **Villager vanilla** (dari templat, seperti desa vanilla) secukupnya agar `VillageScan.villagersOf` menghitung dan
  gosip vanilla bekerja; mereka tidak diubah.
- **NPC 38** (data → tubuh, 38.N): KEPALA_DESA, PETANI ×n, NELAYAN, PANDAI_DESA, TABIB_DESA, PEDAGANG_KELILING,
  ANAK ×n, NENEK/KAKEK (TETUA_DESA), PENJAGA_DESA (hanya `desa_besar`); di klan: PATRIARK_KLAN (tier 4, ranah
  Foundation-Nascent Soul menurut hash), TUAN_MUDA (tier 2), TETUA_KLAN ×2, MURID_KLAN, PELAYAN. Tubuh maks
  `kampung.tubuhMaks` 10 per kampung (6 untuk dusun), per pemain 12 dalam 64 blok (`DuniaBudget` jenis `npc_kampung`).
- **Interaksi:** Kepala Desa (dialog 38.S: kabar desa, permintaan tolong, pengikraran lewat Plakat Pelindung Sekte yang
  sudah ada di sistem Desa), pasar desa (makanan, herba muda, alat murah; harga 38.M.2.2), Tabib Desa (obati
  Penyimpangan Qi ringan 6 × (band + 1) NB, hanya band ≤ 3), Festival Panen (`d % 12 == 0`, siang: tari panen, meja makan
  bersama, bila desa terikrar kesetiaan +2 lewat API publik Desa (verifikasi), adegan klien), **anak berbakat** (kampung
  terikrar: 1 calon per 10 hari lewat `Halls.addCandidates`, 38.M.4.3).
- **Desa Klan:** klan bersikap menurut Nama Baik klan (data `kampung.klan.<site>.sikap`): Tuan Muda yang sombong menantang
  pemain berranah rendah (trope novel; Duel Pedang, aturan ramah), Patriark mengundang pemain kuat; pengikraran klan butuh
  mengalahkan Patriark dalam duel atau sikap ≥ 50; upeti klan menambah bahan kultivasi (herba berumur, batu roh Menengah)
  lewat kait `Tribute.addBonusProvider` (38DS menyuntingnya, 38.E.2). Sumber
  (`kultivator.kampung.klan.inspired`): "Terinspirasi dari Keluarga Xiao di Kota Wu Tan (Battle Through the Heavens) dan
  tuan muda sombong klan bangsawan yang menjadi kiasan baku novel xianxia; rancangan game."
- Sumber kampung (`kultivator.kampung.desa.inspired`): "Terinspirasi dari desa pegunungan asal Han Li di awal A Record of
  a Mortal's Journey dan kuil kecil Dewa Tanah (Tudi Gong) yang ada di hampir setiap desa Tiongkok; rancangan game."

##### 38.C.2.4 Set dan frekuensi

| Set (`kampung/<id>`) | Placement | spacing / separation | salt | `min_chunks` | Exclusion |
|---|---|---|---|---|---|
| `dusun` | `cincin_tempat` | 24 / 9 | 380101 | 6 | `minecraft:villages` 4 |
| `desa` | `cincin_tempat` | 36 / 12 | 380102 | 8 | `minecraft:villages` 6 |
| `desa_besar` | `cincin_tempat` | 64 / 24 | 380103 | 16 | `kultivator:kota/kota_kultivator` 6 |
| `klan` | `cincin_tempat` | 56 / 20 | 380104 | 24 | `minecraft:villages` 6 |

Pack bawaan `kultivator:dunia_kampung`; config `kampung.worldgen`.

#### 38.C.3 Kompleks Sekte NPC (38DS)

##### 38.C.3.1 Letak dan rencana

- Satu kompleks untuk **setiap entri `GerbangPlan`** (satu sumber sekte, 38.0 butir 15), di **belakang** gerbangnya.
  Rotasi gerbang (jigsaw `size 1`, `start_height` konstan) dapat dihitung: `RotasiGerbang.of(seed, chunkX, chunkZ)` =
  `Rotation.values()[r.nextInt(4)]` dengan `WorldgenRandom r = new WorldgenRandom(new LegacyRandomSource(0L));
  r.setLargeFeatureSeed(seed, chunkX, chunkZ)` (urutan acak `JigsawPlacement.addPieces` :70, verifikasi dengan
  `uji kompleks rotasi` yang membandingkan dengan potongan `StructureStart` gerbang yang sudah ada). Arah "belakang" =
  +z lokal templat gerbang yang diputar.
- Rencana `RencanaKompleks` (38W menyediakan kerangka `RencanaRegistry`, 38DS mengisi): pusat kompleks = titik tengah
  gerbang + belakang × (kedalaman gerbang/2 + 8 + setengah kompleks); pita kecil 1 distrik (3 × 3 lot = 144), sedang
  2 distrik (`halaman` 3 × 3 + `puncak` 3 × 2), besar 3 distrik (`halaman`, `dalam`, `puncak`, masing-masing 3 × 3).
  Struktur `kultivator:kompleks_sekte/<aliran>_<distrik>` bertipe `kultivator:tata_letak` (`rencana kompleks_sekte`),
  set `kompleks_sekte/<distrik>` salt 380201-380203, pack `kultivator:dunia_kompleks` (didaftarkan hanya bila
  `diplomasi.strukturAktif` dan `kompleksSekte.worldgen`).
- **Teras gunung:** baris lot naik `dy` = 0 (halaman), +10 (dalam), +18 (puncak) di atas lantai gerbang, disambung lot
  `tangga_gunung` (tangga batu 7 lebar dengan gapura kecil tiap 12 anak tangga). Putusan tanah kompleks: selisih tinggi
  ≤ 30 di 5 × 5 sampel, tanpa air di lot puncak; tidak layak → kompleks tidak lahir (gerbang tetap seperti sekarang).
- Bioma gerbang diperiksa ulang dengan aturan `isValidBiome` gerbang (`#kultivator:has_structure/sekte_gerbang` di titik
  tengah chunk gerbang pada tinggi permukaan) agar kompleks tidak lahir tanpa gerbang.

##### 38.C.3.2 Lot per aliran (pool `kultivator:kompleks_sekte/<aliran>/<lot>`)

| Lot | Isi | Aliran |
|---|---|---|
| `aula_utama` | Aula Utama 32 × 20 × 26, papan nama sekte (billboard klien bernama `DiplomasiService.name`), lambang di atas pintu (decal klien `Lambang`) | semua |
| `lapangan_latihan` | 36 × 36 batu, patung latihan kayu ×6 (entitas templat `armor_stand` berlengan), rak senjata, titik `latihan` ×12, jangkar `kompleks/arena` | semua |
| `paviliun_kitab` | rak kitab dua lantai, titik `meja_tulis` ×6 | Lurus, Netral |
| `paviliun_alkimia` | 4 kuali di api unggun, gudang herba | Lurus, Netral |
| `asrama_luar` / `asrama_dalam` | kasur ×16 / ×8, ruang makan, titik `kasur`, `meja_makan` | semua |
| `kolam_roh` | kolam berpagar giok (`prismarine`), teratai (`lily_pad`), titik `meditasi` ×8 | Lurus, Netral |
| `gua_meditasi` | ceruk tebing 5 × 4 × 5 berderet, titik `meditasi` ×6 | semua |
| `kebun_obat` | petak herba (tanaman vanilla `sweet_berry_bush`, `azalea`, `fern`) | Lurus, Netral |
| `aula_puncak` | aula ketua di puncak, takhta, jangkar `kompleks/puncak` (tempat Ketua bila D memakai `TempatApi.spot`) | semua |
| `menara_lonceng` | menara 9 × 24 × 9, `bell` | Lurus |
| `makam_leluhur` | makam leluhur sekte | Lurus, Netral |
| `kolam_darah` | kolam `red_concrete` + `crimson_*`, rantai, sangkar tawanan (`iron_bars`), titik `jaga` | Iblis |
| `altar_iblis` | altar `polished_blackstone` dengan `soul_lantern` dan rantai (tanpa blok api di templat), lingkar rune klien | Iblis |
| `pasar_sekte` | 4 kios dagang sekte | Netral |
| `gudang`, `dapur`, `rumah_pelayan` | pelengkap | semua |

Gaya aliran lewat processor: Lurus (putih-giok-biru: `quartz`, `light_blue_terracotta`), Netral (kayu-emas: `spruce`,
`yellow_terracotta`), Iblis (hitam-merah: `blackstone`, `red_nether_bricks`, panji hitam klien).

##### 38.C.3.3 Penghuni dan interaksi

- Populasi data per pita: kecil 40, sedang 80, besar 120 (murid luar, murid dalam, tetua, pelayan); tubuh maks
  `kompleksSekte.tubuhMaks` 12 per kompleks (jenis `npc_kompleks`); peran MURID_SEKTE_LUAR, MURID_SEKTE_DALAM,
  TETUA_SEKTE, PELAYAN_SEKTE, PENJAGA_GERBANG_SEKTE (ditambahkan ke anggota gerbang D ≤ 6 yang tetap). Identitas (nama
  sekte, lambang, warna jubah dari `Lambang`) dari `DiplomasiService`; tubuh berjubah lambang sekte (38.O).
- **Sikap** (`DiplomasiService.stance(owner, sekte)`): SEKUTU/BERSAHABAT → disambut, boleh masuk aula dalam, tanding
  persahabatan di lapangan (Duel Pedang aturan ramah, standing +1 maks 1/hari, `KotaDuel` pola), pasar sekte −10 %;
  NETRAL → hanya halaman; BERMUSUHAN → penjaga memperingatkan di gerbang, masuk = standing −2; PERANG → penjaga dan murid
  bermusuhan (`markRealmFoe`), Ketua menunggu di Aula Puncak bila D memakai `TempatApi.spot(sekteId, "puncak")`.
- **Pasar sekte** (lot `pasar_sekte` atau aula untuk sekte tanpa lot): barang khas aliran (Lurus: pil penyembuh, pedang
  besi roh; Netral: jimat, peta; Iblis: racun, pil darah — hanya untuk sikap ≥ NETRAL) dengan harga 38.M.2.2.
- **Sekte langit (37):** untuk entri yang punya Istana Awan (`LangitNpcPlan`), lot `aula_puncak` memakai varian
  `aula_puncak_dermaga` dengan anjungan dan jangkar `kompleks/dermaga_awan` yang bisa dibaca 37 lewat
  `TempatApi.spot(sekteId, "dermaga_awan")`; 38 tidak menyunting berkas 37.
- Situs diterbitkan sebagai `sekte/kompleks` (data `sekte` = id) sekali saat jangkar `kompleks/situs` dimuat.
- Sumber (`kultivator.kompleks_sekte.inspired`): "Terinspirasi dari Sekte Tujuh Misteri di A Record of a Mortal's
  Journey dan Sekte Penyokong (Reliance Sect) di I Shall Seal the Heavens: halaman murid luar, puncak murid dalam, aula
  di puncak gunung; rancangan game."

#### 38.C.4 Gua (38GU)

Semua gua: jigsaw, `step underground_structures`, `encapsulate` atau `beard_box`, `spawn_overrides` monster kosong di
ruang berpenghuni saja; **ranah disarankan** tertulis di kartu masuk (38.0 butir 13); tidak ada blok runtime: pintu
rahasia, segel dan teka-teki adalah entitas (`reruntuhan/ColliderEntity`, `KotaPintuEntity` pola).

| Id (`kultivator:gua/<id>`) | Bioma | Ukuran | Isi dan tokoh | Ranah disarankan | `min_chunks` |
|---|---|---|---|---|---|
| `gua_pertapa` | windswept_hills, meadow, grove, forest, dark_forest, taiga, jungle (lereng) | `size 3`, 4-6 potongan, ±30 × 20 × 30 | serambi berkebun, ruang meditasi dengan mata air, petak herba di bawah lubang cahaya, bilik tidur; **PERTAPA** (ranah tetap dari hash: Inti Emas-Penyatuan Dao) | — (damai) | 10 |
| `makam_warisan` | semua bioma darat; pintu masuk = stela di lahan terbuka, makam di y −20 relatif | `size 5`, 9-12 potongan | lorong mural, 3 ruang ujian (Hati, Kekuatan, Pemahaman), aula makam dengan **SISA_JIWA** | tier 1: Fondasi, 2: Jiwa Baru Lahir, 3: Penyatuan Dao | 16 / 32 / 64 per tier |
| `sarang_binatang` | forest, dark_forest, jungle, bamboo_jungle, taiga, old_growth_*, badlands | `size 3`, gua atau pohon berongga raksasa (batang `dark_oak_log` 9 × 9) | sarang (`hay_block`, tulang `bone_block`), bos binatang elit, 2-4 binatang biasa | menurut tingkat elit (T1-T3) | 8 |
| `sarang_iblis` | semua darat; pintu tersembunyi di tebing | `size 5`, 8-14 potongan | aula kolam darah, sel tawanan, ruang racik pil terlarang, kamar pemimpin; band kultivator nakal (`RogueCultivators.spawnBand`), **TETUA_IBLIS** (bos) | Fondasi (cincin 1) → Jiwa Baru Lahir (cincin 3) | 20 |
| `gua_urat_roh` | semua Overworld, y −20..30 | `size 2`, gua geode 40 × 24 × 40 | dinding `calcite`/`amethyst_block`/`smooth_basalt`, `amethyst_cluster`, `glow_lichen`, urat bijih vanilla (`diamond_ore`, `emerald_ore`, `lapis_ore`), mata air roh; **PENJAGA_KRISTAL** ×2 (makhluk 38MD) | Inti Emas | 12 |
| `cermin_hati` | dark_forest, pale_garden, swamp, mangrove_swamp | `size 2`, ±18 × 12 × 18 | gua kecil dengan kolam diam (air dangkal di atas `black_concrete`), cermin batu `polished_blackstone` | Jiwa Baru Lahir | 24 |

- **Gua Pertapa:** pertapa memberi tiga ujian lintas hari (38.S.4: bawa herba ≥ 100 tahun, meditasi tanpa terputus 5 menit di
  ruangnya, jawab satu koan Dao) → mengajar satu teknik Dao-nya (kitab dari `TechniqueBooks`, verifikasi API pemberian)
  atau +5 pemahaman; setelah itu pertapa "pergi mengembara" (cerita) dan gua menjadi **Gua Kediaman** yang sah (kursi
  `CaveAbode` cocok bila pemain memasang formasi Qi; 38 tidak mengubah `CaveAbode`). Situs meditasi ekstra "Gua
  Pertapa" ×1.3.
- **Makam Warisan:** gerbang ranah pada penghalang entitas; uji Hati = pilihan ilusi 3 tahap (layar dialog, 38.S.4); uji
  Kekuatan = 2-4 **Boneka Penjaga** (entitas 38MD, `markRealmFoe`, berskala tier makam); uji Pemahaman = susun 5 lempeng
  glif (entitas teka-teki pola `TekaPartEntity`). Hadiah sekali per pemain per makam: giok teknik Dao makam, pusaka tier
  makam, dan bila pemain memegang `giok_warisan` saat uji Hati: mutu warisan +10 poin persen (kait `InheritanceTrials`,
  38GU). `giok_warisan` menunjuk makam terdekat yang dikenal (kompas `lodestone_tracker` pola `VillageLetterItem`).
  Murid diveto (38.0 butir 18).
- **Sarang Binatang:** bos = binatang elit (`elite/*`, verifikasi API spawn) muncul saat pemain pertama ≤ 24 blok; binatang
  roh pemain bertarung di sini: XP binatang ×1.25 (kait `SpiritBeasts.addXpFactorProvider`, 38GU, 38.E.2); hadiah inti
  binatang, bahan sesaji evolusi (tabel `harta`), 10 % anak binatang tersesat (serigala/rubah liar yang bisa dijinakkan
  biasa).
- **Sarang Kultivator Iblis:** pintu tebing = `KotaPintuEntity`-pola terbuka saat pemain menyentuh dengan ranah ≥ disarankan
  − 1; tawanan (TAWANAN ×2-4, fana) dibebaskan dengan membuka sangkar (entitas) lalu **dikawal** ke kampung/kota terdekat
  (Nama Baik +3 kota tujuan, kesetiaan desa +5 bila terikrar); bos TETUA_IBLIS (tier 4) di kamar pemimpin; misi gilda
  `sarang` menunjuk ke sini. Sumber (`kultivator.gua.sarang_iblis.inspired`): "Terinspirasi dari markas para kultivator
  jalan iblis yang menculik orang fana untuk diracik, kiasan umum novel xianxia; rancangan game."
- **Gua Urat Roh:** situs meditasi ekstra ×1.6 (dalam CAP); **kelahiran harta** memilih gua urat roh yang dikenal dalam
  jangkauan 300-900 dengan peluang 35 % (kait `TreasureBirths`, 38GU); Penjaga Kristal bangun saat pertama dimasuki per
  hari dunia.
- **Gua Cermin Hati:** uji iblis hati yang dimulai di kolam (pemain duduk di titik `meditasi`, tombol "Hadapi Iblis Hati" di
  layar, `HeartDemons.startTrial` lewat kait publik): kenaikan tekanan saat gagal ×0.5, lulus −20 tekanan tambahan;
  `Sibuk` `tempat.cermin`. Sumber: "Terinspirasi dari Cermin Hati dan ujian iblis batin di I Shall Seal the Heavens;
  rancangan game."
- **Tautan modul R:** Gua Kecil/Agung/Kuno, Penjara dan Menara mendapat cabang jalan setapak (38.C.6), target misi gilda
  `gua` dan baris rumor (38.S.5); 38 tidak menyunting berkas R.

#### 38.C.5 Situs alam (38GU)

| Id (`kultivator:situs/<id>`) | Bioma | Isi | Fungsi |
|---|---|---|---|
| `puncak_petir` | jagged_peaks, frozen_peaks, stony_peaks (dekat puncak, `project_start_to_heightmap` + `beard_box`) | teras altar 23 × 23, sembilan tiang `waxed_cut_copper` beratap, **Batu Catatan** (stela + billboard nama), langkan penonton | kesengsaraan di sini: faktor kerusakan 0.90 (kait `Tribulation`, batas ≥ 0.85), nama tercatat (ranah, hari), 2-4 penonton (pengembara) datang menonton bila ada pemain, situs meditasi ×1.25 |
| `panggung_bintang` | meadow, windswept_hills, savanna_plateau, snowy_slopes | panggung bundar 21 blok, armilari (cincin statis + model klien berputar 38.O) | Bencana Bintang yang dimulai di sini: +1 gulir hadiah (kait `StarCalamity`); menonton peristiwa langit: bonus seperti Menara Pengamat |
| `mata_air_roh` | river tepi, forest, cherry_grove | kolam mata air berbatu giok, kuil kecil | situs meditasi ×1.3, minum (klik kolam) memulihkan 20 % Qi sekali per hari |

`min_chunks`: `puncak_petir` 12, `panggung_bintang` 24, `mata_air_roh` 8.

#### 38.C.6 Jalan, jembatan, pos, kuil jalan dan tonggak (38W)

##### 38.C.6.1 Rencana jalan (`dunia/tempat/jalan/RencanaJalan`)

- **Simpul** (deterministik dari seed): gerbang ibu kota (dari `RencanaKotaBesar`, titik di depan gerbang), kota
  (`kota/kota_kultivator`: chunk potensial dari placement `jauh_dari_pusat`), `kampung/desa` dan `desa_besar` (chunk
  potensial `cincin_tempat`), `klan`, gerbang sekte (`GerbangPlan`), mulut gua R/38 tertentu (`gua_kecil`, `gua_agung`,
  `gua_pertapa`, `makam_warisan` tier 1 hanya sebagai cabang setapak), pos (dibangkitkan di sepanjang sisi panjang).
- **Sel jalan** 2048 × 2048 blok. Setiap sel menghitung graf dari simpul di sel itu dan delapan tetangganya: **pohon
  rentang minimum** (Prim, jarak Euklides) + sisi tambahan dari setiap gerbang ibu kota ke setiap kota ≤ 1500 blok; sisi
  dimiliki sel yang memuat titik tengahnya (tidak ada sisi ganda). Sisi > 2600 blok dibuang. Hasil per sel di cache LRU
  64.
- **Keabsahan simpul** saat worldgen: simpul hanya dipakai bila struktur di sana memang lahir: bioma di titik chunk awal
  lulus tag strukturnya (ibu kota: putusan 38.B.2.4; kompleks/gerbang: 38.C.3.1). Satu ujung tidak sah → seluruh sisi
  tidak lahir (setiap chunk menghitung hal yang sama, jadi tidak ada jalan buntu setengah).
- **Bentuk:** garis patah dari dua tingkat pergeseran titik tengah (±8 % panjang, hash sisi), lalu dihaluskan
  (Chaikin 2 kali). Kelas: **Jalan Kekaisaran** (dari gerbang ibu kota, 5 lebar, `stone_bricks` + `andesite` tepi
  `polished_andesite`), **Jalan Desa** (antar kota/kampung/gerbang sekte, 3 lebar, `dirt_path` + `gravel` tepi
  `coarse_dirt`), **Jalan Setapak** (cabang ke gua, 2 lebar, `dirt_path` + `rooted_dirt`).
- Jalan berhenti 12 blok di luar kotak tempat tujuan; khusus kota lama, ujung dibelokkan ke depan gerbang bila rotasi
  gerbang kota dapat dihitung (start kota = jigsaw `size 6`, `start_height` konstan → rotasi seperti 38.C.3.1;
  verifikasi `uji jalan cek`), selain itu ke tepi tembok terdekat.

##### 38.C.6.2 Struktur jalan

- Set `jalan/jalan` placement `kultivator:jalan` (`JalanPlacement`: `isPlacementChunk` = ada sisi yang lewat chunk ini
  dan chunk tidak di dalam zona tempat lain kecuali ujung masuk), salt 380301, pack `kultivator:dunia_jalan`.
- Struktur `kultivator:jalan/jalan` bertipe `kultivator:jalan` (`JalanStructure`): untuk potongan sisi di dalam chunk-nya
  menaruh **ubin** tiap 3 blok sepanjang garis: `jalan/ubin_<kelas>_<lebar>` (templat `lebar × 1 × lebar`, elemen
  `single_pool_element` dengan `projection terrain_matching` → `GravityProcessor` meletakkan setiap kolom di permukaan,
  verifikasi `SinglePoolElement.getSettings`); ubin tidak diletakkan di kolom air atau di atas daun (kolom dengan
  `WORLD_SURFACE_WG > OCEAN_FLOOR_WG` atau tanah < 2 blok di bawah `MOTION_BLOCKING`).
- **Air:** jalur air ≤ 28 blok → **jembatan** kaku `jalan/jembatan_<kelas>_<panjang>` (8, 12, 16, 20, 24, 28; lebar
  kelas + 2, pagar, lentera tiap 8, tiang batu ke dasar ≤ 12) di `seaLevel + 2`; Jalan Kekaisaran mendapat jembatan
  lengkung batu (`stone_brick_stairs`). Lebih panjang → **pos penyeberangan** `pos/penyeberangan` di dua tepi (dermaga
  kecil + rakit tertambat = entitas `RakitEntity` 38MD; layanan seberang 1 NB, adegan dayung 4 s lalu teleport ke dermaga
  seberang).
- **Lereng:** naik > 1 blok per blok selama ≥ 4 blok → potongan `jalan/tangga_<kelas>` kaku; > 12 blok tinggi → jalan
  berbelok (pergeseran titik tengah dicoba 3 kali), selain itu dibiarkan setapak.
- Jalan **tidak** menimpa struktur lain (berhenti 4 blok sebelum kotak start struktur lain yang dikenal di chunk itu,
  `StructureManager` referensi chunk, verifikasi) dan tidak memotong pohon (ubin hanya di permukaan tanah; pohon yang
  tumbuh kemudian di tepi jalan biasa, karena `dirt_path` bukan `#dirt` bagi saplings, verifikasi `TreeFeature`).

##### 38.C.6.3 Bangunan tepi jalan (rencana `jalan`, placement `rencana_tempat`)

| Id (`kultivator:pos/<id>`) | Letak (deterministik sepanjang sisi) | Ukuran | Isi | Fungsi |
|---|---|---|---|---|
| `tonggak` | tiap 500 blok jarak tempuh, dan di setiap simpang | 3 × 4 × 3 | stela batu dengan ukiran (`chiseled_stone_bricks`), billboard klien "← <tempat> 1.2 li / 600 b →" | menyentuh = situs `pos/tonggak` ditemukan + sisi jalan dan kedua ujungnya tergambar di peta (`DuniaSites.discover`) |
| `kuil_jalan` | tiap ±260 blok (hash ±60) | 5 × 6 × 5 | kuil Dewa Tanah kecil, dupa | dupa 1 NB → **Restu Jalan** (efek `SPEED` I 3 menit, maks 1 per 10 menit); pengembara kadang berdoa di sini |
| `singgah` | tiap ±900 blok pada sisi ≥ 1200 | jigsaw `size 3`, ±40 × 40 | penginapan kecil 2 kamar, warung, kandang kuda, papan kabar, kursi pengembara (`kota/kursi_pengembara`) | istirahat (38.M), makanan, kabar, papan misi salinan Gilda |
| `singgah_besar` | di simpang berderajat ≥ 3 yang ≥ 600 blok dari kota | jigsaw `size 4`, ±64 × 64 | seperti `singgah` + **cakram teleport** (tujuan stasiun), pandai kuda, gudang kafilah | teleport tiba/berangkat, pos kafilah |
| `dagang` | simpang berderajat ≥ 3 yang ≥ 400 blok dari kota, bergantian dengan `singgah_besar` | jigsaw `size 3` | karavanserai: halaman dalam, gudang, kios pedagang wilayah | barang khas wilayah (bioma), beli rampasan, misi kawal |
| `kemah_begal` | 15 % sisi di cincin ≥ 1000 blok, 24-40 blok dari jalan | jigsaw `size 2` | tenda, api unggun **mati** (`campfire[lit=false]`), palisade | band begal (`RogueCultivators.spawnBand`) saat pemain ≤ 48 blok (tekanan harian M2, `Sibuk`); misi gilda `begal` |
| `penyeberangan` | lihat 38.C.6.2 | 9 × 6 × 12 | dermaga, rakit | seberang 1 NB |

- Kafilah (38EK): data yang berjalan sepanjang sisi antar-kota (kecepatan 0.8 blok/s data); tubuh (gerobak `GerobakEntity`
  38MD + kuda vanilla tanpa simpan + 2 pengawal + pedagang) hanya bila pemain ≤ 64 blok dari posisi datanya; menjadi
  target misi kawal dan begal.
- Sumber (`kultivator.jalan.inspired`): "Terinspirasi dari jaringan pos (yizhan) dan jalan raya kekaisaran Tiongkok kuno,
  jalan lurus Qin, dan tonggak li di tepi jalan; rancangan game."

#### 38.C.7 Tempat lama mendapat kehidupan (tanpa worldgen baru)

- Kota Kultivator, Kota Abadi, Pulau Pasar (D) dan desa arwah Dunia Bawah tidak dibangkitkan ulang. 38DS menambah titik
  NBT (`Template.titik`) ke templat kota dan Kota Abadi lewat `tools/gen_dunia_kota.py` (38DS satu-satunya penyunting di
  38; **tanpa** mengubah satu blok pun, diuji dengan perbandingan byte palet/blok sebelum-sesudah di `cek_tempat.py`) →
  kota yang sudah lahir di dunia lama ikut punya kasur, kursi, titik kerja dan jalur, karena titik dibaca dari berkas NBT
  saat runtime dan diterapkan ke potongan yang ada.
- Vanilla village tidak disentuh (penduduk vanilla), tetapi Desa Bawahan tetap berlaku.

#### 38.C.8 Dimensi lain

- Alam Abadi: Kota Abadi (34) mendapat titik (38.C.7); ibu kota tidak lahir di Alam Abadi (bioma Alam Abadi tidak ada di
  `#kultivator:kota_besar/tanah`).
- Dunia Bawah, Laut Tanpa Batas, Menara, Alam Rahasia: tidak ada tempat 38 (desain D/R tetap).

#### 38.C.9 Kepadatan per bioma dan jarak dari spawn

Cincin jarak dari pusat dunia (blok): **R0** 0-96 (6 chunk, kosong, W4), **R1** 96-1000 (tanah awal), **R2** 1000-3000,
**R3** 3000-8000, **R4** > 8000. Angka = rata-rata **per km²** (1000 × 1000 blok) di bioma yang cocok; "—" = tidak lahir;
frekuensi per cincin diatur `cincin_tempat` (38.D.1); angka termasuk peluang gagal bioma.

| Tempat | Bioma cocok | R1 | R2 | R3 | R4 | Catatan |
|---|---|---|---|---|---|---|
| Kota Besar | dataran, hutan, salju datar, sabana, sakura, gurun | — | 1 dijamin di 1800-3200 | 0.04 | 0.04 | 1 per sel 4096² × 0.6 × layak |
| Kota Kultivator (34) | sama | 0.25 | 0.25 | 0.25 | 0.25 | tidak diubah |
| Kompleks Sekte | ikut gerbang | — | 8 per dunia (1500-2900) | — | — | 1 per gerbang |
| `kampung/dusun` | 7 gaya | 1.2 | 1.0 | 0.8 | 0.6 | frekuensi menurun ke luar |
| `kampung/desa` | 7 gaya | 0.5 | 0.6 | 0.5 | 0.4 | |
| `kampung/desa_besar` | 7 gaya | 0.10 | 0.15 | 0.15 | 0.12 | jauh dari kota (6 chunk) |
| `kampung/klan` | 4 gaya | — | 0.15 | 0.2 | 0.2 | `min_chunks` 24 |
| `gua/gua_pertapa` | bukit, hutan, taiga, rimba | 0.3 | 0.6 | 0.6 | 0.6 | |
| `gua/makam_warisan` | darat | 0.05 (tier 1) | 0.15 | 0.25 | 0.3 | tier naik dengan cincin |
| `gua/sarang_binatang` | hutan, rimba, taiga, badlands | 0.8 | 1.2 | 1.4 | 1.6 | |
| `gua/sarang_iblis` | darat | — | 0.2 | 0.45 | 0.6 | `min_chunks` 20 |
| `gua/gua_urat_roh` | semua (bawah tanah) | 0.4 | 0.8 | 1.0 | 1.0 | |
| `gua/cermin_hati` | hutan gelap, rawa, taman pucat | — | 0.1 | 0.15 | 0.2 | |
| `situs/puncak_petir` | puncak | 0.1 | 0.25 | 0.25 | 0.25 | |
| `situs/panggung_bintang` | padang tinggi | — | 0.05 | 0.08 | 0.1 | |
| `situs/mata_air_roh` | tepi sungai, hutan, sakura | 0.3 | 0.3 | 0.3 | 0.3 | |
| Jalan | — | km jalan per km²: ±1.6 | ±2.0 (dekat ibu kota ±3) | ±1.4 | ±1.0 | ikut simpul |
| Tonggak / kuil jalan / singgah | sepanjang jalan | 2 / 4 / 1.1 per km jalan | sama | sama | sama | |
| `pos/dagang` + `singgah_besar` | simpang | 0.1 | 0.15 | 0.1 | 0.08 | |
| `pos/kemah_begal` | sepanjang jalan | — | 0.15 | 0.3 | 0.3 | |

**Aturan "penuh tapi tidak sesak":** (1) tidak ada dua tempat berpenghuni 38 yang start-nya < 8 chunk (exclusion dan
`cincin_tempat` dengan `jarak_tempat` = 8 memeriksa set berpenghuni lain lewat `hasStructureChunkInRange`,
`ChunkGeneratorStructureState:202`); (2) di dalam zona ibu kota dan kompleks + 3 chunk, start struktur permukaan lain
ditolak (38.D.4); (3) target kepadatan total tempat permukaan 38 **≤ 6 per km²** di R1-R2 (dihitung oleh
`uji tempat kepadatan`, 38.D.7, pada seed contoh); (4) bawah tanah tidak dihitung dalam batas permukaan.

#### 38.C.10 Anggaran world generation

| Tempat | Potongan per start | Jangkar entitas | Titik NBT | NBT (gzip) total |
|---|---|---|---|---|
| Kota Besar (11 start) | 9-14 lot + tembok per distrik (≤ 160 total) | ≤ 40 per distrik (±160 total) | ≤ 96 per lot | ≤ 3 MB |
| Kompleks sekte | 9 per distrik (≤ 27) | ≤ 24 | ≤ 64 per lot | ≤ 1.2 MB (3 aliran) |
| Kampung/klan | 5-36 | ≤ 12 | ≤ 32 per potongan | ≤ 1.5 MB (7 gaya) |
| Gua/situs | 2-14 | ≤ 16 | ≤ 24 | ≤ 1 MB |
| Jalan | ≤ 12 ubin + ≤ 1 jembatan per chunk | 0 | 0 | ≤ 0.2 MB |
| Pos | 1-6 | ≤ 6 | ≤ 24 | ≤ 0.4 MB |

Biaya worldgen: putusan tanah sekali per kandidat (cache), rencana jalan per sel (cache), placement tambahan O(1) per
chunk per set (18 set baru); target pembuatan chunk baru ≤ +6 % waktu pada laptop uji (diukur `uji tempat ukur`, 38.D.7).

---------------------------------------------------------------------------------------------------------------------

### 38.N Kehidupan NPC dan animasi (modul 38NL; dipakai SEMUA tempat)

Permintaan pengguna: "Pastikan semua NPC punya kehidupan harian dan animasi yang DETAIL." Satu sistem untuk ibu kota, kota
lama, kampung, desa klan, kompleks sekte, gua pertapa dan pos. **Memperluas, bukan menggandakan** apa yang sudah dibangun
modul K: `KultivatorNpc` (entitas), `NpcAi` (gol), `NpcLife` (rutinitas, obrolan), `NpcPoses` (bahasa tubuh klien),
`KotaGear`/`KotaGearLayer` (perlengkapan), `NpcRenderer`. 38NL adalah satu-satunya penyunting file-file itu di 38
(38.E.2), dan semua perluasan bersifat aditif: perilaku lama kota tetap sama bila 38 dimatikan.

#### 38.N.1 Arsitektur

```
server (dunia/tempat/hidup/**)                                   klien (client/dunia/tempat/hidup/**)
 Penduduk (data, deterministik) ──jadwal(jam, hari, cuaca)──▶ Kegiatan (titik NBT + klip + prop)
      │  LOD L0/L1: tubuh KultivatorNpc                         NpcPoses ◀─ DATA_ANIM / DATA_PROP / DATA_LOOK_AT
      │  LOD L2: PendudukSnapshot (payload 2 Hz) ───────────────▶ ImpostorRenderer (boneka ringan)
      │  LOD L3: hanya data
 TitikTemplat (kultivator_titik di NBT) ◀── TempatApi.potongan(level, situs)
```

- **Penduduk** (`dunia/tempat/hidup/Penduduk`): `record Penduduk(int id, NpcRole peran, int look, int suara, int rumah,
  int kasur, int kerja, int kelompok, long benih)`; `rumah/kasur/kerja` = indeks titik dalam daftar titik tempat itu.
  Dibangkitkan **deterministik** dari `(situsId, benih dunia)` oleh `PendudukGen.untuk(TempatInfo)` saat tempat pertama
  dimuat: jumlah dan campuran peran dari tabel per jenis tempat (38.N.2). Hanya **perubahan** yang disimpan (SavedData
  `kultivator:tempat_penduduk`: terluka sampai, meninggal → diganti "pendatang baru" setelah 3 hari dunia, sikap takut,
  hubungan dengan pemain, penanda quest 38.S).
- **Jadwal** (`Jadwal.kegiatan(Penduduk p, long waktu, CuacaInfo c, AcaraInfo a)` → `Kegiatan(int titik, Klip klip,
  int prop, int durasi)`): fungsi murni dari jam dunia, hari, musim, cuaca dan acara (38.N.3). Tidak ada tick per penduduk;
  posisi data dihitung saat dibutuhkan (LOD, saksi kejahatan, quest).
- **Tubuh** (`TubuhPenduduk`): `KultivatorNpc` yang membawa `pendudukId` + `situsId`; tiap 40 tick per tempat yang punya
  pemain dalam 64 blok, `Pemanggil.isi(level, tempat)` memunculkan penduduk yang posisi jadwalnya paling dekat dengan
  pemain (dalam 48 blok) sampai topi (38.N.7), dan melepas tubuh yang > 56 blok atau "masuk rumah" (ke titik `kasur`
  tertutup) saat tidak terlihat. Tubuh tetap `noSave`.
- **Gerak:** L0 (≤ 24 blok) navigasi vanilla `GroundPathNavigation` ke titik tujuan (jalur dibatasi kotak tempat); L1
  (24-48 blok) lurus antar-simpul `jalan` titik NBT dengan `moveControl` tiap 10 tick, tanpa pencarian jalur.
- **Sinkron animasi** (tambahan pada `KultivatorNpc`, aditif): `DATA_ANIM` (int: `klip << 8 | varian`), `DATA_ANIM_T0`
  (int: `gameTime & 0x7FFFFFFF` saat klip mulai), `DATA_PROP` (int: `kanan | kiri << 12 | punggung << 24`, indeks prop
  38.O.4), `DATA_LOOK_AT` (int: id entitas yang ditatap, −1 = arah jalan). Klip berganti paling sering tiap 10 tick.

#### 38.N.2 Peran baru (`NpcRole`, ditambahkan **di akhir** enum; ordinal lama tidak berubah)

| Id | Gelar | Tempat | Tier | Diam | Kelompok jadwal | Perlengkapan (38.O.3) |
|---|---|---|---|---|---|---|
| `PANGERAN_KOTA` | Pangeran Penjaga Kota | istana | 4 | ya | PEJABAT | mahkota guan giok, jubah naga |
| `MENTERI` | Menteri Kota | istana, yamen | 2 | tidak | PEJABAT | topi pejabat hitam bersayap, papan catatan |
| `PENGAWAL_ISTANA` | Pengawal Istana | istana | 3 | tidak | PENJAGA | helm berumbai merah, zirah lamela, tombak |
| `KAPTEN_PENJAGA` | Kapten Penjaga | yamen, gerbang | 4 | tidak | PENJAGA | helm emas, jubah perang, dao |
| `PENJAGA_GERBANG` | Penjaga Gerbang | gerbang | 3 | ya | PENJAGA | helm, tombak, perisai |
| `HAKIM` | Hakim Yamen | yamen | 2 | ya | PEJABAT | topi pejabat, palu kayu |
| `BANKIR` | Juragan Rumah Uang | rumah uang | 1 | ya | PEDAGANG | topi bulat, sempoa |
| `BANDAR_BATU` | Bandar Batu | judi batu | 2 | ya | PEDAGANG | celemek kulit, pahat |
| `PEMOTONG_BATU` | Tukang Potong Batu | judi batu | 1 | ya | PEKERJA | kacamata batu, kain kepala |
| `PEDAGANG_BINATANG` | Pedagang Binatang | pasar binatang | 2 | ya | PEDAGANG | topi bulu, tali kekang |
| `PEDAGANG_SENJATA` | Pedagang Senjata | toko senjata | 2 | ya | PEDAGANG | rompi kulit, pedang di punggung |
| `EMPU` | Empu Pandai Pusaka | gilda tempa | 3 | ya | PEKERJA | celemek kulit, sarung tangan, palu |
| `ALKEMIS` | Alkemis Gilda | gilda alkemis | 2 | ya | CENDEKIA | topi alkemis, labu pil |
| `KETUA_GILDA` | Ketua Gilda | tiap gilda | 4 | ya | PEJABAT | jubah gilda, lencana bintang |
| `PETUGAS_GILDA` | Petugas Gilda | gilda kultivator | 1 | ya | PEDAGANG | topi petugas, kuas |
| `PENGUJI` | Penguji Gilda | gilda kultivator | 3 | ya | KULTIVATOR | pedang, ikat kepala |
| `PENJAGA_FORMASI` | Penjaga Formasi | stasiun | 3 | ya | KULTIVATOR | jubah trigram, piringan formasi |
| `NAKHODA` | Nakhoda Langit | dermaga | 2 | tidak | PEKERJA | topi lebar, teropong |
| `KULI` | Kuli Angkut | dermaga, pasar, luar | 1 | tidak | PEKERJA | kain kepala, pikulan |
| `PEMILIK_PENGINAPAN` | Pemilik Penginapan | penginapan, pos | 1 | ya | PEDAGANG | celemek, kunci kamar |
| `KOKI` | Koki | rumah makan, dapur | 1 | ya | PEKERJA | topi koki kain, sendok sayur |
| `PELAYAN` | Pelayan | penginapan, istana, klan | 1 | tidak | PEKERJA | nampan |
| `PUSTAKAWAN` | Pustakawan | perpustakaan | 2 | ya | CENDEKIA | topi sarjana, kitab |
| `PENCERAMAH_FANA` | Guru Kuliah | perpustakaan | 3 | ya | CENDEKIA | topi sarjana, kipas |
| `RAHIB` | Rahib Kuil | kuil | 2 | tidak | RAHIB | jubah abu, tasbih |
| `TABIB` | Tabib | balai tabib, kampung | 1 | ya | CENDEKIA | kotak obat |
| `TUKANG` | Tukang | bengkel, semua tempat | 1 | tidak | PEKERJA | sabuk alat, palu, ember plester (38.R) |
| `PENJAGA_TEKA` | Penjaga Kios Teka-teki | festival | 1 | ya | PEDAGANG | lentera tangan |
| `JUARA_SEKTE` | Juara Sekte | arena, balai pertemuan | 3-4 | tidak | KULTIVATOR | jubah lambang sekte, pedang |
| `KEPALA_DESA` | Kepala Desa | kampung | 1 | tidak | TETUA | topi caping tua, tongkat |
| `PETANI` | Petani | kampung, luar kota | 1 | tidak | PEKERJA | caping, cangkul |
| `NELAYAN` | Nelayan | kampung, kolam | 1 | tidak | PEKERJA | caping, joran |
| `PANDAI_DESA` | Pandai Besi Desa | kampung | 1 | ya | PEKERJA | celemek, palu |
| `PEDAGANG_KELILING` | Pedagang Keliling | kampung, pos, jalan | 1 | tidak | PEDAGANG | keranjang punggung |
| `ANAK` | Anak | semua permukiman | 0 | tidak | ANAK | ikat rambut (skala tubuh 0.6) |
| `TETUA_DESA` | Kakek/Nenek | kampung, hunian | 0 | tidak | TETUA | tongkat, kipas |
| `PATRIARK_KLAN` | Patriark Klan | klan | 4 | ya | PEJABAT | mahkota giok kecil |
| `TUAN_MUDA` | Tuan Muda Klan | klan | 2 | tidak | KULTIVATOR | jubah sutra, kipas lipat |
| `TETUA_KLAN` | Tetua Klan | klan | 3 | tidak | TETUA | jubah gelap |
| `MURID_KLAN` | Murid Klan | klan | 1 | tidak | KULTIVATOR | ikat pinggang klan |
| `MURID_SEKTE_LUAR` / `MURID_SEKTE_DALAM` | Murid Luar/Dalam | kompleks | 1 / 2 | tidak | KULTIVATOR | jubah lambang sekte |
| `TETUA_SEKTE` | Tetua Sekte | kompleks | 3 | tidak | TETUA | jubah tetua + lambang |
| `PELAYAN_SEKTE` | Pelayan Sekte | kompleks | 0 | tidak | PEKERJA | sapu |
| `PERTAPA` | Pertapa | gua pertapa | 4 | ya | RAHIB | jubah tambal, tasbih, janggut putih |
| `SISA_JIWA` | Sisa Jiwa | makam warisan | 4 | ya | — | tembus pandang (`FLAG_GHOST` yang ada) |
| `TETUA_IBLIS` | Tetua Iblis | sarang iblis | 4 | ya | — | jubah hitam-merah, tengkorak pinggang |
| `TAWANAN` | Tawanan | sarang iblis | 0 | ya | — | pakaian compang, tali |
| `PENGEMIS` | Pengemis | kumuh | 0 | tidak | ANAK/TETUA | mangkuk, tongkat |

`NpcRole.townsfolk()` tetap menentukan siapa yang melukai Nama Baik; peran baru warga sipil masuk `townsfolk()`;
`TETUA_IBLIS` tidak. Peran tempur bermusuhan memanggil `markRealmFoe` (38.0 butir 14).

#### 38.N.3 Jadwal harian (jam = `(waktuHari / 1000 + 6) mod 24`; 0 tick = 06:00)

Kelompok jadwal (kolom = jam mulai; "→" = berjalan ke). Setiap penduduk punya geser acak ±40 menit dunia (hash) agar
tidak bergerak serempak.

| Jam | PEKERJA (petani, koki, kuli, empu, tukang) | PEDAGANG | PENJAGA | ANAK | TETUA | KULTIVATOR | CENDEKIA / RAHIB | PEJABAT |
|---|---|---|---|---|---|---|---|---|
| 04-05 | tidur | tidur | regu malam patroli; regu pagi bangun | tidur | bangun, `duduk_kursi` di serambi | `napas_meditasi` di titik `meditasi` | rahib: lonceng subuh, `sujud_dupa` | tidur |
| 05-06 | bangun, `basuh_muka` di sumur/baskom, `sapu` halaman | bangun, `basuh_muka` | ganti regu di gerbang (`baoquan` ke regu lain) | tidur | `tai_chi_pagi` di alun/taman | `latihan_pedang_1` | menyapu kuil | bangun |
| 06-07 | `makan` sarapan di `meja_makan` rumah | sarapan; buka kios: `angkat_bawa` peti → `lapak` | sarapan bergiliran di barak | bangun, sarapan | sarapan | sarapan di asrama/ruang makan | sarapan | sarapan |
| 07-12 | **kerja** di titik `kerja:<alat>` (klip sesuai alat) dengan jeda `minum` tiap 50 menit | **jaga kios**: `seru_dagang` tiap 20-40 s bila ada orang ≤ 12, `tawar` saat transaksi, `hitung_sempoa` saat sepi | patroli rute simpul `jaga`, berdiri `jaga_tombak` di pos | sekolah (`membaca` di bangku) 07-10, lalu `main_*` di titik `main` | `catur` berdua di `kursi` teduh, `kipas`, `obrol` | latihan bentuk (`latihan_pedang_1..3`, `latihan_tinju`), tanding latih berpasangan | pustakawan `menulis`, `rak_susun`; rahib `sapu`, `sujud_dupa` | `menulis` di meja, `obrol` menteri |
| 12-13 | **makan siang**: koki di dapur (`masak`), lainnya ke rumah makan/meja rumah (`makan`) | makan di kios (`makan` duduk) bergiliran | makan bergiliran | makan di rumah | tidur siang (`tidur_duduk`) | makan, lalu `napas_meditasi` 20 menit | makan | makan bersama di aula |
| 13-17 | kerja lagi | jaga kios; jual-beli antar-NPC (38.N.4) | patroli | `main_*`, membantu orang tua (`angkat_bawa` kecil) | `obrol` di alun, memberi makan ikan (`lempar_pakan`) | murid: tugas (`sapu`, `angkat_air`), dalam: meditasi gua | kuliah 15-16 (penceramah), membaca | sidang Yamen (`palu_hakim`) |
| 17-18 | pulang, `basuh_muka` | tutup kios: `angkat_bawa` peti → rumah | regu sore | pulang dipanggil ibu (`lambai`) | pulang | latihan senja berkelompok (formasi barisan) | doa senja (`sujud_dupa`), lonceng | pulang |
| 18-20 | makan malam di rumah atau kedai; minum teh `minum_teh` di kedai teh | makan malam; sebagian buka **Pasar Malam** (ibu kota) | regu malam naik; genderang 22:00 (`genderang`) | makan malam, `main_lentera` saat festival | `minum_teh` di kedai, `cerita` (gestur bercerita) ke anak | makan, `obrol` | menulis | jamuan istana (malam acara) |
| 20-22 | `obrol` di serambi, sebagian ke kedai | Pasar Malam (ibu kota) sampai 24 | patroli lentera (prop `lentera_tangan`) | tidur 20:30 | tidur 21 | meditasi malam | meditasi | kerja lilin (`menulis`) |
| 22-04 | tidur di `kasur` (klip `tidur`) | tidur | regu malam: patroli + jaga gerbang tertutup | tidur | tidur | `napas_meditasi` (setengah), tidur (setengah) | tidur | tidur |

**Variasi mingguan dan musiman** (`hari = d % 7`, `musim = (d / 24) % 4`: semi, panas, gugur, dingin):
- Hari 0 **hari pasar** (kampung `desa_besar`, pos dagang): pedagang keliling datang (+3 penduduk tamu), petani menjual
  hasil di `pasar_desa`; hari 6 **hari istirahat**: separuh PEKERJA tidak bekerja → `obrol`, `catur`, memancing.
- Semi: PETANI `tanam_bibit` di ladang; panas: NELAYAN dua kali lebih banyak, anak `main_air` di kolam; gugur: `memanen`,
  keranjang hasil (prop), Festival Panen kampung; dingin: di bioma bersalju penduduk 40 % lebih lama di dalam rumah,
  `hangat_tangan` di dekat tungku.
- **Acara** menimpa jadwal (38.B.6): Festival Lentera (semua ke `jalan_lentera`/alun, anak membawa lentera), Lelang Agung
  (pedagang kaya ke balai lelang), Turnamen (penonton ke tribun → impostor), Gelombang Binatang (38.N.5 bahaya).
- **Cuaca:** hujan → keluar rumah memakai **payung kertas** (prop) atau berteduh di titik beratap, PETANI berhenti;
  badai petir (dibaca, tidak diubah) → anak `takut_sembunyi` di rumah, KULTIVATOR `tatap_langit`; salju → `hangat_tangan`;
  **kabut Qi pekat** (`DuniaApi.sky().weatherAt`) → kultivator keluar bermeditasi di luar (aura lebih terang);
  **peristiwa langit**: Gerhana Darah → semua sipil di dalam rumah, penjaga ganda, rahib `sujud_dupa`; Hujan Meteor →
  penduduk berkumpul di titik `obrol` terbuka, `tunjuk` ke langit; Bulan Kembar → `tatap_langit` dan bisik-bisik.

#### 38.N.4 Perilaku yang terlihat (L0/L1)

1. **Ke titik nyata:** setiap kegiatan punya titik NBT (kasur, kursi, `kerja:<alat>`, `lapak`, `main`, `obrol`, `jaga`,
   `latihan`, `meditasi`, `dupa`); penduduk berjalan ke sana, berbalik ke `hadap` titik, lalu memainkan klipnya. Titik
   terpakai dikunci (satu orang per titik; antre 3 blok di depannya bila penuh).
2. **Membawa barang:** perpindahan kegiatan yang memindahkan barang memasang prop (`DATA_PROP`): kuli memikul peti dari
   dermaga ke gudang, koki membawa keranjang sayur dari pasar ke dapur, petani membawa cangkul ke ladang dan keranjang
   panen pulang, rahib membawa dupa, anak membawa layang-layang.
3. **Jual-beli antar-NPC:** tiap 3-6 menit dunia di pasar, satu penduduk "pembeli" berjalan ke `lapak`: kedua pihak `tawar`
   60 tick (pedagang menggeleng, pembeli menunjuk), lalu tukar prop (kantong uang ↔ barang) dan `baoquan` kecil. Tidak
   mengubah ekonomi pemain (hanya pertunjukan + penghitung "laris" untuk dialog).
4. **Anak bermain:** 2-4 anak di titik `main`: `main_kejar` (berlari antar dua titik `main`, tertawa), `main_lompat`
   (lompat tali bergantian), `main_gasing` (prop gasing di tanah berputar), `main_layangan` (prop layang-layang terbang
   klien), `main_lentera` (festival). Anak lari ke rumah saat bahaya atau saat pemain ditakuti (38.R).
5. **Tetua duduk:** `catur` berdua (papan catur prop di meja, tangan memindah bidak tiap 80 tick), `kipas`, `cerita` ke
   anak yang duduk `duduk_lantai` di depannya.
6. **Pedagang berseru:** `seru_dagang` + gelembung kalimat dari kumpulan per peran (38.S.6) tiap 20-40 s bila pemain ≤ 12
   blok; menyebut barang hari itu ("Pedang besi roh, baru dari tungku!").
7. **Kultivator berlatih:** bentuk pedang tiga rangkaian, tinju, tombak; meditasi dengan `FLAG_AURA` (aura warna Dao);
   tanding latih berpasangan (dua tubuh berhadapan memainkan `tanding_a`/`tanding_b` yang tersinkron, tanpa kerusakan).
8. **Obrolan bergestur:** pasangan di titik `obrol` atau berpapasan: 4 baris (sistem obrolan `NpcLife` yang ada) dengan
   gestur per baris (`bicara_tangan`, `angguk`, `geleng`, `tertawa`, `berdebat`, `tunjuk`, `bisik`), topik dari keadaan
   dunia (38.S.5: kabar, acara, kejahatan, perbuatan pemain), saling menatap (`DATA_LOOK_AT`).
9. **Reaksi terhadap pemain:** menatap pemain yang lewat ≤ 6 blok (2 s); ranah pemain ≥ 2 di atas → `hormat` (perilaku
   lama), ≥ 4 di atas → minggir dan `hormat_dalam`; Nama Baik ≥ 80 → `lambai` dan menyapa dengan nama; ≤ −30 → `marah_tunjuk`
   dan `bisik` ke sebelahnya; sikap takut (38.R.5) → `berlutut`/anak bersembunyi; pemain buronan → `tunjuk` + berteriak
   memanggil penjaga.
10. **Reaksi kejahatan:** saksi `tunjuk` + baris "Pencuri!", berlari ke penjaga terdekat (penjaga datang), lainnya menjauh
    8 blok dan menonton dengan tangan di mulut (`kaget`).
11. **Bahaya** (monster bermusuhan ≤ 16 blok, pertarungan ≤ 12 blok, Gelombang Binatang, pemain menyerang NPC): sipil
    `lari_takut` ke titik `pintu` terdekat lalu tubuhnya lenyap "masuk" (datanya di dalam rumah), atau `takut_sembunyi`
    di balik dinding bila tidak ada pintu; penjaga `hunus` lalu menyerang monster; kultivator ikut bertarung bila tier ≥ 2.
    Setelah 60 s tanpa bahaya, kembali ke jadwal.
12. **Reaksi langit dan suara:** lonceng kuil (`hormat` menghadap kuil), genderang malam (pedagang menutup lebih cepat),
    petir (anak terlonjak `kaget`).

#### 38.N.5 Klip animasi (keyframe)

Dibangun di atas sistem yang ada: kanal `PoseLibrary` (`HX, HY, LEAN, TWIST, RAX, RAY, RAZ, LAX, LAY, LAZ, RLX, RLZ, LLX,
LLZ, LIFT, RLY, LLY, TILT`; arti tanda seperti javadoc `PoseLibrary`: `RAX < 0` lengan maju/naik, `LEAN > 0` membungkuk
ke depan, `LIFT` dalam blok), `CastPoses.Snapshot` untuk menaruh hasil pada setiap `PlayerModel` NPC (kulit, jubah,
perlengkapan ikut), dan `NpcPoses.Blend` untuk transisi. Kelas baru `client/dunia/tempat/hidup/NpcKlip` (data klip) dan
`NpcKlipPlayer` (sampler) dipanggil dari `NpcPoses.target` (38NL menyuntingnya): bila `DATA_ANIM ≠ 0`, klip menang atas
bendera lama `FLAG_TALK/BOW/GAZE/WORK` (yang tetap jalan untuk kota lama).

**Format klip:** `Klip(id, durasi tick, loop, kunci[] {t, kanal = derajat}, mask kanal, easing per segmen
(`easeInOutSine` default, `easeOutBack` untuk pukulan), mod (`BREATH`, `TREMBLE`, `BEAT`, `SHAKE_HEAD` dari `PoseLibrary`),
prop default, peristiwa {t, suara/partikel})`. Kanal yang tidak ditulis = NaN = tidak dikendalikan (kaki tetap berjalan
vanilla saat klip hanya menulis lengan). Derajat dikonversi ke radian saat dimuat. Kunci di bawah: `t` dalam tick.

**Pembauran:** (1) lapisan dasar = gerak vanilla (ayunan kaki saat berjalan); (2) lapisan klip dengan mask, transisi
**6 tick** cross-fade (`Blend` yang ada, `a = 1 − 0.72^dt`); (3) lapisan tambahan: tatapan kepala (38.N.5.3), napas
(`LIFT` ±0.01, periode 80 tick, semua klip diam), kedip mata (38.O.2). Klip `once` kembali ke klip jadwal setelah selesai.
Fase tiap NPC digeser `id × 7 tick` agar gerak berulang tidak serempak.

##### 38.N.5.1 Gerak dan sikap

| Id | Nama | Durasi | Kunci (derajat) | Mask / catatan |
|---|---|---|---|---|
| 1 | `jalan_santai` | loop 40 | t0: RAX 15, LAX 15, RAZ 8, LAZ −8, RAY −30, LAY 30 (tangan di punggung); HX 5 | lengan saja; kaki vanilla |
| 2 | `jalan_tergesa` | loop 20 | LEAN 8; ayunan lengan vanilla ×1.4 (pengali, bukan kunci) | badan |
| 3 | `jalan_tua` | loop 50 | LEAN 18, HX −10 (kepala tegak), RAX −35, RAZ 10 (tangan di tongkat); kecepatan 0.6 | prop `tongkat` kanan |
| 4 | `jalan_bawa` | loop 30 | RAX −62, LAX −62, RAY −14, LAY 14; LEAN 4; ayunan lengan dimatikan | prop `peti` dua tangan |
| 5 | `jalan_pikul` | loop 30 | RAX −160, RAZ 25 (tangan memegang pikulan di bahu), LAX 10; LIFT bob 0.03 tiap langkah | prop `pikulan` punggung |
| 6 | `jalan_anak` | loop 16 | LIFT 0 → 0.06 (t4) → 0 (t8) berulang; lengan vanilla ×1.2 | skala anak 0.6 |
| 7 | `baris_jaga` | loop 30 | RAX −30 (tombak tegak), LAX 0 kaku, ayunan kaki vanilla ×0.8 | prop `tombak` |
| 8 | `lari` | loop 12 | LEAN 15, ayunan vanilla | — |
| 9 | `lari_takut` | loop 12 | LEAN 20; t0: RAX −150, LAX −150, RAZ 30, LAZ −30 (tangan di atas kepala); HY menoleh ke belakang ±60 tiap 24 tick | — |
| 10 | `duduk_kursi` | loop | RLX −90, LLX −90, LIFT −0.5; RAX −20, LAX −20 (tangan di lutut) | posisi ke titik `kursi` + 0.2 |
| 11 | `duduk_lantai` | loop | RLX −80, LLX −80, RLY 35, LLY −35, RLZ 20, LLZ −20, LIFT −0.65; RAX −25, LAX −25 | bersila |
| 12 | `napas_meditasi` | loop 80 | memakai `MeditationPose` yang ada + LIFT +0.015 di t40; `FLAG_AURA` | kultivator |
| 13 | `tidur` | loop 120 | **klien memutar tubuh 90° (sumbu X) di renderer** dan menaruh kepala di bantal (titik `kasur` + hadap); dada naik-turun LIFT ±0.01 | **tidak** `startSleeping` (mengubah `OCCUPIED` blok kasur, dilarang); tubuh `noPhysics` di kasur |
| 14 | `tidur_duduk` | loop 100 | `duduk_kursi` + HX 35 (kepala terkulai), LEAN 10 | tetua siang |
| 15 | `berdiri_jaga` | loop 60 | pose penjaga lama + HY menyapu ±25 tiap 60 | — |
| 16 | `berlutut` | once 12 → loop | RLX −90, LLX 70, LIFT −0.4; LEAN 15; HX 20 | takut / menyerah |
| 17 | `takut_sembunyi` | loop 20 | LEAN 50, RAX −170, LAX −170, RAZ 20, LAZ −20, LIFT −0.3; mod TREMBLE | — |
| 18 | `kaget` | once 10 | t0 netral → t3: LIFT 0.15, RAX −60, LAX −60, HX −15 → t10 kembali | — |

##### 38.N.5.2 Kerja, makan, sapaan

| Id | Nama | Durasi | Kunci | Prop / peristiwa |
|---|---|---|---|---|
| 20 | `makan` | loop 40 | LAX −45, LAY 22 (mangkuk di depan dada); RAX −70 ↔ −88 (t0/t10/t20), HX 10 ↔ 0 (mengunyah t10-t30) | kiri `mangkuk`, kanan `sumpit` |
| 21 | `minum_teh` | loop 100 | t0: RAX −40; t20: RAX −85, RAY −32, HX −12 (menyeruput, 20 tick); t50 kembali; LAX −30 (lepek) | kanan `cangkir`, kiri `lepek` |
| 22 | `tuang_teh` | once 40 | t0 RAX −55; t10 RAX −60, RAZ −20 (teko miring); t30 kembali | kanan `teko`; partikel aliran teh t10-t28 (klien) |
| 23 | `masak` | loop 30 | LEAN 12; RAX −55, RAY −20 → +10 → −20 (aduk, sinus); LAX −40 (memegang wajan) | kanan `sendok_sayur`, asap partikel |
| 24 | `sapu` | loop 24 | LEAN 20; RAX −45, LAX −35; TWIST −12 (t0) → +12 (t12) → −12 | dua tangan `sapu`; debu kecil |
| 25 | `palu_landasan` | loop 20 | t0: RAX −150, LEAN −4; t6: RAX −40, LEAN 12 (easeOutBack); t10 tahan; t20 kembali | kanan `palu`; suara `block.anvil.use` (0.4) + percikan di t6 |
| 26 | `tumbuk_herba` | loop 14 | LEAN 25; RAX −50 → −28 (t7) → −50; LAX −40 (memegang lumpang) | kanan `alu`, lumpang di meja (blok) |
| 27 | `menulis` | loop 30 | LEAN 15, HX 25; RAX −55, RAY −10 ↔ +5 (goresan), LAX −45 | kanan `kuas` |
| 28 | `membaca` | loop 80 | RAX −60, LAX −60, RAY −25, LAY 25, HX 22; t70-t80: LAY 40 (balik halaman) | dua tangan `kitab` |
| 29 | `hitung_sempoa` | loop 16 | RAX −50, LAX −50, RAY −20, LAY 20; RAZ ±4 (jari) | `sempoa` |
| 30 | `tawar` | loop 30 | t0 RAX −70 (telapak terbuka maju); t10 RAX −45; mod SHAKE_HEAD t10-t20; LAX −50, LAY 20 (menghitung jari) | — |
| 31 | `seru_dagang` | once 30 | RAX −110, RAY −50 (tangan di mulut); HX −10; LEAN −4 | gelembung kalimat |
| 32 | `hormat` (baoquan) | once 30 | t0-t6: RAX −80, LAX −80, RAY −35, LAY 35 (kepal + telapak); t6-t18: LEAN 22; t30 kembali | tetap dipakai `FLAG_BOW` lama |
| 33 | `hormat_dalam` | once 50 | seperti 32 dengan LEAN 55, tahan t10-t40 | pemain ≥ 4 ranah di atas |
| 34 | `lambai` | once 24 | RAX −160, RAZ 20 ± 25 (t0, t6, t12, t18) | — |
| 35 | `tunjuk` | once 30 | RAX −90 ke arah sasaran (RAY dihitung dari `DATA_LOOK_AT`), HX/HY ke sasaran | — |
| 36 | `tertawa` | once 40 | LEAN −8, HX −15; LAX −30, LAY 40 (tangan di perut); mod BEAT (bahu) | suara tawa villager (pitch 1.2) |
| 37 | `berdebat` | loop 20 | LEAN 10; RAX −70 ↔ −30, LAX −30 ↔ −70 bergantian; HX −5 | — |
| 38 | `bicara_tangan` | loop 30 | RAX −35 ↔ −50, RAZ 10; angguk HX 0 ↔ 8 | menggantikan `FLAG_TALK` |
| 39 | `bisik` | once 40 | LEAN 15, TWIST 20, LAX −110, LAY 40 (tangan menutup mulut) | — |
| 40 | `marah_tunjuk` | once 30 | RAX −90 tegang (TREMBLE), LEAN 8, HX −8 | — |
| 41 | `angkat_bawa` | once 20 | t0 LEAN 40, RAX −90, LAX −90 (mengambil); t20 LEAN 4, RAX −62, LAX −62 | lalu `jalan_bawa` |
| 42 | `memancing` | loop 200 | t0: RAX −150 (angkat); t8: RAX −45 (lempar, easeOutBack); tahan; mod BEAT kecil di ujung joran (klien); t180-200 tarik RAX −100 | kanan `joran`, tali klien |
| 43 | `mencangkul` | loop 24 | t0: RAX −130, LAX −130; t10: RAX −20, LAX −20, LEAN 20 (easeOutBack); t24 kembali | dua tangan `cangkul`; partikel tanah t10 |
| 44 | `memanen` | loop 30 | LEAN 45; RAX −100 → −40 (meraih, t15); LAX −40 (keranjang) | kiri `keranjang` |
| 45 | `tanam_bibit` | loop 40 | `berlutut` + RAX −60 → −20 (t20) | — |
| 46 | `angkat_air` | loop 30 | `jalan_bawa` dengan RAX −20, LAX −20, RAZ 15, LAZ −15 (dua ember di samping) | `ember` ×2 |
| 47 | `sujud_dupa` | once 90 | dua tangan di dada RAX −90, LAX −90, RAY −40, LAY 40; LEAN 0 → 30 (t15) → 0 (t30) ×3 | `dupa` (asap klien) |
| 48 | `basuh_muka` | once 60 | LEAN 30; RAX −80, LAX −80 → −120 (t15, ke wajah) → −80 ×2 | percikan air |
| 49 | `genderang` | loop 16 | RAX −140 ↔ −60, LAX −60 ↔ −140 bergantian tiap 8 | `pemukul` ×2; suara t0/t8 |
| 50 | `palu_hakim` | once 30 | RAX −120 → −50 (t8) | `palu_kayu`; suara `block.wood.hit` |
| 51 | `lempar_pakan` | once 24 | RAX −60 → −110, RAZ 20 (t10) | partikel butir |
| 52 | `kipas` | loop 20 | RAX −80, RAY −40, RAZ 10 ↔ −10 | `kipas_lipat` |
| 53 | `catur` | loop 80 | duduk + LEAN 20, HX 30; t70: RAX −70 (pindah bidak) | papan `catur` di meja |
| 54 | `cerita` | loop 40 | duduk + `bicara_tangan` lebih lebar (RAZ 30) | — |
| 55 | `hangat_tangan` | loop 40 | RAX −70, LAX −70, RAY −30, LAY 30 (telapak ke tungku), RAZ ±3 | — |
| 56 | `tatap_langit` | loop | HX −45, LEAN −6 (sama dengan `FLAG_GAZE` lama) | — |
| 57 | `basuh_pakaian`, `jemur` | loop 30 | LEAN 35, RAX/LAX −70 ↔ −50 / berdiri RAX −150 (menjemur) | `kain` |
| 58 | `rak_susun` | loop 40 | RAX −120 (meraih rak) ↔ −70 | `kitab` |
| 59 | `hunus` | once 8 | RAX −60 → −20 (pedang keluar), pakai pose siaga 35 | pedang |

##### 38.N.5.3 Latihan, anak, festival, perbaikan

| Id | Nama | Durasi | Kunci / sumber |
|---|---|---|---|
| 60-62 | `latihan_pedang_1..3` | loop 120 | rangkaian kunci dari `client/duel/anim/DuelPoseLibrary` (siaga → tebas datar → tebas turun → tusuk → kembali), 4 gerakan × 30 tick, TWIST ±25 mengikuti tebasan; pedang di tangan kanan (item senjata NPC `NpcGear`) |
| 63 | `latihan_tinju` | loop 20 | kuda-kuda RLZ 25, LLZ −25, LIFT −0.25; RAX −90 (pukul, t0-t5) ↔ −20, LAX bergantian t10-t15 |
| 64 | `latihan_tombak` | loop 30 | LEAN 10; RAX −70, LAX −70 → tusuk t8 (TILT 6) → kembali |
| 65 | `tai_chi_pagi` | loop 160 | RAX/LAX −40 → −90 → −40 lambat, TWIST ±20, berat badan bergeser RLZ/LLZ ±10 |
| 66-67 | `tanding_a/b` | loop 60 | dua klip cermin: `a` menebas t10, `b` menangkis t10 (kunci tangkis 35), lalu bertukar t40 |
| 68 | `main_kejar` | loop 12 | `lari` anak + `tertawa` acak |
| 69 | `main_lompat` | loop 20 | LIFT 0 → 0.45 (t6) → 0 (t12); RAX −30, LAX −30 (memegang tali) |
| 70 | `main_gasing` | once 30 → loop | jongkok LEAN 50, LIFT −0.4; RAX −40 → −10 (lempar gasing t10) |
| 71 | `main_layangan` | loop 40 | RAX −120, HX −30, RAY ±8 (tarik tali) |
| 72 | `main_lentera` / `lentera_angkat` | loop 40 | RAX −150, RAZ 10 (lentera tinggi), HX −20 | `lentera_tangan` (cahaya klien) |
| 73 | `menari_panen` | loop 40 | RAX/LAX −150 ↔ −100 bergantian, TWIST ±25, LIFT 0.05 tiap 10 |
| 74 | `perbaiki_palu` | loop 16 | seperti `palu_landasan` dengan LEAN 30 menghadap dinding | `palu` (38.R) |
| 75 | `perbaiki_plester` | loop 30 | RAX −80, RAY −40 → +20 (sapuan sendok plester) | `sendok_plester`, `ember` |
| 76 | `angkat_papan` | loop 30 | RAX −170, RAZ 30 (papan di bahu), LAX 15 | `papan_kayu` |
| 77 | `ganti_tanah` | loop 24 | `mencangkul` setengah amplitudo | `sekop` |
| 78 | `tanam_ulang` | loop 40 | `tanam_bibit` | `bibit` |

##### 38.N.5.4 Tatapan kepala, mata, prop

- **Tatapan:** sasaran = `DATA_LOOK_AT` (lawan bicara, pemain yang disapa, benda kerja), selain itu arah jalan; batas
  yaw ±55°, pitch ±30°; badan ikut berputar (TWIST) bila yaw > 40°; kecepatan 8°/tick dengan easing; pemain yang lewat
  ≤ 6 blok ditatap 2 s (sekali per 30 s per NPC per pemain).
- **Mata dan kedip** (38.O.2): lapisan wajah dengan pupil bergeser 1 piksel ke arah tatapan (kiri/tengah/kanan) dan kedip
  2 frame tiap 60-140 tick (acak per NPC); mata tertutup saat `tidur`, `napas_meditasi`, `sujud_dupa`.
- **Prop di tangan:** `NpcPropLayer` (klien, baru) menggambar model prop 38.O.4 di tangan kanan/kiri/punggung menurut
  `DATA_PROP`, dengan transform per klip (pola `DiscipleHeldItemLayer.inHand`, `SektePoses.Prop`); prop hilang saat
  bertarung.

#### 38.N.6 Penduduk jauh, impostor dan saksi

- **L2 impostor** (48-128 blok): `PendudukSnapshot` (payload S2C 38NL, maks 48 entri, tiap 40 tick bila berubah):
  `situsId`, lalu per entri posisi relatif ter-kuantisasi (short ×3), yaw (byte), look (int), klip dasar (byte: jalan,
  diam, duduk, kerja). Klien menggambar **boneka impostor** (model 38.O.1 "impostor", 10 kubus, tekstur 32 × 32 diwarnai
  jubah) dengan ayunan kaki prosedural; Minimal 0, Normal 32, Maksimal 48 per pemain; tidak digambar di belakang kamera
  (frustum) dan memudar 120-128 blok.
- **Penonton** (tribun arena, alun-alun festival): impostor duduk di titik `kursi` dengan klip `sorak` prosedural (lengan
  naik bergiliran), maks 160 per arena (Minimal 0, Normal 64, Maksimal 160), digambar dalam satu batch per jenis.
- **L3 data:** posisi dihitung hanya bila dibutuhkan (saksi kejahatan dalam lot yang sama: `Jadwal.kegiatan` → titik; quest;
  kabar).
- **Saksi data** (38.B.5.2): kejahatan di dalam lot yang menurut jadwal sedang dihuni penduduk data → peluang 50 % tercatat.

#### 38.N.7 Anggaran dan penyebaran tick

| Tempat | Penduduk data | Tubuh maks (config) | Impostor maks | Jenis `DuniaBudget` |
|---|---|---|---|---|
| Kota Besar | 650 | 28 (`kotaBesar.tubuhMaks` 12-48) | 48 | `npc_kota_besar` |
| Kota Kultivator (34) | 40 (baru, dari titik) | 14 (`kota.npcPerTown` lama) + 6 penduduk | 24 | `npc` (lama) + `npc_hidup` |
| Kampung / desa besar / klan | 6 / 18 / 36 / 30 | 6 / 10 / 10 / 10 | 0 / 16 / 24 / 16 | `npc_kampung` |
| Kompleks sekte | 40 / 80 / 120 | 12 | 24 | `npc_kompleks` |
| Gua pertapa, makam, sarang | 1-6 | 6 | 0 | `npc_gua` |
| Pos singgah / dagang | 4-8 | 4 | 0 | `npc_pos` |

- Topi global semua jenis 38: `tempat.tubuhGlobal` 64 (24-128); per pemain 24 tubuh dalam 96 blok (semua jenis 38);
  `DuniaBudget.overloaded` (MSPT > 45) → tidak ada tubuh baru, impostor berhenti diperbarui, klip tetap.
- Penyebaran: `Pemanggil.isi` satu tempat per tick bergiliran; gol khusus tubuh tiap 4 tick (≤ 24 blok dari pemain) atau
  10 tick (`DuniaBudget.throttled`); evaluasi jadwal tubuh tiap 40 tick; pencarian jalur maks 8 per tick server untuk
  semua tubuh 38 (antrean).
- Klien: animasi penuh ≤ 32 blok; 32-64 blok klip disampel tiap 2 frame tanpa tatapan/kedip/prop kecil; > 64 blok hanya
  sikap dasar. `EffectQuality` Minimal: klip tanpa mod TREMBLE/BEAT, tanpa partikel klip.

#### 38.N.8 Config dan uji

- Config `tempat.hidup`: `aktif` (true), `tubuhGlobal` 64, `perPemain` 24, `impostor` true, `obrolanDetik` 40,
  `transaksiAntarNpc` true, `reaksiCuaca` true, `jadwalGeser` 40 (menit dunia).
- Uji (`/kultivasi uji hidup ...`, op): `jadwal <npc|id>` (kegiatan sekarang dan berikutnya), `jam <0-23>` (pratinjau
  jadwal tanpa mengubah waktu: tubuh memainkan kegiatan jam itu), `klip <id> [npc]` (mainkan klip pada NPC terdekat),
  `klip semua` (barisan 24 NPC uji memainkan semua klip berurutan, 38.E.4), `prop <id>`, `impostor` (jumlah dan LOD),
  `saksi` (siapa melihat pemain sekarang), `bahaya` (simulasikan bahaya 60 s), `cuaca <hujan|badai|kabut|gerhana>`
  (hanya **pratinjau reaksi** NPC; cuaca dunia tidak diubah).

---------------------------------------------------------------------------------------------------------------------

### 38.O Model 3D, tekstur dan animasi model (modul 38MD)

Permintaan pengguna: "Semuanya jangan cuma ide atau event: harus benar-benar ada model 3D yang bagus, animasi dan cerita
yang bagus." Semua model buatan sendiri: kubus (ModelPart atau model item JSON) dan tekstur prosedural dari Python
(`tools/png.py`), **tanpa kotak placeholder** dan tanpa aset unduhan. Bangunan sendiri adalah blok vanilla (NBT, 38.B-C);
yang bergerak atau hidup adalah model di bawah.

#### 38.O.0 Aturan model (diperiksa `validate()` di setiap generator, pola `gen_dunia_naga_models.py`)

- **Model item JSON** (prop, pajangan): jangkar `(8, 8, 8)`, depan menghadap utara (−z), elemen dalam −16..32, uv 0..16,
  **maks 40 elemen**, rotasi elemen satu sumbu ±22.5/±45, uv diskalakan ke ukuran sisi (2 texel per piksel model pada
  tekstur 32 px). Berkas: `assets/kultivator/models/item/fx/tempat/<id>.json`, `items/fx/tempat/<id>.json`, tekstur
  `textures/item/fx/tempat/<id>.png`. Ditampilkan lewat `DataComponents.ITEM_MODEL` di tangan (prop) atau di
  `ItemDisplay` klien yang dipose dengan `DisplayKey` (pajangan).
- **ModelPart entitas** (`LayerDefinition` di kelas model klien): ukuran dalam piksel model (16 = 1 blok), pivot
  `PartPose.offset`, `texOffs` dalam atlas entitas 128 × 128 (atau pita 32 px pada atlas perlengkapan K
  `perlengkapan.png` 64 × 512, yang dilebarkan 38MD menjadi 64 × 1024 dengan pita 16-31 baru; pita 0-15 lama **tidak
  diubah**).
- **Palet** (dipakai semua generator 38, `tools/tempat_palet.py`): lak merah `#8E1F1F`, emas kusam `#C9A24A`, giok
  `#5FA88C`, kayu tua `#5A3A22`, kayu muda `#9C7448`, besi `#7C8288`, perunggu `#8C6A3A`, kertas `#E8DDC0`, tinta
  `#1E1A18`, sutra biru `#3A5A8C`, kain kasar `#8A7A60`, kristal `#B8A6F0` (pancar), darah iblis `#6A1420`, tulang
  `#D8CFB8`. Bayangan: setiap warna punya 4 tingkat terang (`shade 0.7, 0.85, 1.0, 1.15`) + derau 3 % dan tepi gelap 1 px.
- **Pemeriksaan visual:** setiap generator menulis pratinjau 8× ke folder pratinjau bila diberi argumen; pemeriksa
  (modul dan langkah finish) **membuka gambarnya dengan Read** dan mencatat di berkas progres modul; render isometrik
  model (proyeksi kubus ke PNG, `tools/tempat_iso.py`, 38MD) untuk setiap model entitas dan prop besar.

#### 38.O.1 Tubuh NPC

Tubuh tetap `PlayerModel` kulit murid/NPC yang ada (`NpcRenderer`, look 0-N, jubah diwarnai) + lapisan perlengkapan
(`KotaGearLayer`) + lapisan baru:

| Lapisan / model | Isi | Spesifikasi |
|---|---|---|
| `NpcFaceLayer` (baru, 38MD) | mata, alis, mulut 2 frame | kotak wajah 8 × 8 × 0.02 px di depan kepala (pivot kepala, z −4.01); atlas `textures/dunia/tempat/wajah.png` 128 × 64: 8 bentuk mata × 4 keadaan (tengah, kiri, kanan, tertutup), 6 mulut (diam, bicara A/B, senyum, cemberut, terkejut); dipilih dari `look` + keadaan (38.N.5.4) |
| Skala anak | `ANAK` | `scale 0.62` di render state, kepala 1.15 relatif (proporsi kanak-kanak) |
| Skala tua | `TETUA_DESA`, `KEPALA_DESA`, `PERTAPA` | tidak diskala; postur lewat klip `jalan_tua` |
| Hantu | `SISA_JIWA` | `FLAG_GHOST` yang ada (tembus pandang 0.45) + kabut bawah tubuh (quad aditif) |
| Impostor (`ImpostorModel`, klien) | penduduk 48-128 blok | 10 kubus: kepala 8³, badan 8 × 12 × 4, lengan 2 × (4 × 12 × 4), kaki 2 × (4 × 12 × 4), rok jubah 9 × 6 × 5, topi umum 9 × 2 × 9 + 5 × 3 × 5; tekstur 32 × 32 abu netral diwarnai `robe`; ayunan prosedural `sin(t × 0.6) × 0.5 rad` |

**Perlengkapan baru** (pita atlas 16-31; tiap baris = satu `GearPiece` di `KotaGear`, dipasang pada bagian tubuh):

| Id | Bagian | Kubus (ukuran px; offset dari pivot) | Pita | Untuk peran |
|---|---|---|---|---|
| `mahkota_guan_giok` | kepala | dasar 4 × 2 × 3 (0, −9, 0); tusuk 7 × 0.6 × 0.6 (0, −9.5, 0) | 2 giok, 1 emas | PANGERAN_KOTA, PATRIARK_KLAN |
| `jubah_naga` | badan | lapis dada 8.6 × 12.4 × 4.6; lengan lebar 5 × 6 × 5 ×2 di siku | 16 sutra kuning-naga (sulaman tekstur naga) | PANGERAN_KOTA |
| `topi_pejabat` | kepala | badan topi 8.4 × 4 × 8.4 (0, −8, 0); dua sayap 6 × 0.6 × 1 (±6, −7, 2) | 0 hitam | MENTERI, HAKIM |
| `helm_berumbai` | kepala | helm 8.8 × 5 × 8.8; jambul 2 × 4 × 2 (0, −11, 0) | 7 lamela, 3 merah | PENGAWAL_ISTANA, PENJAGA_GERBANG |
| `helm_kapten` | kepala | helm 9 × 5.5 × 9; sayap 4 × 3 × 0.5 ×2; jambul 2 × 5 × 2 | 1 emas, 3 merah | KAPTEN_PENJAGA |
| `zirah_lamela` | badan + lengan | dada 8.8 × 9 × 4.8; bahu 5 × 3 × 5 ×2; rok zirah 9 × 5 × 5 | 7 | penjaga istana, kapten |
| `perisai_bundar` | lengan kiri | cakram 1 × 10 × 10 (12 sisi = 3 kubus diputar 0/±22.5) + umbo 1.5 × 3 × 3 | 6 kulit, 12 perunggu | PENJAGA_GERBANG |
| `topi_bulat_pedagang` | kepala | 8.4 × 3 × 8.4 + tepi 10 × 0.6 × 10 | 0 | BANKIR, PEMILIK_PENGINAPAN |
| `celemek_kulit` | badan | 8.2 × 10 × 0.6 depan (0, 4, −2.4) | 6 | EMPU, BANDAR_BATU, PANDAI_DESA |
| `sarung_tangan_empu` | lengan | 4.4 × 5 × 4.4 ×2 di pergelangan | 6 | EMPU |
| `kacamata_batu` | kepala | dua lensa 2 × 2 × 0.6 + tali 9 × 0.6 × 9 | 12 perunggu, 15 kasa | PEMOTONG_BATU |
| `topi_alkemis` | kepala | kerucut bertingkat 7 × 3 × 7 + 4 × 3 × 4 + bola 2³ | 17 sutra ungu | ALKEMIS |
| `topi_sarjana` | kepala | 8 × 3 × 8 + papan atas 9 × 0.6 × 9 + dua pita 1 × 6 × 0.4 | 0 hitam | PUSTAKAWAN, PENCERAMAH_FANA |
| `jubah_trigram` | badan | jubah 8.6 × 13 × 4.6 dengan tekstur trigram | 18 biru tua | PENJAGA_FORMASI |
| `topi_nakhoda` | kepala | tepi 12 × 0.6 × 12; puncak 7 × 3 × 7 | 5 jerami gelap | NAKHODA |
| `kain_kepala` | kepala | 8.6 × 2 × 8.6 + simpul 2 × 2 × 2 belakang | 9 kain | KULI, TUKANG, PEMOTONG_BATU |
| `sabuk_alat` | badan | sabuk 8.8 × 1.5 × 4.8; kantong 2 × 3 × 1.5 ×3 | 6 | TUKANG |
| `topi_koki` | kepala | 8 × 4 × 8 kain + lipatan 9 × 1 × 9 | 4 putih | KOKI |
| `tasbih` | badan | 10 manik 1 × 1 × 1 melingkar di dada (rotasi 22.5) | 8 kayu | RAHIB, PERTAPA |
| `janggut_putih_panjang` | kepala | 4 × 8 × 1 (0, 3, −4.2) + kumis 6 × 1 × 1 | 4 | PERTAPA, TETUA_DESA (kakek) |
| `sanggul_nenek` | kepala | 4 × 3 × 3 belakang + tusuk 5 × 0.5 × 0.5 | 14, 2 | TETUA_DESA (nenek) |
| `caping` | kepala | kerucut 3 tingkat 12 × 1 × 12, 8 × 1.5 × 8, 4 × 1.5 × 4 | 5 jerami | PETANI, NELAYAN, KEPALA_DESA |
| `keranjang_punggung` | badan | 7 × 9 × 5 belakang (0, 2, 4.5) + tali 1 × 9 × 0.5 ×2 | 5 | PEDAGANG_KELILING |
| `ikat_rambut_anak` | kepala | dua cepol 2.5³ (±2.5, −8.5, 1) | 14 | ANAK |
| `jubah_lambang_sekte` | badan | jubah 8.6 × 13 × 4.6; panel lambang 4 × 4 × 0.1 di dada (decal dari `Lambang`) | 19 putih (diwarnai sekte) | MURID_SEKTE_*, TETUA_SEKTE, JUARA_SEKTE |
| `jubah_iblis` | badan | jubah 8.8 × 14 × 4.8; kerah tinggi 9 × 3 × 5; tengkorak pinggang 2³ | 20 hitam-merah, 4 tulang | TETUA_IBLIS |
| `kain_compang` | badan | 8.4 × 11 × 4.4 bertekstur sobek (alpha) | 21 | TAWANAN, PENGEMIS |
| `topi_bulu` | kepala | 9 × 4 × 9 bulu + ekor 2 × 2 × 6 | 22 bulu | PEDAGANG_BINATANG |
| `kipas_lipat_pinggang` | badan | 1 × 6 × 0.5 terselip | 13 | TUAN_MUDA, PENCERAMAH_FANA |
| `lencana_bintang` | badan | 2 × 2 × 0.3 di dada (tekstur 1-9 bintang) | 1 | KETUA_GILDA, PETUGAS_GILDA |

#### 38.O.2 Makhluk dan entitas bergerak (model entitas, 38MD; perilaku server oleh modul pemilik)

| Entitas (id) | Pemilik server | Bagian model (px) | Tekstur | Animasi |
|---|---|---|---|---|
| `kultivator:penjaga_kristal` (Penjaga Kristal) | 38GU | badan gugus 10 × 14 × 8 (pivot pinggang); kepala prisma 6 × 7 × 6 diputar 45°; 6 pecahan 2 × 6 × 2 di bahu dan punggung (rotasi ±22.5); lengan 4 × 14 × 4 dengan tinju kristal 6³; kaki 4 × 10 × 4; tinggi 2.4 blok | 128 × 64 `penjaga_kristal.png`: kristal ungu bertingkat 5 warna + lapisan pancar (emissive) | diam: melayang LIFT ±0.05 periode 60, pecahan berputar mengorbit 2°/tick; jalan: langkah berat 30 tick per langkah + guncang kamera klien 0.1 bila ≤ 8 blok; serang: kedua tinju naik (t0-t10, −150°) lalu hantam (t10-t14, easeOutBack, partikel kristal); mati: pecahan berhamburan (partikel), tubuh tenggelam 20 tick |
| `kultivator:boneka_penjaga` (Boneka Penjaga makam) | 38GU | humanoid kaku: kepala bertopeng 8 × 8 × 8 + jambul 2 × 3 × 2; badan 8 × 12 × 4 dengan cincin perunggu; sendi bola 3³ di bahu, siku, lutut; lengan atas/bawah 3 × 6 × 3; pedang kayu 1 × 14 × 2 | 128 × 64: kayu tua + perunggu + topeng merah-putih | diam: kepala menyentak 15° tiap 40 tick; jalan: langkah patah-patah (ayunan dikuantisasi 4 langkah); serang: tebas 3 kunci (t0 −120, t6 −20 easeOutBack, t14 kembali); bangkit: dari duduk bersila 30 tick (kunci per sendi) saat uji dimulai |
| `kultivator:gerbang_kota` (daun gerbang) | 38KS | dua daun, masing-masing (lebar gerbang/2 × tinggi × 4) px dikalikan dari data jangkar; paku emas 1.5³ grid 4 × 6; palang 2 × 2 × lebar | 128 × 128: kayu merah lak + besi + paku emas | buka/tutup: daun berputar 0 ↔ 92° pada engsel luar, 24 tick, `easeInOutSine`, debu partikel di kaki saat mulai; roboh (Gelombang Binatang): satu daun miring 12° dan bergetar (TREMBLE), tidak ada blok berubah |
| `kultivator:gerobak` (gerobak kafilah) | 38EK | bak 24 × 6 × 40; dinding 1 × 6 × 40 ×2; roda 2 × (2 × 18 × 18) dari 3 kubus berputar 0/30/60° + jari 1 × 16 × 1 ×4; galah 1 × 1 × 30 ×2; muatan varian `peti` (3 kubus 10³), `karung` (4 × 8 × 6), `guci` (6 × 10 × 6 + mulut 4 × 2 × 4) | 128 × 128 kayu muda + besi | roda berputar `jarak / 0.56 rad`; bak bergoyang ±1.5° saat jalan; tarik kuda vanilla (tidak disimpan) |
| `kultivator:rakit` (Rakit Penyeberangan) | 38W | 7 batang 4 × 4 × 48 berjajar; palang 2 × 2 × 30 ×2; galah 1 × 1 × 48 | 64 × 64 bambu | terombang-ambing LIFT ±0.04 periode 50, rotasi Z ±1.2°; adegan dayung: galah tusuk-tarik 20 tick |
| `kultivator:sangkar` (pintu sangkar tawanan) | 38GU | rangka 16 × 24 × 1 dengan 6 jeruji 1 × 22 × 1 + gembok 2 × 3 × 1 | 64 × 64 besi berkarat | terbuka: gembok jatuh (8 tick) lalu daun berayun 0 → 100° (20 tick) |
| `kultivator:lempeng_glif` | 38GU | lempeng 14 × 2 × 14 + bingkai 16 × 1 × 16; glif timbul 1 tebal | 64 × 64 batu + 6 glif (unsur, pancar) | diputar 90° per klik (10 tick, easeOutBack); benar → glif menyala (lapisan pancar memudar masuk 20 tick) |
| `kultivator:pintu_tebing` | 38GU | batu palsu 16 × 32 × 4 dengan retakan | 64 × 64 meniru `stone` + retak | geser ke samping 24 tick (pola `KotaPintuEntity`), debu |
| `kultivator:tikus_roh` (tikus roh dapur, quest `tikus_dapur`) | 38GU | badan 6 × 4 × 10, kepala 4 × 4 × 4 + telinga 2 × 2 × 0.5, ekor 1 × 1 × 8 (2 segmen), kumis quad | 64 × 32 abu kebiruan + mata pancar | lari zig-zag, ekor bergoyang sinus; tidak merusak blok, tidak berkembang biak, lenyap 60 s setelah quest |
| `kultivator:papan_hidup` (papan kabar, poster buronan, papan misi) | 38UI (isi), 38MD (model) | bingkai 32 × 20 × 2 + atap kecil 36 × 2 × 6 | kayu tua + kertas | kertas berkibar sedikit (vertex sinus, ≤ 32 blok); kertas baru "ditempel" (skala 0 → 1, 6 tick) saat misi diperbarui; isi teks digambar klien (`Font` di dunia, 38.U) |

#### 38.O.3 Dekorasi festival dan dunia (klien saja)

| Model | Spesifikasi | Animasi | Anggaran (Min/Nor/Maks) |
|---|---|---|---|
| Lentera langit | badan kertas 6 × 8 × 6 px (5 kubus: 4 dinding + atas) + api dalam quad pancar 2 × 3 | naik 0.03 blok/tick, ayun ±6° periode 70, redup setelah 600 tick | 15 / 60 / 150 |
| Lentera tangan | kerangka bambu 4 × 6 × 4 + kertas merah + gagang 0.5 × 8 × 0.5 | ayun ±10° ikut langkah | prop (38.O.4) |
| Lentera jalan festival | lentera bulat 8 × 7 × 8 merah dengan rumbai 1 × 4 × 1, digantung di titik `jalan_lentera` | ayun angin ±4° | 12 / 40 / 80 |
| Kembang api | sprite quad 16 × 16 bintang + jejak (atlas 8 frame) | ledakan 24 tick, warna dari palet festival | 2 / 6 / 12 per 20 s |
| Armilari Panggung Bintang | 3 cincin (12 kubus 1 × 1 × 6 tiap cincin membentuk lingkaran berdiameter 40 px) + bola pusat 6³ | cincin berputar 0.6, −0.4, 0.25°/tick pada sumbu berbeda | 1 per situs |
| Roda kincir air | roda 6 sudu 2 × 24 × 6 diputar 0/30/60/90/120/150° | 6°/tick | 1 per kincir ≤ 48 blok |
| Panji kota / tenda lapak | quad kain 8 segmen dengan gelombang sinus vertex (pola bendera 36 S, implementasi sendiri `KainKota`) | gelombang 1.4 Hz | ≤ 24 per ibu kota |
| Asap dupa | 3 quad asap bertumpuk naik dan memudar | 40 tick siklus | 0 / 2 / 4 per pembakar |
| Gelembung kalimat dan papan nama toko | panel 9-slice 38.U + teks | muncul skala 0 → 1 (4 tick) | ≤ 6 gelembung |

#### 38.O.4 Prop tangan dan pajangan (model item JSON, `kultivator:fx/tempat/<id>`)

| Indeks | Id | Elemen (ringkas, px) | Dipakai klip |
|---|---|---|---|
| 1 | `mangkuk` | mangkuk 6 × 3 × 6 (cincin 4 kubus) + isi nasi 5 × 1 × 5 | `makan` |
| 2 | `sumpit` | 2 batang 0.5 × 0.5 × 9 | `makan` |
| 3 | `cangkir` | 3 × 3 × 3 tanpa telinga | `minum_teh` |
| 4 | `lepek` | 4 × 0.5 × 4 | `minum_teh` |
| 5 | `teko` | badan 5 × 4 × 5, tutup 3 × 1 × 3, cerat 1 × 1 × 3 (rot 22.5), pegangan 1 × 3 × 1 | `tuang_teh` |
| 6 | `sendok_sayur` | mangkuk 2 × 1 × 2 + gagang 0.5 × 0.5 × 9 | `masak` |
| 7 | `sapu` | gagang 1 × 18 × 1 + ijuk 5 × 6 × 2 | `sapu` |
| 8 | `palu` | kepala 3 × 3 × 6 + gagang 1 × 10 × 1 | `palu_landasan`, `perbaiki_palu` |
| 9 | `alu` | 1.5 × 9 × 1.5 | `tumbuk_herba` |
| 10 | `kuas` | 0.6 × 7 × 0.6 + bulu 1 × 2 × 1 | `menulis` |
| 11 | `kitab` | sampul 6 × 1.5 × 8 + jilid tali | `membaca`, `rak_susun` |
| 12 | `sempoa` | bingkai 9 × 5 × 1 + 7 batang + 35 manik (digabung 7 elemen bertekstur) | `hitung_sempoa` |
| 13 | `tongkat` | 1 × 18 × 1 + kepala bengkok 2 × 2 × 3 | `jalan_tua` |
| 14 | `peti` | 8 × 6 × 6 + pita besi | `jalan_bawa`, `angkat_bawa` |
| 15 | `pikulan` | galah 1 × 1 × 24 + 2 keranjang 5 × 4 × 5 | `jalan_pikul` |
| 16 | `joran` | 0.6 × 0.6 × 24 bertahap 3 kubus menipis | `memancing` |
| 17 | `cangkul` | gagang 1 × 14 × 1 + mata 4 × 1 × 3 | `mencangkul` |
| 18 | `keranjang` | 6 × 5 × 6 anyaman + pegangan | `memanen` |
| 19 | `ember` | 4 × 5 × 4 + pegangan 4 × 0.5 × 0.5 | `angkat_air`, `perbaiki_plester` |
| 20 | `dupa` | 3 batang 0.4 × 6 × 0.4 + ujung bara pancar | `sujud_dupa` |
| 21 | `pemukul` | 1 × 9 × 1 + bola kain 2³ | `genderang` |
| 22 | `palu_kayu` | 3 × 2 × 2 + gagang 1 × 5 × 1 | `palu_hakim` |
| 23 | `kipas_lipat` | 7 bilah 0.3 × 7 × 1 dirotasi kipas (maks 40 elemen) | `kipas` |
| 24 | `papan_catur` | 8 × 0.6 × 8 bertekstur 9 × 10 garis + 6 bidak 1 × 0.6 × 1 | `catur` |
| 25 | `payung_kertas` | 8 rusuk 0.3 × 0.3 × 9 (rot 45) + kanopi 4 kubus pipih 12 × 0.5 × 12 bertingkat + gagang | cuaca hujan |
| 26 | `gasing` | 2³ + paku 0.5 × 1 × 0.5 | `main_gasing` (berputar 30°/tick klien) |
| 27 | `layang_layang` | rangka 2 batang + kain 8 × 0.2 × 10 + ekor 3 quad | `main_layangan` (terbang di ujung tali quad) |
| 28 | `lentera_tangan` | lihat 38.O.3 | festival |
| 29 | `kantong_uang` | 3 × 3 × 3 + ikat | transaksi |
| 30 | `nampan` | 8 × 0.5 × 6 + 2 cangkir | `PELAYAN` |
| 31 | `sendok_plester`, 32 `papan_kayu` (12 × 1 × 3 ×3 bertumpuk), 33 `sekop`, 34 `bibit` (pot 3³ + tunas) | | 38.R |
| 35 | `tombak` | 1 × 34 × 1 + mata 1 × 5 × 2 + rumbai 2 × 2 × 2 | penjaga |
| 36 | `kain` | 6 × 0.5 × 8 terlipat | `jemur` |
| 37 | `teropong` | 2 × 2 × 7 dua ruas | NAKHODA |
| 38 | `labu_pil` | 3³ + 2³ + tutup | ALKEMIS |
| 39 | `pahat` | 0.6 × 6 × 1 | PEMOTONG_BATU |
| 40 | `batu_mentah_s/m/l/xl` | gumpal 4 kubus diputar, kulit batu kasar 4 varian + urat cahaya pancar tipis | judi batu (di meja dan saat dipotong: model terbelah dua bagian 2 × 8 tick) |

**Pajangan senjata** (`kotabesar/rak_senjata`): bukan model palsu, melainkan **ItemStack stok sungguhan** (38.M.2.1) yang
digambar klien sebagai `ItemDisplay` (DisplayKey, dipose miring 30° di rak, berputar 0.5°/tick bila disorot); bila stok
habis, rak kosong dengan tanda "Habis" (38.U).

**Senjata kota baru** (item, 38EK; model item 38MD; tekstur 32 × 32): `kultivator:senjata/jian_<mutu>` (pedang lurus:
bilah 1 × 22 × 2 + pelindung 6 × 1 × 2 + gagang 1 × 6 × 1 + rumbai), `dao_<mutu>` (golok melengkung: 3 segmen bilah
diputar 0/−11.25/−22.5), `qiang_<mutu>` (tombak panjang: model tangan dipanjangkan 1.4×), `kapak_<mutu>`, `gada_<mutu>`
(bola bersirip 6 kubus), `kipas_besi_<mutu>` (kipas berbilah logam: 9 bilah), `kebut_<mutu>` (gagang 1 × 10 × 1 + ekor
kuda 12 helai quad). Mutu: `fana`, `roh_rendah`, `roh_menengah`, `roh_tinggi` → empat tekstur bilah (besi, besi roh
kebiruan, perak berurat, giok berpendar). Total 7 bentuk × 4 mutu = 28 model, 28 tekstur.

#### 38.O.5 Generator, berkas dan uji model

- `tools/gen_tempat_models.py` (38MD): semua prop, senjata kota, makhluk (LayerDefinition ditulis tangan di Java; generator
  membuat teksturnya), wajah, impostor; `tools/gen_tempat_gear.py` (38MD): pita 16-31 atlas perlengkapan (memperluas
  `perlengkapan.png` dengan menyalin pita 0-15 byte demi byte lalu menambah; `gen_dunia_kota_gear.py` lama tidak
  disunting); `tools/gen_tempat_textures.py` (38MD): lentera, kembang api, asap, kain, glif, kristal.
- Uji (`/kultivasi uji model ...`, op): `pamer` (barisan semua makhluk 38 di depan pemain, AI mati, 30 s), `prop` (24 NPC uji
  memegang prop 1-40 berurutan), `gear <peran>` (NPC uji berperlengkapan peran), `wajah` (siklus mata/mulut),
  `senjata` (rak uji berisi 28 senjata kota), `festival` (semua dekorasi festival 20 s, klien).

---------------------------------------------------------------------------------------------------------------------

### 38.S Cerita, tokoh, dialog, quest dan rumor (modul 38ST)

#### 38.S.1 Mesin cerita (data-driven)

- **Dialog** `data/kultivator/tempat/dialog/<id>.json`: simpul `{id, pembicara (peran atau tokoh), teks (kunci lang),
  gestur (klip 38.N.5), suara? (kunci voice), pilihan: [{teks, syarat[], akibat[], ke}]}`. **Syarat**: `nama_baik ≥ n`,
  `ranah ≥ r`, `bendera <id>`, `punya <item> <n>`, `nb ≥ n`, `quest <id> tahap <k>`, `hari ≥ d`, `sikap_sekte <s> ≥ X`,
  `takut ≥ n` (38.R.5), `jam a-b`. **Akibat**: `bendera`, `nama_baik ±n`, `beri <item>`, `ambil <item>`, `bayar <nb>`
  (lewat `NilaiBatu.pay`), `quest_mulai <id>`, `quest_maju <id>`, `buka_layanan <layar>`, `mulai_duel <aturan>`,
  `klip <id>`, `adegan <id>`, `kabar <templat>`. Kunci lang `kultivator.cerita.<dialog>.<simpul>[.<pilihan>]`.
- **Quest** `data/kultivator/tempat/quest/<id>.json`: tahap berurutan dengan cabang; tujuan: `pergi <jenis situs>`
  (tempat nyata terdekat yang diketahui atau dicari `DuniaSites.locateFor`), `bicara <tokoh|peran@situs>`, `kalahkan <tag
  entitas> n`, `antar <item> ke <tokoh>`, `kawal <entitas> ke <situs>`, `tunggu <hari>`, `duel <tokoh>`, `racik <pil>`,
  `tempa <bentuk> mutu ≥ k`, `donasi <nb>`, `bebaskan tawanan n`. Batas waktu opsional; gagal → cabang gagal (bukan buntu).
- **Kelas** (`dunia/tempat/cerita/**`, 38ST): `CeritaData` (SavedData `kultivator:tempat_cerita`: bendera per pemain,
  quest per pemain, alur ibu kota per situs), `Dialog`, `DialogNode`, `Syarat`, `Akibat`, `Quest`, `QuestTracker`
  (memeriksa tujuan dari event: `DuniaEvents`, `ServerLivingEntityEvents.AFTER_DEATH`, kait selesai alkimia/tempa 38EK,
  `SITE_DISCOVERED`), `Tokoh` (tokoh berulang), `KabarTempat` (rumor), `CeritaReload` (pemuat data, `ResourceLoader`
  reload listener, verifikasi API 26.3).
- **Jurnal** di layar 38.U ("Catatan Perjalanan"): quest aktif, tahap, penunjuk arah (jarum ke tujuan), ringkasan cerita
  yang sudah terjadi. Semua teks Indonesia (utama) dan Inggris dengan kunci identik.

#### 38.S.2 Tokoh berulang (nama tetap per ibu kota pertama; ibu kota lain memakai generator nama dengan watak yang sama)

| Tokoh | Peran | Tempat | Watak dan suara | Muncul di |
|---|---|---|---|---|
| **Yan Wushuang** | PANGERAN_KOTA | istana | tenang, lelah oleh jabatan, rindu bertapa; `boss` | alur ibu kota bab 1, 4, 7 |
| **Yan Ruyi** | pewaris (PANGERAN_KOTA varian muda) | istana, kuil | lembut, keras kepala soal keadilan, kultivasi lemah; `f2` | bab 3-7 |
| **Gu Lianshan** | MENTERI | istana, pasar | cerdas, pandai bicara, dicintai pedagang; ambisius tetapi bukan iblis; `m2` | bab 4-7, Lelang Agung |
| **Mo Ying** | ajudan Gu (MENTERI varian) → TETUA_IBLIS | istana → sarang iblis | sopan berlebihan, mata tak pernah tersenyum; `m3` | bab 2-6 (dalang) |
| **Bao Qing** | HAKIM | yamen | adil, keras, humor kering; `elder` | hukum, bab 1, 5 |
| **Tie Hu "Harimau Besi"** | KAPTEN_PENJAGA | gerbang, yamen | jujur, kaku, takut pada atasannya; `m1` | penjaga, Gelombang Binatang, bab 6 |
| **Nyonya Qian Duoduo** | BANKIR | rumah uang | ramah, menghitung segalanya, setia pada kontrak; `f3` | bank, quest "Wesel Palsu" |
| **Lao Shi Si Mata Satu** | BANDAR_BATU | rumah judi | licik, berkode kehormatan, benci penipu; `m3` | judi, quest "Batu Bermata Darah" |
| **Ou Ye Muda** | EMPU | gilda tempa | perfeksionis, irit kata; `m1` | toko senjata, pesanan, quest "Pedang Tanpa Nama" |
| **Tetua Dan Chen** | KETUA_GILDA (alkemis) | gilda alkemis | sombong pada pemula, hangat pada yang berbakat; `elder` | ujian alkemis, Pertemuan Pil, bab 3 |
| **Lin Fei Si Lengan Satu** | KETUA_GILDA (kultivator) | gilda kultivator | mantan pemburu, blak-blakan; `f2` | misi E-S, Daftar Naga Muda |
| **Xiao Mei** | PETUGAS_GILDA | gilda kultivator | ceria, suka bergosip; `f1` | papan misi, rumor |
| **Feng Ao** | PENGUJI → saingan | gilda, arena | muda, sombong, berbakat; saingan berulang pemain di Daftar Naga Muda, turnamen, misi bersaing; `m3` | gilda, turnamen, jalan |
| **Tuan Wen** | PUSTAKAWAN | perpustakaan | pelupa, ensiklopedis; `elder` | perpustakaan, kuliah, quest arsip |
| **Biksu Jing Xin** | RAHIB | kuil | sabar, bijak; `elder` | kuil, Cermin Hati, bab 5 |
| **Bibi Hong** | PEMILIK_PENGINAPAN | penginapan | cerewet, keibuan; anaknya merantau ke sekte; `f1` | penginapan, quest "Surat untuk Ah Lun" |
| **Gendut Zhu** | KOKI | rumah makan | riang, takut tikus; `m2` | rumah makan, festival |
| **Ma Laoliu** | PEDAGANG_BINATANG | pasar binatang | sayang binatang, benci pemburu liar; `m3` | pasar binatang |
| **Kapten Yun Fei** | NAKHODA | dermaga | petualang, pembual; `m2` | dermaga, Perjalanan Awan |
| **Kakek Lumpur** | PENGEMIS (ahli tersembunyi) | kumuh | malas bicara; sesungguhnya kultivator Penyatuan Dao yang menyepi (kiasan "pengemis sakti"); `elder` | kumuh, bab 2, 6 |
| **Si Kecil Dou** | ANAK | kumuh | yatim lincah, pembawa kabar; `f1` (nada tinggi) | kumuh, bab 2 |
| **Lao Qiao** | PEDAGANG_KELILING | jalan, pos, kampung | pedagang keliling yang muncul di banyak tempat; tahu semua jalan; `m2` | pos, kampung, quest "Kafilah Terakhir" |

Desa: Kepala Desa, Patriark dan Tuan Muda klan, pertapa, sisa jiwa dan tetua sekte memakai **generator nama**
(`TempatNames`, marga 32 × nama 48) dengan watak dari tabel 8 watak (`ramah, curiga, serakah, bijak, penakut, sombong,
jujur, licik`) yang menentukan cabang dialog.

#### 38.S.3 Alur cerita ibu kota: "Bayangan di Balik Takhta"

Satu alur per ibu kota (keadaan di `CeritaData.alur.<situs>`, dibagi semua pemain; partisipasi per pemain). Berjalan
lintas **±24 hari dunia**; tidak pernah buntu: bila pemain tidak ikut, setiap bab maju sendiri setelah batas waktunya
dengan hasil bawaan (dikabarkan lewat rumor).

| Bab | Pemicu | Isi dan tujuan | Cabang | Hadiah |
|---|---|---|---|---|
| 1 Kedatangan | pemain pertama masuk ibu kota | Tie Hu menyambut di gerbang; Hakim Bao menempel pengumuman "Orang hilang di Kampung Kumuh" | — | situs ibu kota, Nama Baik +2 |
| 2 Hilang di Kumuh | hari ≥ +1 | bicara dengan Si Kecil Dou dan Kakek Lumpur; 3 petunjuk (sandal berdarah di gang, kertas jimat hitam, saksi pengemis); jejak ke **Sarang Kultivator Iblis** terdekat yang dikenal (bila tak ada, `locateFor gua/sarang_iblis` 64 chunk) | bebaskan tawanan → bukti 1 "Cap Bayangan" | Nama Baik +5, 3 × (band + 1) × 20 NB |
| 3 Pewaris Sakit | hari ≥ +4 | Yan Ruyi pingsan diracun; Tetua Dan Chen minta **Pil Penawar Racun Darah** (resep diberi gilda; bahan Air Mata Air Roh dari situs `mata_air_roh`) atau ambil Rumput Bintang di Gua Urat Roh | racik sendiri (gilda + Tingkat Alkemis +1) / beli dari Pasar Gelap (cepat, Nama Baik −3, bukti hilang) | gelar "Penyelamat Pewaris" |
| 4 Dua Calon | hari ≥ +8 | Pangeran Yan ingin menyepi; calon: Ruyi (adil, lemah) dan Gu Lianshan (cakap, disukai pedagang). Pemain menggalang dukungan: 3 dari 5 kepala gilda/tokoh (Dan Chen, Lin Fei, Qian Duoduo, Ou Ye, Bao Qing) lewat quest kecil masing-masing | dukung Ruyi / dukung Gu / netral | standing gilda (Nama Baik +3 per tokoh) |
| 5 Topeng Terlepas | hari ≥ +12 dan bukti 1 ada | bukti 2 (catatan pembukuan di Rumah Uang, quest Qian) + bukti 3 (mata-mata di Kuil, Biksu Jing Xin + uji di Gua Cermin Hati): Mo Ying, ajudan Gu, adalah tetua iblis yang meracun Ruyi tanpa sepengetahuan Gu | (a) bawa 3 bukti ke Hakim Bao → sidang terbuka (adegan Yamen, palu hakim) → Mo Ying kabur; (b) hadapi Mo Ying sendiri → Duel (bos `TETUA_IBLIS`, ranah = band pemain + 1) di gudang Distrik Awan; (c) diam dan terima suap Mo Ying 300 × (band + 1) NB | (a) Nama Baik +10; (b) pusaka Mo Ying; (c) uang, bendera `bersekongkol` |
| 6 Malam Gerhana | hari ≥ +16 (atau Gerhana Darah berikutnya) | sekte iblis Mo Ying (sekte NPC beraliran Iblis dari `GerbangPlan` bila ada, selain itu band kultivator nakal) menyerang bersamaan dengan Gelombang Binatang khusus; Kakek Lumpur muncul membantu di gelombang ketiga bila pemain menolong Dou di bab 2 | (c) di bab 5 → serangan lebih berat (+1 gelombang) dan Kakek Lumpur tidak datang | Nama Baik +10, harta tier tinggi |
| 7 Penobatan | hari ≥ +20 | upacara di Alun-alun Agung (adegan, penduduk berkumpul, lentera siang), pemenang sesuai dukungan bab 4 dan hasil bab 5-6 | Ruyi naik: harga normal, penjaga ramah, denda ×1.0; Gu naik: stok toko +2, denda ×1.25, Pasar Gelap dirazia lebih sering | gelar "Penjaga Takhta", Lencana Istana (item), audiensi tanpa syarat Nama Baik |

- Batas waktu default: bab 2-6 masing-masing 6 hari dunia; tanpa pemain → Mo Ying tetap tersembunyi, Gu dinobatkan di hari
  +28, rumor "Pewaris Yan sakit-sakitan".
- Adegan (`SekteScenes`/`DuniaScenes` pola, ≤ 4 s, 7b): sidang Yamen, Ruyi pingsan, topeng Mo Ying pecah, penobatan.
- Sumber (`kultivator.cerita.takhta.inspired`): "Terinspirasi dari kisah hakim Bao Zheng (dinasti Song) yang membongkar
  kejahatan pejabat, dan dari kiasan novel xianxia tentang mata-mata jalan iblis di istana; tokoh dan alurnya rancangan
  game."

#### 38.S.4 Rantai quest tempat (contoh lengkap + daftar)

**Contoh dialog lengkap** (`dialog/bibi_hong_ah_lun.json`, Indonesia / English):

```
[mulai] Bibi Hong (tertawa→cemberut): "Kamar? Ada. Tapi... Tuan Kultivator, boleh minta tolong sebentar?"
        / "A room? Of course. But... honoured cultivator, may I ask a small favour?"
  > "Ada apa, Bi?" / "What is it, auntie?"                                 → [cerita]
  > "Aku sibuk." / "I'm busy."                                             → [akhir_sibuk] (bendera hong_tolak)
[cerita] (bicara_tangan) "Anakku Ah Lun masuk sekte di gunung timur tiga musim lalu. Tak ada surat sejak itu."
        / "My son Ah Lun joined a sect in the eastern mountains three seasons ago. Not a letter since."
  > "Sekte mana?" / "Which sect?"            [syarat: ada kompleks sekte dikenal]   → [sekte]
  > "Akan kucari." / "I will look for him."  [akibat: quest_mulai surat_ah_lun]    → [terima]
[sekte] "Katanya <nama sekte terdekat>. Bawa surat ini, ya?" [akibat: beri surat_bibi_hong, quest_mulai surat_ah_lun]
[terima] (hormat) "Semoga langit membalas kebaikanmu! Kamar malam ini gratis." [akibat: bendera inap_gratis_1]
[akhir_sibuk] (kipas, menghela napas) "Ya sudah... kamar tiga NB semalam."
```

Quest `surat_ah_lun`: (1) pergi ke kompleks sekte (sikap ≥ NETRAL) → bicara MURID_SEKTE_LUAR "Ah Lun" (penduduk data
bernama, dijamin ada di kompleks terdekat); (2) cabang: Ah Lun bahagia (bila sekte Lurus/Netral) → bawa balasan → Bibi
Hong menangis bahagia (klip `tertawa` + `hormat`), hadiah hidangan istimewa + kamar gratis 7 hari; atau Ah Lun ditahan sekte
Iblis (sekte Iblis) → bebaskan lewat duel dengan Tetua sekte atau bayar tebusan 100 × (band + 1) NB → Ah Lun pulang dan
menjadi PELAYAN di penginapan (penduduk baru).

**Daftar rantai** (setiap rantai 2-5 tahap, ≥ 1 cabang; lang di `tempat_cerita.*`):

| Id | Pemberi | Ringkas | Tempat yang dipakai |
|---|---|---|---|
| `tungku_pertama` | Tetua Dan Chen | pengantar ujian Tingkat Alkemis 1 | Gilda Alkemis |
| `resep_hilang` | Tetua Dan Chen | halaman resep kuno di Makam Warisan; cabang: serahkan ke gilda (Tingkat +1) atau simpan (resep pribadi) | makam warisan |
| `pedang_tanpa_nama` | Ou Ye Muda | kumpulkan Kristal Urat (Gua Urat Roh), tempa bersama Empu, celup di Mata Air Roh → pedang jian unik "Tanpa Nama" (mutu roh tinggi, bisa diberi nama pemain) | gua urat roh, mata air roh, gilda tempa |
| `daftar_naga_muda` | Lin Fei | tiga pertemuan dengan Feng Ao (misi bersaing, duel di arena, turnamen); cabang: kalahkan dengan hormat (Feng Ao jadi sahabat, membantu di bab 6) atau mempermalukan (Feng Ao dendam, muncul sebagai lawan di jalan) | gilda, arena, jalan |
| `wesel_palsu` | Qian Duoduo | surat wesel palsu beredar; lacak ke Pasar Gelap; cabang: serahkan pemalsu ke Yamen atau peras dia | rumah uang, kumuh, yamen |
| `batu_bermata_darah` | Lao Shi | batu mentah terkutuk menyedot jiwa pemotong; bawa ke Kuil; uji Cermin Hati kecil | judi, kuil, cermin hati |
| `lonceng_retak` | Biksu Jing Xin | lonceng kuil retak setelah gempa (kerusakan 38.R pada lot kuil): kumpulkan perunggu, bantu tukang | kuil, 38.R |
| `surat_ah_lun` | Bibi Hong | lihat contoh di atas | penginapan, kompleks sekte |
| `tikus_dapur` | Gendut Zhu | tikus roh di gudang (monster kecil, `silverfish` **tidak**: memakai `kultivator:tikus_roh` kosmetik 38MD tanpa merusak blok) | rumah makan |
| `binatang_curian` | Ma Laoliu | anak rubah roh dicuri pemburu liar; lacak ke `pos/kemah_begal` | pasar binatang, jalan |
| `peta_awan` | Kapten Yun Fei | antar barang ke istana langit sekutu (37) atau ke dermaga ibu kota lain | dermaga, 37 |
| `kafilah_terakhir` | Lao Qiao | kawal kafilah dari pos dagang ke kota lewat jalan berbegal | jalan, pos, kota |
| `gagal_panen` | Kepala Desa | ladang layu: ambil Air Roh atau usir binatang dari Sarang Binatang terdekat; cabang pengikraran desa | kampung, sarang binatang |
| `anak_berbakat` | Kepala Desa | anak berakar roh: bawa ke sekte pemain (calon) atau ke sekte NPC (standing +5) | kampung, aula pemain |
| `tuan_muda_sombong` | Tuan Muda klan | Tuan Muda menantang; cabang: kalahkan (Patriark menghormati, pengikraran terbuka) atau mengalah (klan meremehkan, harga +10 %) | desa klan |
| `murid_kabur` | Tetua Sekte NPC | murid sekte kabur membawa kitab; temukan di pos singgah; kembalikan atau lindungi dia (calon untuk sekte pemain, standing −5) | kompleks, pos |
| `tiga_ujian_pertapa` | Pertapa | tiga ujian (38.C.4) dengan 12 koan Dao (3 pilihan, satu "benar" menurut Dao pertapa) | gua pertapa |
| `warisan_<dao>_<tier>` | Sisa Jiwa | 9 Dao × 3 tier: kisah sang leluhur (3 paragraf), ujian Hati menyesuaikan kisahnya | makam warisan |
| `tawanan_pulang` | tawanan | kawal pulang ke kampung asal (kampung terdekat); keluarga menyambut | sarang iblis, kampung |

#### 38.S.5 Rumor yang berubah mengikuti dunia (`KabarTempat`)

- Sumber peristiwa: `DuniaEvents` (`TREASURE_BORN/CLAIMED`, `SKY_EVENT`, `SEAL_CHANGED`, `BEAST_EVOLVED`,
  `SITE_DISCOVERED`), `SekteBus` (perang, pakta, turnamen), acara ibu kota, alur cerita (bab), perbuatan pemain (bos yang
  dikalahkan, kejahatan, donasi, tagihan yang dibayar/ditolak 38.R, juara), kerusakan tempat (38.R).
- Tabel templat (`data/kultivator/tempat/kabar/*.json`): `{id, pemicu, bobot, umur_hari, peran_penyebar[], teks (kunci),
  isian}`; disimpan maks 24 rumor hidup per situs + 12 global; dipakai oleh obrolan NPC (38.N.4), Kedai Teh (`KabarDunia`
  lama tetap), Papan Kabar ibu kota, Xiao Mei, Si Kecil Dou, Lao Qiao.
- Contoh (Indonesia): "Katanya cahaya pelangi turun di dekat <tempat>; tiga kelompok sudah berangkat." · "Sejak Gerhana
  Darah, pintu-pintu dikunci lebih awal." · "Tuan <pemain> membayar ganti rugi sampai kepingan terakhir. Orang jujur!" ·
  "Jangan dekati <pemain>... dia meratakan alun-alun dan tak ada yang berani menagih." (38.R takut) · "Sekte <A> dan <B>
  menandatangani pakta di Balai Pertemuan." · "Pewaris Yan sakit lagi..." (bab 3) · "Feng Ao naik ke peringkat tiga
  Daftar Naga Muda." · "Ada yang melihat orang berjubah hitam keluar-masuk Kampung Kumuh malam-malam." (bab 2) ·
  "Jembatan di jalan barat runtuh; tukang sedang memperbaikinya." (38.R) · "Pedagang keliling Lao Qiao bilang jalan ke
  <kota> sedang dibegal."

#### 38.S.6 Kalimat NPC dan suara baru (selain ibu kota 38.B.8)

- **Gelembung kalimat** (teks saja, tanpa suara): 12-30 kalimat per kelompok peran dan keadaan (`tempat_kalimat.*`):
  sapaan pagi/siang/malam, seruan dagang per toko, keluhan cuaca, reaksi takut, reaksi kagum pada ranah tinggi, komentar
  festival, bisikan tentang buronan. Total ±600 kunci (Indonesia + Inggris).
- **Suara Jepang baru** (38ST menambah ke `tools/gen_voices.py` `WORLD` dan grup `VoiceEvents`; grup `tempat.*`):

| Kunci | Suara | Jepang | English | Indonesia | Emosi |
|---|---|---|---|---|---|
| `tempat.village_elder` | elder | 旅のお方、村で少し休んでいきなされ。 | Traveller, rest a while in our village. | Pengembara, beristirahatlah sebentar di desa kami. | calm |
| `tempat.village_fear` | elder | ど、どうかお許しを…修理は我らがいたします。 | P-please forgive us... we will repair it ourselves. | M-mohon ampun... kami sendiri yang akan memperbaikinya. | fear (normal) |
| `tempat.bill_request` | m1 | 恐れながら…壊れた分の弁償をお願いしたい。 | With respect... we must ask you to pay for the damage. | Maaf... kami harus meminta ganti rugi atas kerusakannya. | normal |
| `tempat.bill_paid` | m1 | ありがたい。すぐに職人を集めます。 | Thank you. I'll gather the craftsmen at once. | Terima kasih. Akan segera kukumpulkan para tukang. | joy |
| `tempat.bill_refused` | m1 | …この恨み、町は忘れませんぞ。 | ...The town will not forget this. | ...Kota ini tidak akan melupakannya. | angry |
| `tempat.repair_work` | m2 | よいしょ！もう一枚！ | Heave! One more plank! | Hup! Satu papan lagi! | joy |
| `tempat.clan_patriarch` | boss | 我が一族の地に、何用か。 | What business brings you to my clan's land? | Ada urusan apa di tanah klanku? | calm |
| `tempat.young_master` | m3 | 身の程を知れ。この地で俺に逆らうとはな！ | Know your place. Defying me on my own land! | Tahu dirilah. Berani menentangku di tanahku sendiri! | angry |
| `tempat.hermit` | elder | 縁ある者よ、座るがよい。 | You who share a bond with me, sit. | Engkau yang berjodoh denganku, duduklah. | calm |
| `tempat.hermit_koan` | elder | 空の器にこそ、道は満ちる。 | Only an empty vessel can be filled with the Dao. | Hanya wadah yang kosong dapat diisi Dao. | calm |
| `tempat.inheritance` | heaven | 我が遺産を継ぐ資格、示してみよ。 | Show me you are worthy of my legacy. | Tunjukkan bahwa kau layak mewarisi peninggalanku. | normal |
| `tempat.inheritance_pass` | heaven | よかろう。我が道、汝に託す。 | Very well. I entrust my path to you. | Baiklah. Jalanku kuserahkan padamu. | calm |
| `tempat.demon_hideout` | demon | 生きて帰れると思うな！ | Don't think you'll leave alive! | Jangan kira kau bisa pulang hidup-hidup! | angry |
| `tempat.captive_saved` | f1 | 助かった…本当にありがとうございます！ | We're saved... thank you, truly! | Kami selamat... terima kasih, sungguh! | joy |
| `tempat.waystation` | m2 | 駅で一休みしていきな。水も飯もあるぞ。 | Rest a bit at the post. There's water and food. | Mampirlah istirahat di pos. Ada air dan makanan. | normal |
| `tempat.peddler` | m2 | 珍しい品だよ、遠い町から運んできたんだ！ | Rare goods, carried all the way from distant towns! | Barang langka, kubawa dari kota yang jauh! | joy |
| `tempat.bandit` | m3 | 通行料を置いていけ！ | Leave a toll if you want to pass! | Tinggalkan upeti kalau mau lewat! | angry |
| `tempat.tribulation_peak` | heaven | この峰は、幾多の劫を見届けてきた。 | This peak has witnessed countless tribulations. | Puncak ini telah menyaksikan tak terhitung kesengsaraan. | calm |
| `tempat.sect_visit` | m1 | 本宗に何のご用でしょうか。 | What brings you to our sect? | Ada keperluan apa ke sekte kami? | normal |
| `tempat.child_play` | f1 | 鬼さんこちら！ | Catch me if you can! | Ayo tangkap aku! | joy |
| `cerita.ruyi_thanks` | f2 | このご恩は、決して忘れません。 | I will never forget this kindness. | Budi ini takkan pernah kulupakan. | calm |
| `cerita.moying_reveal` | m3 | 気づくのが遅かったな…！ | You noticed too late...! | Kau terlambat menyadarinya...! | angry |
| `cerita.bao_verdict` | elder | 証拠は揃った。判決を下す！ | The evidence is complete. I pronounce the verdict! | Bukti sudah lengkap. Putusan dijatuhkan! | angry |
| `cerita.coronation` | boss | 今日より、この都を汝に託す。 | From this day, I entrust this capital to you. | Mulai hari ini, kota ini kuserahkan padamu. | calm |

#### 38.S.7 Lore tempat (ringkas; teks lengkap 2-4 paragraf per kunci di `tempat_lore.*`)

- **Ibu kota:** didirikan 900 tahun lalu oleh tujuh sekte yang lelah berperang sebagai "tanah netral"; Paviliun Pengawal
  Kota dipimpin Pangeran Penjaga yang dipilih dewan gilda; hukum pertama: "Di dalam tembok, pedang tidur."
- **Distrik:** Istana (dulu benteng tujuh sekte), Cendekia (perpustakaan menyimpan salinan kitab semua sekte, inti
  kenetralan), Awan (dermaga dibangun setelah kapal roh pertama), Gilda (tiga gilda bersaing sejak berdirinya), Pusat
  (Tugu Pendiri: tujuh pedang patah ditanam dalam batu), Pasar Timur (Rumah Uang lebih tua dari kota), Kumuh (pengungsi
  dari desa yang diserbu binatang), Arena (tempat sengketa sekte diselesaikan dengan adu, bukan perang).
- **Gilda:** Alkemis (rumus pertama Tetua Dan Chen menyembuhkan wabah), Pandai Pusaka (keturunan murid Ou Ye), Kultivator
  (didirikan pemburu binatang; lengan Lin Fei hilang dalam Gelombang Binatang besar dua puluh tahun lalu).
- **Kampung:** setiap kampung mendapat legenda kecil dari tabel (dewa tanah setempat, sumur keberuntungan, kisah banjir)
  dan sejarah hubungan dengan sekte terdekat.
- **Kompleks sekte:** legenda pendiri dari `GeneratorNama` sekte + tabel 12 kisah (mis. "Pendiri menebas gunung jadi dua
  dan membangun aula di celahnya").
- **Gua:** pertapa (alasan menyepi dari tabel 10), makam (kisah leluhur per Dao), sarang iblis (sekte asal), gua urat roh
  (mitos naga tidur), cermin hati (biksu yang melihat iblisnya sendiri).
- **Jalan:** tonggak bertuliskan jarak dalam li dan nama raja jalan kuno; kuil jalan untuk Dewa Tanah penjaga pengembara.

---------------------------------------------------------------------------------------------------------------------

### 38.R Perbaikan dan ganti rugi (modul 38PR)

Permintaan pengguna: "Kalau suatu area atau tempat diserang atau rusak, NPC memperbaikinya pelan-pelan; aku diminta
membayar ganti rugi, atau, karena mereka takut, mereka memperbaikinya sendiri tetapi lebih lama."

**Prinsip blok (mengikat):** semua penempatan blok perbaikan lewat **satu jalur yang sudah diizinkan**:
`kehancuran/WorldBreaker` mode restore (yang sudah ada: "a restore only puts back what was taken, where it is still air",
menunggu chunk termuat dan orang yang berdiri di blok). 38PR menambah **satu metode publik aditif**
`WorldBreaker.submitRestore(RestoreRequest)` (38.R.3) dan **satu pendengar** `WorldBreaker.addChangeListener(...)`
(atribusi Mode Kehancuran). `sect/build/engine/BuildPlacer` tidak dipakai (pemiliknya 37B, dan restore WorldBreaker sudah
memenuhi kebutuhan). Tidak ada `setBlock` baru di mana pun.

#### 38.R.1 Cetak biru tempat (apa yang "seharusnya")

- **Tempat yang dirawat:** struktur di tag `#kultivator:tempat_dirawat`: `kota_besar/*`, `kota/kota_kultivator*`,
  `kota/kota_abadi`, `kampung/*`, `kompleks_sekte/*`, `pos/singgah*`, `pos/dagang`, jembatan `jalan/jalan` (hanya potongan
  jembatan), `laut/pulau_pasar_*` (tempat modul D, hanya dibaca). Gua, situs dan reruntuhan **tidak** dirawat.
- **Volume:** gabungan kotak potongan `StructureStart` tempat itu (dibaca dari referensi struktur chunk lewat
  `level.structureManager()`, verifikasi), dipersempit ke kotak titik `bangunan` (38.B.2.5) bila templat punya; jalan dan
  halaman ikut (lapis permukaan saja, tinggi 3).
- **Rekam (bukan diturunkan dari templat):** templat + processor acak (lumut/retak `rule`, `block_rot`) + adaptasi tanah
  tidak bisa dihitung ulang dengan tepat dan murah; maka 38PR **merekam** blok sungguhan sekali: saat chunk yang memuat
  volume tempat dirawat dimuat (`ServerChunkEvents.CHUNK_LOAD`, Fabric) dan belum direkam, chunk masuk antrean rekam
  (`perbaikan.rekamPerTick` 2 chunk per tick). Rekaman = irisan volume ∩ chunk: palet (≤ 4096 state) + indeks `short[]`
  dengan RLE; untuk block entity hanya **jenisnya** (peti: `LootTable` bila belum pernah dibuka + bendera `kosong`;
  papan tulisan/panji: tag lengkap; lain: tidak).
- **Garis dasar jujur:** bila chunk sudah dihuni lama (`ChunkAccess.getInhabitedTime()` > 1200 tick) saat direkam (tempat
  lama di dunia lama), keadaan saat itu menjadi garis dasar (kerusakan lama dianggap bagian kota).
- **Simpan:** berkas sendiri `world/kultivator/cetak/<dimensi>/r.<rx>.<rz>.dat` (NBT gzip per 32 × 32 chunk; LRU 6 region
  di memori; ditulis saat `ServerLifecycleEvents` simpan/berhenti dan tiap 5 menit bila kotor; verifikasi API simpan
  Fabric). Perkiraan: ibu kota ±0.7 MB, kota ±0.2 MB, kampung ±0.05 MB.

#### 38.R.2 Deteksi dan atribusi

- **Pemindai** (`PindaiTempat`): untuk setiap tempat dirawat yang punya pemain dalam 64 blok, satu kolom chunk volume
  dibandingkan dengan rekaman tiap `perbaikan.pindaiTick` (40) tick, bergiliran, maks 4096 pembacaan blok per tick seluruh
  server. Perbedaan → entri `Kerusakan(pos, harapan, ditemukan, waktu, sebab, pelaku?)`. Tidak membaca chunk yang tidak
  termuat.
- **Log atribusi** (cincin 5 menit per tempat, memori):

| Sebab | Sumber peristiwa | Pelaku |
|---|---|---|
| `pecah` | `PlayerBlockBreakEvents.AFTER` di dalam volume | pemain |
| `pasang` | `UseBlockCallback` di dalam volume + perbedaan berupa blok asing di sisi yang diklik dalam 10 s | pemain |
| `ledakan_tnt` | `ServerEntityEvents.ENTITY_LOAD` `PrimedTnt` dalam volume + 16 blok → `getOwner()` + posisi; perbedaan dalam 8 blok dalam 10 s | pemilik TNT |
| `api` | `UseItemCallback`/`UseBlockCallback` dengan `flint_and_steel` atau `fire_charge` di volume; blok terbakar di dekatnya dalam 60 s | pemain |
| `kehancuran` | `WorldBreaker.addChangeListener` (baru, 38PR) — pemilik pekerjaan (murid → tuan) | pemain |
| `serangan` | perbedaan selama serbuan aktif (Gelombang Binatang, `SectRaids`, serbuan desa, perang sekte) | — |
| `monster` | kawah tanpa log, endermen, api alam | — |

- **Gugus kerusakan:** entri dalam 6 blok dan 2 menit digabung; gugus punya pelaku bila ≥ 60 % entri teratribusi ke satu
  pemain. Pemain di dalam klaimnya sendiri tidak pernah tercatat (38.R.3 aturan 2).

#### 38.R.3 Pekerjaan perbaikan

- **Pekerjaan** (`PekerjaanPerbaikan`): daftar operasi `(pos, targetState, expectedState, tagBE?)`, urut: penopang dulu
  (Y terendah, blok penuh), lalu dinding, atap, kaca, pintu, tanaman; satu pekerjaan per gugus.
- **Tukang:** tubuh `TUKANG` dari titik `kotabesar/tukang`, bengkel kampung, pelayan kompleks, pemilik pos (jumlah regu: ibu
  kota 3 regu × 3, kota 3, kampung 2, kompleks 3, pos 1) berjalan ke gugus membawa prop (palu, papan, ember plester,
  sekop, bibit), memainkan klip `perbaiki_palu`, `perbaiki_plester`, `angkat_papan`, `ganti_tanah`, `tanam_ulang`
  (38.N.5.3); satu MANDOR (`KEPALA_DESA`/`KAPTEN_PENJAGA`/`TETUA_SEKTE`) berdiri `tunjuk`. Suara palu dan baris
  `tempat.repair_work`. Perancah: **gambar klien** (tiang dan papan quad di sekitar gugus), bukan blok.
- **Laju dasar:** satu blok per tukang per `perbaikan.detikPerBlok` (5 s) → regu 3 orang ±36 blok/menit; maks 60 per
  tempat per menit. Pengali: **kru sewaan** (tagihan dibayar) ×4 (tiga tukang tambahan muncul); **takut** ×0.33; donasi ×2
  (1 hari); pemain membantu ×1.5.
- **Penerapan blok:** tiap detik, operasi yang "selesai dikerjakan" oleh tukang dikumpulkan menjadi satu
  `RestoreRequest` dan dikirim ke `WorldBreaker.submitRestore`:

```java
public record RestoreRequest(ResourceKey<Level> dimension, long[] positions, BlockState[] targets,
        BlockState[] expected, @Nullable CompoundTag[] blockEntityTags, String reason,
        @Nullable java.util.function.Consumer<boolean[]> done) {}
public static boolean submitRestore(RestoreRequest request);   // WorldBreaker, 38PR, aditif
```

  Semantik (sama dengan restore yang ada, diperketat): posisi diubah **hanya** bila blok sekarang **sama dengan
  `expected`** (keadaan saat pekerjaan direncanakan) atau udara; tunggu chunk termuat; lewati bila ada makhluk berdiri di
  sana; ikut topi `kehancuranBlocksPerTick`, jeda MSPT, `FLAGS` yang sama (tanpa pembaruan tetangga, tanpa jatuhan);
  operasi yang gagal dikembalikan lewat `done` dan direncanakan ulang. Topi tambahan 38: `perbaikan.blokPerTick` 8.
- **Dikerjakan saat tak ada orang:** bila chunk tak termuat, kemajuan dihitung sebagai jam kerja data; saat chunk dimuat
  lagi, operasi yang "sudah dikerjakan" diterapkan sekaligus lewat restore (tanpa animasi), tetap dalam topi per tick.
- **Aturan keselamatan:**
  1. Hanya posisi di dalam volume yang terekam.
  2. **Tidak pernah di klaim pemain:** `ProtectedZones.claimed(level, pos)` — metode publik baru (38PR, aditif) yang hanya
     menanyakan penyedia klaim (`addClaimProvider`): wilayah aula (zona prasasti/aula yang sudah dicatat
     `ProtectedZones.noteHall/noteStele`), kursi `CaveAbode` (38PR menambah satu baris pendaftaran di `CaveAbode.init`),
     plakat Desa (satu baris di `Desa.init`), situs langit 37 (bila 37 mendaftarkannya; 38 tidak menyunting 37).
     Penyedia zona tempat 34/38 **tidak** dihitung sebagai klaim.
  3. **Block entity berisi tidak pernah dibuang:** wadah asing berisi barang di posisi yang harus dipulihkan → dilewati
     dan ditandai "menghalangi" (mandor meminta pemain memindahkannya lewat dialog).
  4. **Blok asing** (dipasang pemain) yang harus dihapus: itemnya (1 buah, `state.getBlock().asItem()`) masuk **Barang
     Sitaan** tempat itu atas nama pemasang (bila diketahui) — diambil gratis lewat dialog penagih/Yamen dalam 14 hari,
     lalu dijual ke Kas Tempat. Tidak ada barang yang hilang diam-diam.
  5. **Wadah dipulihkan kosong**; peti ber-`LootTable` yang belum pernah dibuka dan dirusak **monster** dipulihkan dengan
     `LootTable` yang sama; yang dirusak pemain dipulihkan kosong (tidak ada tambang jarahan).
  6. Fluida: sumber air hanya dipulihkan bila rekaman berisi sumber dan posisinya tertutup (≥ 4 tetangga padat di
     rekaman); **tidak pernah lava**.
  7. Blok gravitasi hanya bila penopangnya sudah ada (urutan Y).
  8. Api dipadamkan lebih dulu (dipulihkan ke udara rekaman).
  9. Tanaman dipulihkan di umur 0; pohon tidak ditanam ulang (hanya bibit di titik `tanam`).

#### 38.R.4 Tagihan ganti rugi

- **Nilai blok** (`NilaiBlok`, NB, dari tag/material): tanah/rumput 0.05, batu/kerakal 0.1, kayu/papan 0.2, wol/karpet 0.15,
  kaca/panel 0.3, bata/batu bata/terakota 0.4, tembaga/kuarsa/hias 0.6, pintu/pintu kolong 0.5, lentera/rantai/jeruji 1.0,
  ranjang 2, lonceng 20, tanaman 0.05, lain 0.25.
- **Faktor tempat:** kampung/dusun/pos 1.0, desa klan 1.5, kota 1.5, kompleks 2.0, ibu kota 2.5. **Tagihan** =
  `ceil(Σ nilai × faktor × 1.3 (upah))`, minimal 5 NB.
- **Kapan:** gugus berpelaku → saat pelaku berada dalam 48 blok tempat itu (atau kunjungan berikutnya dalam 3 hari dunia)
  **penagih** (Kepala Desa, Kapten Penjaga, Tetua Sekte, pemilik pos) berjalan mendekat, `hormat` kecil, lalu layar
  **Tagihan Ganti Rugi** (38.U.3.7): daftar jenis blok × jumlah, total, tiga tombol:
  - **Bayar** (`NilaiBatu.pay`): kru sewaan ×4, Nama Baik tetap (+1 bila dibayar dalam 1 hari), rumor baik.
  - **Tawar**: sekali; Nama Baik ≥ 20 → −20 %; selain itu 50 %: −20 % atau penagih tersinggung (+10 %, `marah_tunjuk`).
  - **Tolak** (atau abaikan 1 hari): Nama Baik −`min(30, ceil(tagihan / 5))`; harga di tempat itu +10 % selama 7 hari
    (`TempatHarga.faktor`); ibu kota: poin kejahatan `tagihan / 10` (bisa menjadi Buronan); kampung terikrar: kesetiaan
    −10 (API publik Desa, verifikasi); kompleks: `DiplomasiService.adjust(−5)`; penjaga kota bermusuhan bila Nama Baik ≤ −30
    (aturan K yang ada). Perbaikan tetap jalan dengan laju dasar.
- Beberapa pelaku: masing-masing ditagih bagiannya.

#### 38.R.5 Takut (pemain jauh lebih kuat)

- **Kekuatan tempat** (indeks ranah 0 = Fana … 8 = Penyatuan Dao, ≥ 9 = Abadi): dusun 0, desa 1, desa besar 2, klan =
  ranah Patriark, kota 34 = 3, ibu kota = 4 (Jiwa Baru Lahir; penjaga terkuat), kompleks = `tier` sekte − 1, pos 1.
- Bila `ranahPemain − kekuatan ≥ perbaikan.selisihTakut` (3): **tidak ada tagihan**. Penagih datang lalu `berlutut`,
  baris `tempat.village_fear`; laju perbaikan ×0.33 (2-4 kali lebih lama, dengan kru lebih sedikit); meter **Takut**
  tempat (0-100) naik `min(40, tagihan / 2)` dan turun 2 per hari dunia.
- Akibat Takut ≥ 20: dialog terbata (varian kalimat), anak masuk rumah saat pemain ≤ 32 blok, senyum hilang (wajah 38.O.1
  "cemberut"), pedagang memberi diskon 10 % "persembahan", pemberi quest menolak halus, kedai teh tidak berbagi rumor,
  penduduk festival menjauh. Takut ≥ 60: penduduk `hormat_dalam` saat pemain lewat, penjaga tidak menegur kejahatan
  (tetap dicatat).
- **Menebus:** "Bayar walau tak diminta" (tagihan terakhir) → Takut −30, Nama Baik +5, rumor "orang kuat yang rendah hati";
  membantu tukang; donasi.

#### 38.R.6 Kerusakan oleh monster, serbuan dan sekte lain

- Laju dasar; mandor menempel pengumuman "Mohon bantuan" di papan.
- **Donasi** (dialog mandor): NB → laju ×2 selama 1 hari, Nama Baik +1 per 20 NB (maks +10/hari).
- **Antar bahan** ke gudang perbaikan (jangkar `tukang`): setiap barang blok yang cocok dengan operasi tersisa mengurangi
  satu operasi (barang diambil), Nama Baik +1 per 32 barang.
- **Bantu** ("Bantu Tukang", maks 3 kali/hari): pemain berdiri dekat regu 2 menit (kanal, `Sibuk` tidak diperlukan) →
  laju ×1.5, Nama Baik +2. Akumulasi 100 poin bantu → gelar "Sahabat <tempat>" + hidangan kampung.

#### 38.R.7 Data, anggaran, config dan uji

- SavedData `kultivator:tempat_rusak`: gugus, operasi tersisa, kemajuan, tagihan (status, jumlah, batas), meter Takut per
  tempat, Barang Sitaan, poin bantu. Kunci baru saja; dunia lama tanpa data = tanpa kerusakan tercatat.
- Anggaran: rekam 2 chunk/tick, pindai ≤ 4096 baca/tick, restore ≤ 8 blok/tick (dan topi WorldBreaker), tubuh tukang ikut
  topi 38.N.7, hanya chunk termuat; MSPT tinggi → pindai dan restore dijeda.
- Config `perbaikan`: `aktif` true, `tagihanAktif` true, `takutAktif` true, `selisihTakut` 3, `detikPerBlok` 5,
  `pengaliSewa` 4.0, `pengaliTakut` 0.33, `blokPerTick` 8, `pindaiTick` 40, `rekamPerTick` 2, `faktorIbuKota` 2.5.
- Uji (`/kultivasi uji kota_besar <sub>` dan alias umum `/kultivasi uji tempat <sub>` untuk tempat mana pun terdekat):
  `rusak <n> [pemain|monster|ledakan]` (membuat kerusakan uji **lewat WorldBreaker** (pekerjaan uji op seperti 30.6)
  dengan atribusi yang dipilih), `perbaiki [cepat|semua|batal]`, `tagihan [lihat|buat|bayar|tolak]`, `takut <0-100>`,
  `cetak [info|rekam|bandingkan]` (jumlah blok terekam, perbedaan sekarang), `sitaan`, `gugus` (daftar gugus dan
  pelaku).

---------------------------------------------------------------------------------------------------------------------

### 38.M Lingkar uang: belanja, misi, penghasilan sekte (modul 38EK)

Permintaan pengguna: "Aku bisa membeli senjata di pasar atau lelang dengan uang itu (batu roh), aku juga bisa menjalankan
misi, atau kalau punya sekte aku bisa dapat penghasilan dari menjalankan sekteku."

#### 38.M.0 Mata uang dan kurs (mengikuti perbaikan M3, `docs/progres/fix-M3.md`)

| Mata uang | Dipakai di | Kurs (satu sumber) |
|---|---|---|
| **Batu Roh** Rendah/Menengah/Tinggi/Agung (barang) | semua | dibayar lewat `NilaiBatu.pay/give` |
| **Nilai Batu (NB)** (satuan harga) | kota, ibu kota, kampung, pos | `NilaiBatu.VALUE = {1, 9, 81, 729}`; `give` tidak pernah mengeluarkan Agung (M3) |
| **Batu Asal Abadi** | Alam Abadi, Lelang Abadi | acuan 1 Batu Asal ≈ 150 NB (audit ekonomi S4c); Rumah Uang: beli 180 NB (maks 8 per minggu, hanya pemain Kenaikan Abadi), jual 90 NB (maks 8 per hari) → selisih mencegah putaran |
| **Poin Kontribusi (PK)** | kas aula sekte | 1 PK = 2 NB (satu konstanta M3, verifikasi nama `Harga`/`Contribution`); **tidak pernah** ditukar langsung NB ↔ PK di 38 (hanya lewat barang, memakai harga persembahan yang ada) |

Pita pemain `band` = pita ranah `Harga` (0 Fana … 8 Penyatuan Dao), dipakai semua rumus di bawah.

#### 38.M.1 Sasaran pendapatan dan pengeluaran per pita (dasar keseimbangan)

| Pita | Ranah | Pendapatan aktif / hari dunia (sasaran) | Barang belanja utama |
|---|---|---|---|
| 0 | Fana | 20-30 NB | senjata fana 15-20, makanan, penginapan |
| 1 | Pemurnian Qi | 40-70 | zirah rantai/besi, pil dasar, teleport |
| 2 | Fondasi | 100-160 | senjata roh rendah 60-80, kunci alam, binatang |
| 3 | Inti Emas | 250-400 | senjata roh menengah 240-300, pusaka mutu 1-2, judi batu |
| 4 | Jiwa Baru Lahir | 600-900 | roh tinggi 900-1100, pesanan tempa mutu 3, lelang |
| 5 | Transformasi Dewa | 1200-1800 | pusaka mutu 3-4, lot lelang tinggi |
| 6-8 | Kekosongan … Penyatuan Dao | 2000-4000 (misi NB dibatasi pita 6, M3) | pusaka mutu 4 pesanan, Lelang Agung, Batu Asal |

Satu peningkatan senjata yang berarti = 1-3 hari dunia pendapatan pita itu. Angka ini adalah sasaran uji
(`uji ekonomi simulasi`, 38.M.6), bukan konstanta kode.

#### 38.M.2 Belanja

##### 38.M.2.1 Senjata, zirah dan pusaka (Paviliun Seribu Bilah, Gilda Pandai Pusaka, pasar sekte, kios kota)

- **Senjata kota** (item baru 38EK, model 38MD, `kultivator:senjata/<bentuk>_<mutu>`):

| Bentuk | Kelas Duel Pedang (35) | Serangan (fana / roh rendah / menengah / tinggi) | Kecepatan | Catatan |
|---|---|---|---|---|
| `jian` (pedang lurus) | PEDANG | 5 / 7 / 9 / 11 | 1.6 | seimbang |
| `dao` (golok) | PEDANG | 6 / 8 / 10 / 12 | 1.4 | tebasan berat |
| `qiang` (tombak) | TOMBAK | 5 / 7 / 9 / 11 | 1.2 | jangkauan +0.5 (atribut `entity_interaction_range`, verifikasi) |
| `kapak` | KAPAK | 7 / 9 / 11 / 13 | 1.0 | — |
| `gada` | GADA | 8 / 10 / 12 / 14 | 0.9 | — |
| `kipas_besi` | PEDANG | 4 / 6 / 8 / 10 | 2.0 | ringan |
| `kebut` (fuchen) | PEDANG | 3 / 5 / 7 / 9 | 2.2 | pegangan pertapa |

  Kelas Duel Pedang dibaca `WeaponClass.of(stack)` lewat **tag item** (data, bukan kode: 38EK menambah item ke tag yang
  dibaca `WeaponClass.of`, verifikasi nama tagnya; bila kelas hanya dibaca dari tag vanilla `#minecraft:swords|axes`,
  `qiang` memakai tag tombak 26.3 bila ada, selain itu PEDANG). Daya tahan 250 / 600 / 1200 / 2400. **Tidak bisa** menjadi
  Pusaka Natal (yang tetap hanya `forged_artifact`); bisa diperbaiki Empu.
  Harga: fana 15-20, roh rendah 60-80, roh menengah 240-300, roh tinggi 900-1100 NB (× `priceScale` kota); syarat beli
  pita ≥ 0 / 2 / 3 / 4. Bandingkan pusaka tempa: `SWORD_BASE 6 + 0.6/AP`, batas 22 → senjata kota adalah anak tangga, pusaka
  tempa tetap puncak.
- **Pusaka tempa di rak** (`forged_artifact` bentuk `pedang/payung/wajan/lonceng`, mutu 0-2): dibuat lewat API publik
  `Forging` (38EK menambah `Forging.buatStok(ServerLevel, String bentuk, int mutu, RandomSource)` aditif, verifikasi)
  dengan sub-stat acak; harga `Harga.ARTIFACT[mutu] × 1.2`; stok berputar harian (3-5 barang per rak, 1 barang langka 5 %
  per hari: mutu +1).
- **Pesanan tempa** (Empu Ou Ye): pemain memilih bentuk + mutu (≤ 4) + menyerahkan bahan resep `Forging` (barang diambil
  saat memesan) + ongkos `0.15 × Harga.ARTIFACT[mutu]`; jadi dalam 1/2/3/3/4 hari dunia (mutu 0-4); hasil = keluaran
  `Forging` dengan panas tingkat gilda 3; disimpan di `KotaBesarData` (persisten, 38.0 butir 16) dan diambil di gilda.
  Maks 2 pesanan per pemain.
- **Zirah**: vanilla (rantai 40 NB/set, besi 60, intan 180 per keping, pita ≥ 1/1/3) + **Jubah Kultivator** kota (item
  hias tanpa atribut, 4 warna gilda, 30 NB).
- **Jimat, pil, herba**: toko lama `Toko` (jangkar `kota/pil`) tetap; Gilda Alkemis menambah herba/pil bergating Tingkat
  Alkemis (pita herba ≤ tingkat; batas umur herba ≤ 300 tahun aturan M3).
- **Perbaikan dan penilaian**: perbaikan daya tahan = 2 % nilai per 10 % daya tahan; penilaian (tampilkan sub-stat
  tersembunyi) gratis 3 kali/hari.

##### 38.M.2.2 Jasa dan harga lain

| Jasa | Harga (NB) | Catatan |
|---|---|---|
| Kamar penginapan (ibu kota, pos) | 4 / malam (pos 2) | bangun: Regenerasi I 60 s + "Istirahat Nyenyak" (XP meditasi ×1.10 selama 20 menit; tidak bertumpuk dengan teh, yang lebih besar berlaku) |
| Hidangan: Bakpao Roh, Sup Ginseng Roh, Mi Panjang Umur, Arak Bunga Persik (item baru, 38EK) | 2 / 6 / 12 / 25 | kenyang; regen Qi +10 % 5 menit; penyerapan +2 hati 3 menit; pemahaman +0.02/menit meditasi 10 menit (tidak bertumpuk dengan Teh Awan Ungu) |
| Pasar kampung (makanan, herba muda, alat) | `Harga.value × 0.9` | stok 4-6, beli-kembali 0.3 |
| Pasar sekte NPC | `Harga.value × 1.0` (sekutu −10 %) | barang khas aliran |
| Pedagang keliling / pos dagang | `Harga.value × 1.1` | barang wilayah lain; beli rampasan × 0.4 |
| Teleport / Perjalanan Awan / rakit | `ceil(2 + d/250)` maks 120 / `ceil(6 + d/150)` / 1 | 38.B.4.3 |
| Dupa kuil / kuil jalan | 3 / 1 | |
| Baca kitab (pratinjau) / salin giok | `10 × mutu` / `0.6 × nilai giok + 10` | salinan ≥ beli-kembali → tanpa arbitrase |
| Arsip kabar (jenis tempat 38) | `1.5 ×` harga kabar Kedai Teh | |
| Tabib (Penyimpangan Qi / murid luka) | `20 × (band + 1)` / `15 × (band + 1)` | kampung `6 × (band + 1)` pita ≤ 3 |
| Pelatih binatang | `15 × tier` | 1 per binatang per hari |
| Anak binatang roh (serigala / rubah) | 120 / 160 | 1 per 3 hari, pita ≥ 2 |
| Sesaji evolusi | `Harga.value × 1.2` | stok 2/hari |
| Brankas Rumah Uang | gratis 9 slot (18 Warga Dikenal) | barang (bukan wadah berisi) disimpan di SavedData |
| Tarik simpanan / Surat Wesel / gadai | 1 % (min 1) / 2 % / pinjam 40 % nilai, tebus 46 % dalam 7 hari | gadai tak ditebus → barang dijual Rumah Uang |
| Judi batu: Kerikil / Bongkah / Batu Gunung / Batu Purba | 5 / 40 / 300 / 2400 | nilai harapan 0.88 (38.M.2.4) |
| Buka gerbang malam | 2 | gratis Terhormat |
| Suap penjaga (bukan Buronan Berat) | `10 × (band + 1)` | −10 poin kejahatan, sekali/hari; 50 % ditolak penjaga jujur (+5 poin) |
| Denda / tagihan ganti rugi | 38.B.5.2 / 38.R.4 | sink |
| Biaya ujian gilda / Pertemuan Pil / turnamen | `5 × n²` / `20 × (band + 1)` / `50 × (band + 1)` | sink + dana hadiah |

##### 38.M.2.3 Lelang terpadu (satu mesin, bukan sistem ketiga)

- **Mesin:** `dunia/kota/BalaiLelang` (kota) dengan penitipan persisten `Titipan` (M3) adalah **satu-satunya** mesin lelang
  NB. 38EK menambah satu titik masuk publik aditif `BalaiLelang.mulai(ServerLevel, DuniaAnchorEntity juru, ProfilLelang
  profil)` dengan `record ProfilLelang(int lot, int titipMaks, double komisi, List<String> sumberPenawar, String
  tabelLot)`; profil `KOTA` = angka lama (5 lot, titip 2, komisi 0), profil `AGUNG` (ibu kota): 8 lot (3 pita rendah, 3 pita
  menengah, 2 pita tinggi relatif pemain terkuat yang hadir), titip 4, **komisi 10 %** (sink, ke Kas Kota), penawar NPC =
  kelompok Saingan + nama sekte NPC (`DiplomasiService`) + tokoh (Gu Lianshan, Qian Duoduo).
- **Isi lot Agung** (`tabelLot` `agung`): senjata kota roh tinggi, pusaka tempa mutu 2-4 (dibuat `Forging.buatStok`), pil
  dan harta `Harga.TREASURES` pita tinggi, sesaji binatang, kunci Alam Rahasia, gulungan penunjuk tempat langka, satu
  **Lot Misteri** (kotak tertutup, isi dari tabel judi Batu Gunung).
- **Lelang Abadi** (`abadi/Auction`, Batu Asal) tetap di Kota Abadi; ia memakai `Titipan` yang sama (M3). Ibu kota hanya
  menampilkan jadwalnya di Papan Kabar.
- Layar lelang hidup 38.U.3.3.

##### 38.M.2.4 Judi batu (nilai harapan dijaga)

- Hasil per batu (semua tingkat, harga P): kosong 50 %; kecil 30 % (0.6-1.2 P, rata 0.9); sedang 15 % (rata 2.2 P);
  besar 4 % (rata 5 P); jackpot 1 % (rata 8 P) → **EV = 0.27 + 0.33 + 0.20 + 0.08 = 0.88 P**. Isi = batu roh (lewat
  `NilaiBatu.give`) atau barang dari tabel `Harga` dengan nilai setara (herba berumur, inti binatang, giok teknik, bahan
  tempa); jackpot Batu Purba bisa memberi pusaka mutu 3.
- **Mata Batin** (pita ≥ 4, 20 % bar Qi, maks 3 per hari): petunjuk warna (abu/biru/emas) yang benar 70 %; batu yang sudah
  diperiksa naik harga ×1.6 (bandar tahu). Strategi terbaik diuji `uji ekonomi judi 100000` → EV ≤ 1.02.
- Batas: 10 batu per hari per pemain, Batu Purba 1 per hari.

#### 38.M.3 Penghasilan dari misi (Gilda Kultivator; satu papan, bukan duplikat)

- **Satu sistem papan:** `PapanMisi` kota (K, jenis `buru/kemah/herba/kawal`) tetap; 38EK memperluasnya (satu pemilik di
  38, setelah M3) menjadi **papan bertingkat**: setiap misi punya **pangkat E-S**; papan kota menawarkan E-C, Gilda
  Kultivator ibu kota E-S, papan salinan di pos singgah E-D. Misi lama papan kota dihitung sebagai E/D/C menurut pitanya,
  sehingga poin gilda juga naik dari papan kota.
- **Pangkat dan syarat:** E (Fana+), D (Pemurnian Qi), C (Fondasi), B (Inti Emas), A (Jiwa Baru Lahir), S (Transformasi
  Dewa+). Batas waktu: E/D 1 hari dunia, C/B 2, A/S 3. Misi per hari: 3 (+1 di Lencana 5, +1 di Lencana 8; maks 5).
- **Imbalan NB** = `dasar(band)` M3 (×2 per pita, batas pita 6; mis. pita ≤ 1: 6-12, 2: 15-30, 3: 40-80, 4: 80-160, 5:
  160-320, 6+: 320-640) × pengali pangkat (E 0.6, D 0.8, C 1.0, B 1.3, A 1.6, S 2.0) + Nama Baik (+2..+4) + poin gilda (E 1,
  D 2, C 4, B 7, A 11, S 16) + 20 % peluang barang (giok, sesaji, peta).
- **Jenis misi** (cerita singkat 2-3 kalimat per templat + 20 % **tikungan**: cabang moral di tengah misi):

| Jenis | Tujuan nyata | Tikungan contoh |
|---|---|---|
| `buru`, `kemah`, `herba`, `kawal` (lama) | seperti `PapanMisi` | — (tetap) |
| `binatang` | kalahkan bos `gua/sarang_binatang` | induk melindungi anak: biarkan (Nama Baik +3, imbalan ½) atau bunuh |
| `sarang` | bersihkan `gua/sarang_iblis`, bebaskan tawanan | salah satu tawanan adalah mata-mata iblis |
| `begal` | bubarkan `pos/kemah_begal` | begal ternyata petani kelaparan: ampuni (beri 20 NB) atau tangkap |
| `kafilah` | kawal kafilah data dari pos/kota ke kota lain sepanjang jalan | kafilah menyelundupkan barang Pasar Gelap |
| `gua` | selidiki gua R/38, bawa bukti (barang kunci dari peti) | — |
| `kurir` | antar surat ke kampung/kompleks | surat berisi tantangan duel |
| `tawanan` | kawal tawanan bebas pulang | — |
| `naga_muda` | tanding dengan peringkat Daftar Naga Muda | Feng Ao ikut (38.S.4) |

- **Lencana Bintang 1-9** (poin 10/30/60/100/160/240/340/460/600): 3 → diskon 5 % toko gilda; 5 → +1 misi/hari; 7 → misi S dan
  **Gudang Gilda** (stok langka mingguan); 9 → gelar "Bintang Sembilan", 1 teleport gratis/hari. Ujian di Lencana 3, 6, 9:
  Duel Pedang melawan PENGUJI seranah (aturan ramah).
- **Daftar Naga Muda:** papan 50 nama (penduduk data kultivator + juara sekte NPC + Feng Ao + pemain); skor dari poin gilda,
  menang duel di arena, turnamen; diperbarui tiap hari; 10 besar → gelar, 1 → "Naga Muda Pertama".

#### 38.M.4 Penghasilan sekte (buku kas yang jelas)

- **Buku Kas Sekte** (`BukuKas`, SavedData `kultivator:tempat_kas`): baris `(hari, sumber, jenis PK|BATU, ±jumlah, catatan)`
  per pemilik aula, simpan 30 hari dunia; layar 38.U.3.6 memisahkan **kolom PK** dan **kolom Batu (NB)**, total harian,
  grafik 7 hari. Pencatat: modul 38 sendiri (`BukuKas.catat(...)`) + pendengar publik yang sudah ada (`SekteBus`
  `WORK_DONE` tambang/kebun, `TOURNAMENT_ENDED`, peristiwa Diplomasi) bila tersedia; 38 tidak menyunting 36.
- **Sumber penghasilan:**

| Sumber | Jenis | Asal | Batas |
|---|---|---|---|
| Tambang Roh, Kebun Obat (36 W) | barang → Lumbung (dicatat nilai PK) | sistem 36 yang ada | batas 36 |
| Upeti desa bawahan (25.6) + desa klan (+bahan kultivasi, 38.C.2.3) | barang | `Tribute` + kait 38DS | batas lama |
| **Lapak Sekte** di ibu kota (38.M.4.1) | NB | penjualan ke pembeli data | 38.M.4.1 |
| **Kafilah Dagang Sekte** (38.M.4.2) | NB | jual barang di kota tujuan | 38.M.4.2 |
| Hadiah turnamen (aula 36, Turnamen Agung 38) | NB / PK | hadiah | per turnamen |
| Upeti sekte bawahan / pakta (36 D) | PK / barang | `DiplomasiService` | batas D |
| Jual surplus Lumbung ke Gilda | NB | 0.5 × `Harga.value` | maks `30 × (band+1) × level aula` NB/hari |
| Misi cerita (36 X) | PK / barang | X | batas X |

- **Pengeluaran yang dicatat:** sewa lapak, ongkos kafilah, gaji pengawal kafilah (PK), biaya ujian, tagihan ganti rugi yang
  dibayar atas nama sekte, sewa kru perbaikan.

##### 38.M.4.1 Lapak Sekte

- Sewa satu dari enam kios `kotabesar/lapak_sekte`: `20 × (band + 1)` NB per 7 hari (syarat aula lv ≥ 2). Inventori lapak 18
  slot diisi pemain di lapak (barang buatan sekte: pil, jimat, herba, senjata tempa; **ditolak**: wadah berisi, cincin,
  barang fase 9, batu roh).
- **Penjaga lapak:** satu murid ditugaskan (murid "away" dengan alasan `lapak`, pola `Halls.storeAway`, verifikasi);
  saat pemain ≤ 48 blok, tubuh NPC bertampang murid itu berdiri di kios (`seru_dagang`, `hitung_sempoa`, 38.N).
- **Penjualan:** sekali per hari dunia, setiap tumpukan punya peluang terjual per harga relatif `h = harga / Harga.value`:
  h ≤ 1.0 → 80 %, ≤ 1.2 → 50 %, ≤ 1.5 → 20 %, > 1.5 → tidak (harga maks 1.5); volume maks per hari `40 × (band + 1) ×
  levelAula` NB; pajak pasar 5 % (sink). Hasil masuk kas lapak (ambil di lapak) dan buku kas. Watak murid (36 P) mengubah
  peluang ±10 % (Licik +, Malas −).

##### 38.M.4.2 Kafilah Dagang Sekte

- Syarat aula lv ≥ 3 (1 kafilah aktif; lv 6: 2). Pemain memilih barang (dari Lumbung atau inventori) + tujuan (kota/ibu
  kota/pos dagang yang diketahui) + 0-3 murid pengawal. Durasi `ceil(jarak / 1500)` hari dunia (min 1).
- Harga jual di tujuan = `Harga.value × permintaan` (0.9-1.3 menurut tabel bioma/jenis barang tujuan, berubah tiap 7 hari);
  ongkos 5 % + 2 PK per pengawal per hari. Risiko begal: 10 % per hari di cincin ≥ R3 tanpa pengawal (2 %/hari dengan ≥ 2
  pengawal Fondasi+); diserang → kehilangan 30-60 % muatan, murid tidak pernah mati (aturan 36.0 butir 3: luka).
- Laba bersih dibatasi `+30 %` nilai barang dan `200 × (band + 1)` NB per minggu per sekte. Pemain boleh ikut mengawal
  (misi `kafilah` gilda dengan imbalan tambahan).

##### 38.M.4.3 Anak berbakat dan Hari Pembukaan Gerbang (penghasilan murid, bukan uang)

- Hari Pembukaan Gerbang (ibu kota, 10-harian): sewa lapak perekrutan 30 NB; 8-12 calon muda (penduduk data `ANAK`
  remaja); peluang menerima `clamp(0.3 + renown/1000 + 0.1 × (levelAula − rata-rata tier sekte NPC) + NamaBaik/200,
  0.1, 0.9)`; maks 3 calon per hari → `Halls.addCandidates(owner, List<CandidateSpec>)` (verifikasi pembuatan
  `CandidateSpec`).
- Kampung terikrar: 1 calon per 10 hari (38.C.2.3).

#### 38.M.5 Keran dan pembuangan (anti-eksploit)

| Keran (faucet) | Batas | Pembuangan (sink) | Besar |
|---|---|---|---|
| Misi gilda / papan | 3-5 per hari, imbalan pita ≤ 6 | komisi Lelang Agung | 10 % harga |
| Lapak sekte | volume harian `40 × (band+1) × lv`, harga ≤ 1.5 nilai | pajak lapak | 5 % |
| Kafilah | laba ≤ 30 %, ≤ `200 × (band+1)`/minggu | ongkos kafilah, risiko begal | 5 % + kerugian |
| Jual surplus Lumbung | ≤ `30 × (band+1) × lv`/hari, 0.5 × nilai | sewa lapak | `20 × (band+1)`/minggu |
| Pertahanan kota | `30 × (band+1)` per kemenangan, dari Kas Kota (yang diisi denda/komisi) | denda, tagihan, suap | 38.B.5 / 38.R |
| Judi batu | EV 0.88, 10 batu/hari | judi | 12 % harapan hilang |
| Hadiah lomba/turnamen | dari biaya pendaftaran ×4 maks | biaya ujian, pendaftaran | per acara |
| Batu Asal → NB (90) | 8 per hari | NB → Batu Asal (180) | selisih 90 |
| Gadai | — | bunga gadai 15 % | per gadai |
| — | — | teleport, penginapan, makanan, dupa, perbaikan senjata, tabib | jasa |

Aturan: **tidak ada barang yang dibeli kembali di atas 0.5 × harga jual** di mana pun di 38; barang yang dibeli dari
toko 38 diberi komponen `kultivator:asal_toko` (hari beli) sehingga toko 38 dan lapak sekte menolak menjual-ulangnya
dalam 3 hari (putaran beli-jual cepat); **Kas Kota** (per ibu kota, data) hanya diisi pembuangan dan hanya membayar hadiah
pertahanan/festival (tidak pernah mencetak).

#### 38.M.6 Uji ekonomi

`/kultivasi uji ekonomi ...` (op, 38EK): `simulasi <pita> <hari>` (simulasi pemain aktif: misi + belanja khas; cetak
pendapatan/pengeluaran per hari, harus jatuh di sasaran 38.M.1 ±25 %), `judi <n>` (EV per strategi), `lapak <hari>`,
`kafilah <tujuan>`, `kas` (buku kas pemain), `kurs` (cetak `NilaiBatu.VALUE`, PK/NB, Batu Asal), `stok ulang`.

---------------------------------------------------------------------------------------------------------------------

### 38.U UI baru: kit "Pasar Giok" dan semua layar (modul 38UI)

Permintaan pengguna: "Aku ingin semuanya dengan GUI baru yang segar." Semua layar konten 38 memakai kit baru yang **segar
tetapi tetap keluarga Gulungan Giok** (DESIGN 5b: bukan tampilan vanilla/Butter Bot, navigasi tablet vertikal di kiri,
tanpa baris tab atas; DESIGN 33: dibuka dari hub atau interaksi NPC, muat 320 × 240).

#### 38.U.1 Arsitektur dan kontrak

- **Server** (38UI, kecil): `dunia/tempat/ui/TempatLayar` — payload S2C `kultivator:tempat_layar` `(String layar, int npcId,
  CompoundTag lembar)` dan C2S `kultivator:tempat_aksi` `(String layar, int npcId, String op ≤ 256)` (didaftarkan di init
  38UI sendiri, pola `KotaNet`); `TempatLayar.buka(ServerPlayer, String layar, int npcId, CompoundTag lembar)`;
  `TempatLayar.daftar(String layar, Penangan p)` dengan `interface Penangan { void aksi(ServerPlayer p, int npcId, String
  op); }`. Modul gameplay (KS, EK, DS, GU, PR, ST, W) **membangun lembar** (pola `KotaView`) dan mendaftar penangan; 38UI
  hanya menggambar. Jarak aksi ≤ 8 blok dari NPC (`KotaNet.REACH`) atau dari jangkar layanan; layar hub tanpa NPC memakai
  `npcId = -1` dan hanya membaca.
- **Klien:** `client/tempat/ui/kit/**` (komponen) + `client/tempat/ui/layar/**` (layar). Basis: `client/screen/ui/Widget`,
  `Palette`, `UiSound`, `SlipTooltip`, `TalismanButton`, `JadeCoin`, `Eased` yang ada (dipakai, tidak disunting).
- **Hub** (`HubCards.register`, tanpa menyunting `HubCardsPage`): DUNIA: kartu "Kota Besar" (peta kota & direktori),
  "Catatan Perjalanan" (jurnal); SEKTE (sub-koin Aula, kunci aula): "Buku Kas", "Lapak & Kafilah"; DIRI tidak diubah.
  Topik Ensiklopedia `kota_besar`, `tempat`, `gilda`, `perbaikan`, `ekonomi` (lang 38ST).

#### 38.U.2 Kit (token, tata letak, komponen)

- **Grid:** satuan 4 px; margin aman 6 px; jarak antar-komponen 4/8 px; lebar kolom kiri tablet 28 px (tablet 24 px +
  4). Ukuran dasar 320 × 240 (GUI terskala), tata letak lebar ≥ 480 × 270 memakai dua kolom isi.
- **Bingkai panel** (9-slice, sudut 8 px, `textures/gui/tempat/panel_<gaya>.png` 32 × 32): `kertas` (perkamen dengan
  bingkai emas ganda, gaya hub), `kayu` (papan lak merah untuk toko/pasar), `batu` (abu batu untuk Yamen/gilda), `kain`
  (sutra untuk istana/lelang), `giok` (jade untuk stasiun/kuil). Garis awan samar di latar (tekstur), simpul ornamen di
  sudut.
- **Tipografi** (font vanilla; tidak ada font baru): judul ×1.5 (bayangan tinta), subjudul ×1.0 tebal (`ChatFormatting.BOLD`),
  isi ×1.0, keterangan ×0.75, **minimum 0.6** (aturan 33); angka rata kanan; satu baris maks 52 karakter isi pada 320 lebar;
  teks panjang dipotong dengan "…" + tooltip penuh.
- **Token warna** (`TempatWarna`, turunan `Palette`): distrik `istana` emas-merah `#C9A24A/#8E1F1F`, `cendekia` tinta-biru
  `#2A3A5C`, `awan` biru langit `#6FA8C8`, `gilda` perunggu `#8C6A3A`, `pusat` giok `#5FA88C`, `pasar` ambar `#D08A2A`,
  `hunian` cokelat `#7A5A3A`, `arena` merah tua `#7A1A1A`, `kumuh` abu `#6A6A60`; aliran Lurus `#D8E4F0`, Netral `#C9A24A`,
  Iblis `#6A1420`; status ok `#5FA88C`, peringatan `#D0A040`, bahaya `#B03030`, terkunci 45 % tinta.
- **Ikon** `textures/gui/tempat/ikon.png` 256 × 256 (16 px, 16 × 16 slot): 4 batu roh + NB + PK + Batu Asal, pangkat E-S,
  bintang 1-9, 7 bentuk senjata + zirah + pil + herba + jimat, 11 distrik, 20 layanan, gembok, jam pasir, panah tren,
  kafilah, lonceng, cap merah, mata (Mata Batin), timbangan.
- **Gerak:** buka layar = "lentera dibuka" 200 ms (skala Y 0.92 → 1 + alfa, `Eased`); hover = naik 1 px + pendar 120 ms;
  tekan = turun 1 px 60 ms + suara `UiSound` (lonceng giok); daftar muncul berurutan 20 ms per baris (maks 8); angka uang
  bergulir 300 ms saat berubah; tawaran lelang baru berkedip emas 400 ms.
- **Komponen** (`client/tempat/ui/kit`): `PanelTempat` (9-slice bergaya), `TabLentera` (tablet vertikal berbentuk lentera,
  yang aktif menyala dan menjorok 3 px; angka 1-9), `DaftarGulir` (daftar bergulir, batang gulir giok, roda tetikus, ↑↓,
  PgUp/PgDn, Home/End), `KartuBarang` (ikon 16 atau pratinjau, nama berwarna mutu, harga, stok, keadaan), `Pratinjau3D`
  (model item/entitas berputar 20°/s di kotak 64-96 px, seret untuk memutar; memakai render item GUI dengan `PoseStack`
  diputar sumbu Y dan dimiringkan 25°, verifikasi API 26.3 `GuiGraphics` item 3D; cadangan: ikon 4×), `BandingStat` (bilah
  stat dengan selisih hijau/merah terhadap senjata di tangan), `LabelHarga` (format "1.234 NB", "2,5 rb", "1,2 jt";
  tooltip rincian batu "1 Tinggi · 4 Menengah · 3 Rendah"; warna merah bila uang kurang), `PemilihJumlah` (−/+ koin giok,
  Shift ×10), `LencanaPangkat` (E-S berbingkai warna), `CincinWaktu` (cincin hitung mundur), `PapanSkor` (baris hidup
  berurut ulang dengan animasi geser), `KeadaanKosong` (ilustrasi kecil + kalimat + tombol arah), `KeadaanTerkunci` (cap
  gembok + alasan + syarat), `TombolAksi` (memakai `TalismanButton`), `GelembungDialog` (panel bicara dengan potret).
- **Keyboard:** Tab/Shift+Tab pindah fokus (cincin fokus emas 1 px), Enter aktifkan, Esc kembali/tutup, 1-9 tab, `/` cari
  di daftar toko, ←/→ ubah jumlah, Spasi = tawar (lelang). Semua aksi bisa tanpa tetikus.
- **Skala GUI 2-4 dan jendela kecil:** tata letak dihitung dari lebar terskala: < 320 → satu kolom, pratinjau 3D menjadi
  ikon 32 px dan panel stat bisa dilipat; 320-479 → kolom kiri daftar + panel kanan detail; ≥ 480 → tiga kolom (tablet,
  daftar, detail + pratinjau besar). Tidak ada teks di bawah 0.6, tidak ada tumpang tindih (diuji `uji ui skala 2|3|4`).
- **Tekstur** dibuat `tools/gen_tempat_ui.py` (38UI, `tools/png.py`): 5 panel, ikon, tablet lentera, potret bingkai,
  stempel; pratinjau 8× dibuka dengan Read.

#### 38.U.3 Layar dan wireframe (320 × 240; ⟦ ⟧ = tablet kiri, [ ] = tombol, ( ) = koin)

##### 38.U.3.1 Dialog NPC (`dialog`) — semua NPC 38

```
+----------------------------------------------------------------------+
| ⟦Bicara⟧ |  ┌──────┐  Bibi Hong · Pemilik Penginapan     Nama Baik 34 |
| ⟦Jasa  ⟧ |  │potret│  "Kamar? Ada. Tapi... Tuan Kultivator,          |
| ⟦Quest ⟧ |  │ 48px │   boleh minta tolong sebentar?"                 |
|          |  └──────┘                                                 |
|          |  1 [Ada apa, Bi?]                                         |
|          |  2 [Aku sibuk.]                                           |
|          |  3 [Tentang kamar...]                    (Esc) tutup      |
+----------------------------------------------------------------------+
```

Potret = render kepala NPC sungguhan (model + wajah 38.O.1) yang berkedip dan bicara; pilihan bersyarat yang tidak
terpenuhi tampil terkunci dengan alasannya; tablet "Jasa" muncul bila NPC punya layanan.

##### 38.U.3.2 Toko senjata (`toko_senjata`) — Paviliun Seribu Bilah, gilda tempa, pasar sekte

```
+----------------------------------------------------------------------+
|⟦Pedang⟧| Cari: [/ jian____]  Urut: (harga)(mutu)   Uangmu: 1.234 NB  |
|⟦Golok ⟧|-----------------------------+--------------------------------|
|⟦Tombak⟧| ▣ Jian Roh Menengah  260 NB |   ╭──────────╮  Jian Roh Mnh  |
|⟦Kapak ⟧| ▣ Dao Roh Menengah   280 NB |   │  3D item │  Serangan  9 ▲2|
|⟦Gada  ⟧| ▣ Jian Roh Tinggi  1.020 NB🔒│   │ berputar │  Kecepatan 1.6 =|
|⟦Kipas ⟧| ▣ Pusaka Pedang Mutu 2 *    |   ╰──────────╯  Daya tahan 1200|
|⟦Kebut ⟧| ▣ ...                       |  Kelas duel: PEDANG             |
|⟦Pesan ⟧|                             |  vs di tangan: Pedang Besi      |
|        |  ▼ 6 dari 9                 |  [Beli 260 NB]  (−)(1)(+)       |
+----------------------------------------------------------------------+
```

Keadaan: stok habis (kartu abu "Habis, kembali besok"), terkunci pita ("Butuh Inti Emas"), uang kurang (harga merah, tombol
nonaktif dengan alasan), barang langka hari ini (bintang emas), tab **Pesan** (pesanan tempa: pilih bentuk/mutu, daftar
bahan dengan centang punya/kurang, waktu jadi, ongkos; daftar pesanan berjalan dengan `CincinWaktu`).

##### 38.U.3.3 Lelang hidup (`lelang`) — Balai Lelang kota dan Lelang Agung

```
+----------------------------------------------------------------------+
|⟦Lot   ⟧| LOT 3/8  Pusaka Payung Mutu 3     ╭────────╮  Harga kini     |
|⟦Titip ⟧| Penjual: Gilda Tempa              │  3D    │   2.640 NB      |
|⟦Hasil ⟧| Dasar 2.200 NB                    ╰────────╯  oleh Gu Lianshan|
|        |-----------------------------------------------  ◷ 0:06      |
|        | Papan tawaran:                                               |
|        |  Gu Lianshan ........ 2.640  ✦ baru                          |
|        |  Kelompok Kabut Hitam 2.520                                  |
|        |  Anda ............... 2.400                                  |
|        | [Tawar +5%] [+10%] [+25%]  (Spasi = +5%)   Uangmu 3.100 NB   |
+----------------------------------------------------------------------+
```

Tab **Titip** (barang yang dititip + status persisten `Titipan`), **Hasil** (lot terjual, hasil titipan, utang). Keadaan
"belum dimulai" (jadwal dan hitung mundur), "lot berikutnya", "terjual" (cap merah "TERJUAL" berputar masuk).

##### 38.U.3.4 Papan Misi Gilda (`gilda_misi`)

```
+----------------------------------------------------------------------+
|⟦Papan ⟧| Lencana ★★★★☆☆☆☆☆  (poin 142/160)   Misi hari ini 2/4       |
|⟦Aktif ⟧|------------------------------+-------------------------------|
|⟦Naga  ⟧| [C] Bubarkan begal Jalan Barat|  Bubarkan begal Jalan Barat  |
|⟦Ujian ⟧| [B] Sarang iblis di Lembah... |  Pangkat C · Fondasi+        |
|        | [E] Antar surat ke Dusun Batu |  "Kafilah garam diserang tiga |
|        | [S] 🔒 butuh Transformasi Dewa|   kali di tikungan..."        |
|        |                               |  Imbalan 60 NB · NB+3 · ★+4  |
|        |                               |  Waktu 2 hari · ±640 b barat  |
|        |                               |  [Terima]  [Tunjuk di peta]   |
+----------------------------------------------------------------------+
```

Tab **Aktif** (misi berjalan, tahap, penunjuk arah, batas waktu), **Naga** (Daftar Naga Muda 50 baris, posisi pemain
disorot), **Ujian** (ujian lencana berikutnya). Kertas misi di papan dunia (38.O.2 `papan_hidup`) menampilkan judul yang
sama.

##### 38.U.3.5 Rumah Uang (`bank`)

```
+----------------------------------------------------------------------+
|⟦Simpan⟧| Saldo: 12.480 NB      Di tas: 3 Tinggi 5 Menengah 12 Rendah |
|⟦Wesel ⟧| [Setor semua batu]  [Tarik ___ NB] (biaya 1%)               |
|⟦Gadai ⟧| Riwayat: +500 (hari 41) · −120 (hari 40) · ...               |
|⟦Tukar ⟧| Brankas 9/18:  ▣ ▣ ▣ ▣ ▣ ▢ ▢ ▢ ▢                           |
|⟦Brankas⟧                                                             |
+----------------------------------------------------------------------+
```

##### 38.U.3.6 Buku Kas Sekte (`buku_kas`) — hub SEKTE dan Kantor Catatan

```
+----------------------------------------------------------------------+
|⟦Hari ini⟧ Hari 52            PK            BATU (NB)                 |
|⟦7 hari ⟧ Tambang Roh         +38                                     |
|⟦Sumber ⟧ Lapak Sekte                       +214  (pajak −11)         |
|⟦Lapak  ⟧ Kafilah ke Kota Awan               +0   (berjalan, 1 hari)  |
|⟦Kafilah⟧ Upeti Desa Kali Putih  +12 (barang)                         |
|         Sewa lapak                          −60                      |
|         ─────────────────────────────────────────                    |
|         Bersih                  +50 PK      +143 NB    ▁▃▅▂▆▇▅ 7 hari|
+----------------------------------------------------------------------+
```

PK dan Batu **selalu** dalam dua kolom terpisah dengan ikon masing-masing; tab **Lapak** (isi 18 slot, harga per barang
dengan petunjuk "laku ±80 %/50 %/20 %", murid penjaga) dan **Kafilah** (muatan, tujuan, pengawal, risiko, perkiraan laba,
kafilah berjalan di peta mini).

##### 38.U.3.7 Tagihan Ganti Rugi (`tagihan`)

```
+------------------------------------------------------+
|  ✉ Tagihan dari Kepala Desa Kali Putih                |
|  "Maaf, Tuan... pagar dan dua rumah rusak kemarin."  |
|   Batu bata ×24 ....... 9,6                          |
|   Papan kayu ×40 ...... 8,0                          |
|   Kaca ×6 ............. 1,8                          |
|   Upah 30% ............ 5,9          Total 26 NB     |
|  [Bayar 26 NB]   [Tawar]   [Tolak]                   |
|  Bayar: perbaikan ×4 lebih cepat, nama baik terjaga.  |
|  Tolak: Nama Baik −6, harga +10% 7 hari.             |
+------------------------------------------------------+
```

Varian **takut** (tanpa angka): NPC berlutut di potret, teks "Kami akan memperbaikinya sendiri...", tombol [Bayar walau
tak diminta] dan [Pergi]. Varian **donasi** (kerusakan monster): kemajuan perbaikan, [Donasi], [Antar bahan], [Bantu].

##### 38.U.3.8 Peta Kota dan Direktori (`peta_kota`)

```
+----------------------------------------------------------------------+
|⟦Peta   ⟧|  ┌───────────────┐   Distrik Pasar Timur                  |
|⟦Layanan⟧|  │ ▦ ▦ ▦         │   · Balai Lelang Agung (lelang hari 5)  |
|⟦Tokoh  ⟧|  │ ▦ ◉ ▦ ▤ kumuh │   · Rumah Uang Seribu Tael             |
|⟦Acara  ⟧|  │ ▦ ▦ ▦         │   · Rumah Batu Sumber                   |
|         |  │   ▤ luar      │   · Pasar Binatang Roh                  |
|         |  └───────────────┘   [Tandai di peta dunia]                |
+----------------------------------------------------------------------+
```

Peta skematis 3 × 3 + distrik luar berwarna token distrik, posisi pemain, tablet **Layanan** (daftar semua layanan + jam
buka), **Tokoh** (tokoh berulang yang sudah dikenal + di mana sekarang menurut jadwal), **Acara** (kalender 38.B.6, hitung
mundur).

##### 38.U.3.9 Layar lain (ringkas)

| Layar | Isi utama | Keadaan khusus |
|---|---|---|
| `teleport` (Stasiun, cakram pos) | daftar tujuan diketahui dengan jarak, ongkos, ikon jenis; pratinjau peta mini; [Berangkat] → kanal 3 s di HUD | tujuan belum diketahui (terkunci "Temukan dulu"), buronan ("Ditolak"), uang kurang |
| `dermaga` | rute Perjalanan Awan, bahan bakar, lunas (Pratinjau3D kapal kecil), jadwal tamu langit 37 | malam (tutup) |
| `pasar_binatang` | kandang: anak binatang (Pratinjau3D entitas berputar), sesaji, pelatih (pilih binatang milik terdekat), inti | stok 1/3 hari |
| `judi_batu` | rak batu mentah (Pratinjau3D batu), [Mata Batin], [Beli], meja potong: animasi belah 16 tick + cahaya hasil, riwayat hari ini | batas 10/hari |
| `penginapan` / `rumah_makan` / `jasa` | kamar, hidangan (ikon + efek), tabib, kuil (dupa), perpustakaan (baca/salin/arsip) | sudah menginap, efek aktif |
| `yamen` | poin kejahatan, status (Dicurigai/Buronan), denda, [Serahkan diri], Barang Sitaan | sel: hitung mundur besar |
| `acara` (papan festival/lomba) | jadwal, skor Pertemuan Pil hidup (`PapanSkor`), bracket Turnamen Agung, teka-teki lentera (3 pilihan) | acara data (tidak ada pemain) → ringkasan hasil |
| `lapak_rekrut` | calon muda (potret, akar, bakat ringkas), peluang, [Undang] | aula lv < 2 |
| `jurnal` (Catatan Perjalanan) | quest aktif/selesai per tempat, penunjuk, ringkasan alur ibu kota per bab | belum ada quest |
| `gilda_alkemis` / `gilda_tempa` | ujian tingkat (syarat, centang), toko gilda, pesanan, penilaian | tingkat belum cukup |
| `kafilah` (sisi kota, misi) | kafilah data di jalan (posisi), [Kawal] | — |

#### 38.U.4 Teks dunia (billboard)

Papan kabar, poster buronan, kertas misi, tonggak jalan, papan nama toko dan nama di Batu Catatan digambar klien dengan
`Font` di dunia (pola teks billboard 36 A), hanya ≤ 24 blok, maks 12 papan bersamaan, panel 9-slice kertas di belakang
teks; poster buronan memuat potret kepala pemain (render kepala kulit) dan jumlah hadiah.

---------------------------------------------------------------------------------------------------------------------

### 38.D Performa, keamanan worldgen, perlindungan, simpanan, config, perintah, panduan dan peta

#### 38.D.1 Placement baru (38W, `dunia/tempat/worldgen/**`)

| Tipe (`kultivator:<id>`) | Kelas | Field (selain field vanilla `salt`, `frequency`, `exclusion_zone`, `locate_offset`) | Arti |
|---|---|---|---|
| `cincin_tempat` | `CincinPlacement extends RandomSpreadStructurePlacement` | `spacing`, `separation`, `min_chunks` (default 6), `cincin`: `[{sampai_chunk, frekuensi}]`, `jarak_tempat` (0-16, default 8) | seperti `jauh_dari_pusat` + frekuensi per cincin jarak (hash chunk < frekuensi) + menolak bila start set berpenghuni 38 lain ada dalam `jarak_tempat` chunk (`hasStructureChunkInRange` untuk set di tag `#kultivator:tempat_berpenghuni`, verifikasi cara mendapat `Holder<StructureSet>` dari `possibleStructureSets()`) |
| `rencana_tempat` | `RencanaPlacement` | `rencana` (`kota_besar`, `kompleks_sekte`, `jalan_titik`), `distrik`/`jenis` | start hanya di chunk yang ditentukan rencana deterministik (38.B.2.3) |
| `jalan` | `JalanPlacement` | — | chunk yang dilewati sisi jalan (38.C.6.2) |

Jenis struktur: `kultivator:tata_letak` (`TataLetakStructure`), `kultivator:jalan` (`JalanStructure`). Semua didaftarkan
di `TempatSpine.init()` **selalu** (38.0 butir 17); yang dimatikan config hanyalah pack worldgen.

#### 38.D.2 Keamanan worldgen dan validasi

- Hanya chunk baru; tidak ada `/place`; uji = locate + teleport (`DuniaCommands.findAndTeleport`) atau pratinjau hantu.
- Pack bawaan per modul lewat `DuniaPacks.worldgen(modul, on)`: `kultivator:dunia_kotabesar` (38KS), `dunia_kampung`,
  `dunia_kompleks` (38DS), `dunia_gua` (38GU), `dunia_jalan` (38W, termasuk set `pos/*`). Templat, pool, struktur dan
  processor selalu dimuat (data mati tidak berbiaya).
- **Validasi di repo** `tools/cek_tempat.py` (38W; dijalankan setiap generator dan di langkah finish): `structgen.validate_all`
  untuk semua NBT 38; ukuran potongan ≤ 48 × 48; jangkar ≤ 6 per potongan dan ≤ 40 per struktur/distrik; titik ≤ 96 per lot;
  setiap `pool` di JSON ada, setiap gaya punya pool atau jatuh ke dasar; grid lot setiap distrik lengkap (tidak ada lubang);
  salt unik di seluruh `data/**` dan `resourcepacks/**`; setiap set memakai placement 38 dengan `min_chunks`; struktur
  berpenghuni punya `spawn_overrides` monster kosong; blok terlarang di templat (`fire`, `soul_fire` bebas di atas tanah
  yang bisa menyebar, `lava`, `tnt`, `spawner`, `respawn_anchor` bermuatan, `campfire[lit=true]` di luar daftar putih
  dapur/stasiun alkimia, `lightning_rod`); anggaran gzip per tempat (38.C.10); perbandingan byte palet+blok templat kota
  lama sebelum/sesudah penambahan titik (harus identik kecuali kunci `kultivator_titik`).
- **Harness proyek** (langkah finish, di luar repo): `scratchpad/final/wg/run_all.sh` (TplCheck + WgCheck) diperluas dengan
  struktur dan set 38; harus "ALL OK".

#### 38.D.3 Penjaga dunia lama (`RencanaLama`, 38W)

- Tempat multi-start (11 distrik ibu kota, 1-3 distrik kompleks) tidak boleh lahir setengah di dunia yang sudah dijelajahi.
- Pada `ServerWorldEvents.LOAD` overworld (sebelum persiapan spawn), untuk setiap kandidat ibu kota dalam 12.000 blok dan
  setiap rencana kompleks: periksa apakah salah satu chunk start distriknya **sudah ada di berkas region** (baca header
  region, `RegionFile`/`RegionFileStorage` "punya chunk", verifikasi API 26.3; ±700 chunk, < 100 ms). Ada → kandidat
  **diblok** selamanya (SavedData `kultivator:tempat` `diblok`), dan `RencanaKotaBesar`/`RencanaKompleks` melewatinya
  (cincin pertama: kandidat berikutnya dalam urutan menjadi ibu kota pertama).
- Dunia baru (tidak ada region) → tidak ada pemeriksaan. Kandidat > 12.000 blok diperiksa saat pertama dibutuhkan server
  (peta/kabar) dan saat itu juga dicatat.
- Satu tempat jigsaw tunggal (kampung, gua, pos) mengikuti perilaku vanilla (start hanya di chunk baru; potongan ke chunk
  lama terpotong), seperti DESIGN 34.

#### 38.D.4 Zona bebas struktur (mixin, 38W)

- `mixin/ChunkGeneratorTempatMixin` (baru) pada `ChunkGenerator.tryGenerateStructure(...)` (privat, :572) `@Inject(at =
  HEAD, cancellable = true)`: bila `TempatKeepOut.tolak(seed, selected.structure(), sourceChunkPos, randomState,
  generator)` → `setReturnValue(false)`.
- Ditolak: struktur ber-`step` `surface_structures` yang **bukan** bagian rencana tempat itu, yang chunk start-nya di dalam
  zona ibu kota (kotak distrik + 3 chunk) atau kompleks (+2 chunk) yang **layak dan tidak diblok**; kecuali tag
  `#kultivator:boleh_di_tempat` (struktur jalan 38, sumur alam). Benteng (`strongholds`) dan struktur bawah tanah tidak
  pernah ditolak (mata ender tetap benar).
- Baris mixin di `kultivator.mixins.json` (pemilik 37G; 38W menambah satu baris **setelah** 37G mendarat, 38.E.2).

#### 38.D.5 Mode Kehancuran dan zona aman

- `TempatZones` (38W): satu `ProtectedZones.addZoneProvider` berbasis hash spasial per level: kotak ibu kota (dari rencana +
  situs yang sudah terbit), kompleks, kampung (jangkar `kampung/lonceng` protect 48-96), pos (protect 24), jangkar teka-teki
  dan segel gua; O(1) per panggilan, gagal = melindungi (aturan provider). Gua, situs alam dan jalan di luar zona = tanah
  biasa bagi Mode Kehancuran (W1).
- `TempatZones.safe` mengisi `DuniaApi.safeGround` (M2) untuk ibu kota, kampung, desa klan, pos, halaman kompleks
  Lurus/Netral (38.0 butir 11).
- Mode Kehancuran pemain sendiri tetap tidak bisa menghancurkan ibu kota/kampung; kerusakan di tepi zona tercatat 38.R.

#### 38.D.6 Ringkasan keamanan blok (diperiksa finish)

Yang **bukan** blok runtime: gerbang kota, pintu sel, pintu tebing, sangkar, lempeng glif (entitas); perancah, lentera,
kembang api, panji, asap, cahaya festival, papan teks (klien); tidur NPC (render, tanpa `startSleeping`); pertahanan kota
(tanpa creeper/enderman, zombie tanpa dobrak pintu); petir tidak pernah dibuat; cuaca/waktu tidak pernah diubah (uji
`cuaca` hanya pratinjau reaksi). Satu-satunya perubahan blok runtime 38: **perbaikan** lewat `WorldBreaker.submitRestore`
(38.R.3) dan kerusakan uji lewat pekerjaan uji WorldBreaker (op).

#### 38.D.7 Anggaran performa (laptop lemah; server dan klien)

| Hal | Anggaran | Penjaga |
|---|---|---|
| Worldgen | putusan sekali per kandidat (cache 64), jalan per sel (cache 64), placement O(1) | ≤ +6 % waktu chunk (`uji tempat ukur`) |
| Tubuh NPC 38 | global 64, per pemain 24, per tempat 38.N.7 | `DuniaBudget`, MSPT guard |
| Gol NPC | tiap 4/10 tick, jalur ≤ 8 per tick | `DuniaBudget.throttled` |
| Jangkar | ≤ 40 per distrik; `DuniaAnchors` 64 per tick | spine 34 |
| Impostor | ≤ 48 per pemain, payload 2 Hz bila berubah | `EffectQuality` |
| Penonton | ≤ 160 per arena (Maks), 1 batch | `EffectQuality` |
| Pindai 38.R | ≤ 4096 baca blok/tick; restore ≤ 8 blok/tick | MSPT jeda |
| Rekam 38.R | 2 chunk/tick | antrean |
| Payload | lembar layar ≤ 32 KB; snapshot ≤ 48 entri | 38UI |
| Klien | animasi penuh ≤ 32 b; lentera/kembang api lewat `EpicBudget.takeQuads` | `EffectQuality`, `ShaderCompat` |

`/kultivasi uji tempat ukur` mencetak: waktu rata-rata placement/struktur 38 per chunk baru (sampel 200 chunk), jumlah
tubuh/impostor/jangkar aktif, ms per tick bagian 38 (`hidup`, `perbaikan`, `acara`).

#### 38.D.8 Kompatibilitas simpanan

- SavedData baru (overworld): `kultivator:tempat` (rencana diblok, situs ibu kota), `kultivator:kota_besar` (kejahatan,
  rekening bank, brankas, pesanan tempa, Lencana, Daftar Naga Muda, Kas Kota, acara), `kultivator:tempat_penduduk`,
  `kultivator:tempat_cerita`, `kultivator:tempat_rusak`, `kultivator:tempat_kas`; berkas `world/kultivator/cetak/**`.
  Semua kunci baru; dunia lama tanpa kunci = nilai bawaan.
- `NpcRole` hanya **ditambah di akhir** (ordinal lama tetap); tubuh `noSave`, jadi tidak ada entitas lama yang rusak.
- `StructureStart` tata letak tersimpan sebagai potongan `JIGSAW` vanilla + id struktur `kultivator:*`; bila mod dicopot,
  vanilla membuang start yang tidak dikenal (log), blok tetap ada.
- Templat kota lama yang ditambah titik: blok identik (38.D.2), jadi kota yang sudah lahir tidak berubah.
- Item baru (senjata kota, hidangan, jimat lentera, surat wesel, lencana, trofi) didaftarkan selalu (K1).
- **Turun versi:** jar lama tidak mengenal `kultivator:tata_letak`/`jalan` → start dibuang saat chunk dimuat (blok tetap);
  catatan rilis meminta cadangan dunia.

#### 38.D.9 Config (`config/kultivator-dunia.json`, `DuniaConfig.section`, dijepit saat dimuat)

| Bagian | Kunci (bawaan) | Pemilik |
|---|---|---|
| `tempat` | `aktif` true, `tubuhGlobal` 64, `perPemain` 24, `debugTitik` false | 38W |
| `tempat.hidup` | 38.N.8 | 38NL |
| `kotaBesar` | `worldgen` true, `aktif` true, `jarakPertamaMin` 1800, `jarakPertamaLebar` 1400, `peluangSel` 0.6, `jarakAntar` 2500, `selisihMaks` 20, `tubuhMaks` 28, `jamMalam` true, `hukum` true, `acara` true, `gelombangBinatang` true, `alurCerita` true | 38KS |
| `ekonomi38` | `toko` true, `bank` true, `judi` true, `judiEv` 0.88 (0.80-0.92), `lapak` true, `kafilah` true, `komisiLelang` 0.10, `pengaliHarga` 1.0 (0.5-2) | 38EK |
| `kampung` | `worldgen` true, `aktif` true, `tubuhMaks` 10, `festivalPanen` true | 38DS |
| `kompleksSekte` | `worldgen` true, `aktif` true, `tubuhMaks` 12 | 38DS |
| `gua` | `worldgen` true, `aktif` true, `penjagaKristal` true | 38GU |
| `jalan` | `worldgen` true, `kelahiranPos` true, `begal` true | 38W |
| `perbaikan` | 38.R.7 | 38PR |
| `cerita` | `aktif` true, `rumorMaks` 24 | 38ST |

`/kultivasi uji jelajah muat` (spine) memuat ulang semua.

#### 38.D.10 Perintah

Semua uji di bawah `/kultivasi uji` (op, `DuniaCommands.test`; alias `/cultivate uji`); chip grup `tempat` ditambah ke
`DuniaCommands.CHIPS` (38W).

| Akar | Sub-perintah | Modul |
|---|---|---|
| `tempat` | `rencana` (daftar kandidat ibu kota, kompleks, layak/diblok), `cek` (validasi sisi server: titik, jangkar, zona di sekitar), `zona`, `kepadatan [radius]` (hitung tempat 38 per km² pada seed ini, tanpa membuat chunk: dari rencana + placement), `ukur`, `rusak|perbaiki|tagihan|takut|cetak|sitaan|gugus` (38.R.7), `lihat <folder/potongan>` | 38W, 38PR |
| `kota_besar` | `cari [indeks]`, `distrik <id>`, `lot`, `jam_malam [tutup|buka]`, `hukum <poin>`, `sel`, `acara <festival|pil|lelang|gerbang|turnamen|pertahanan|pertemuan> [mulai|akhir]`, `alur <bab>` (lompat bab), `teleport <tujuan>`, `istana`, + `rusak|perbaiki|tagihan` (alias 38.R) | 38KS, 38PR |
| `ekonomi` | 38.M.6 + `senjata <bentuk> <mutu>`, `pesanan selesai`, `lencana <1-9>`, `misi <pangkat>`, `bank`, `lapak`, `kafilah` | 38EK |
| `hidup` | 38.N.8 | 38NL |
| `model` | 38.O.5 | 38MD |
| `cerita` | `dialog <id>`, `quest <id> [tahap]`, `bendera <id> [on|off]`, `rumor [tambah <templat>]`, `tokoh` (lokasi tokoh berulang) | 38ST |
| `kampung` | `cari <ukuran> [gaya]`, `klan`, `panen`, `anak` | 38DS |
| `kompleks` | `cari <sekte>`, `rotasi` (bandingkan rotasi gerbang terhitung vs nyata), `sikap <sekte> <sikap>` (pratinjau) | 38DS |
| `gua` | `cari <jenis>`, `ujian <warisan|cermin|pertapa> [tahap]`, `penjaga` | 38GU |
| `jalan` | `cari`, `peta` (sisi di sekitar), `cek` (ujung jalan vs gerbang), `pos <jenis>` | 38W |
| `ui` | `layar <id>` (buka layar dengan lembar contoh), `skala <2|3|4>` (pratinjau tata letak) | 38UI |

Perintah pemain (tanpa op): `/kultivasi jurnal` (Catatan Perjalanan), `/kultivasi kas` (Buku Kas, pemilik aula),
`/kultivasi denda` (bayar denda bila di dekat Yamen/penjaga).

#### 38.D.11 Kitab Panduan

- Bab **18 "Kota Besar"** (indeks 17) dan bab **19 "Tempat-Tempat Dunia"** (indeks 18) ditambah di **akhir** daftar
  (`CHAPTER_COUNT` +2; id `c18.*`, `c19.*` di akhir agar bit `GuideSync` aman); urutan tampil (`displayOrder`, M1) setelah bab
  13 "Dunia yang Luas"; `minRealm` bab 18 = Pemurnian Qi, bab 19 = Fana (M1). Pengeditan `GuideChapters` oleh 38W **setelah**
  37G (bab 17) mendarat.
- Langkah bab 18: `c18.tiba` (adv `tempat/ibu_kota`), `c18.layanan` (pakai 3 layanan berbeda: flag), `c18.gilda` (Lencana
  1), `c18.lelang` (ikut Lelang Agung), `c18.hukum` (baca papan Yamen, opsional), `c18.acara` (ikut satu acara),
  `c18.takhta` (selesaikan bab 3 alur), `c18.teleport` (pakai stasiun).
- Langkah bab 19: `c19.kampung`, `c19.ikrar` (ikrarkan kampung 38, opsional), `c19.kompleks`, `c19.pertapa`,
  `c19.warisan`, `c19.sarang`, `c19.jalan` (sentuh 3 tonggak), `c19.perbaikan` (bayar tagihan atau bantu tukang).
- Teks `kultivator.guide.c18.<id>.title|body`, `chapter.18|19` dan `.desc` di `lang-src/tempat_panduan.*` (38ST).

#### 38.D.12 Peta dan penanda

- `DuniaMarkers` (38W menambah baris; ikon baru di `markers.png` lewat `tools/gen_dunia_peta_textures.py`, 38W satu-satunya
  penyunting di 38, ikon lama tidak diubah): `kota/kota_besar` (ikon 23, `#FFD27A`, zoom min 0), `kampung/dusun|desa|
  desa_besar` (24, `#B8A070`), `kampung/klan` (25, `#C08060`), `sekte/kompleks` (26, warna aliran), `gua/pertapa` (27),
  `gua/makam` (28), `gua/sarang_binatang` (29), `gua/sarang_iblis` (30, `#8A2030`), `gua/urat_roh` (31, `#B8A6F0`),
  `gua/cermin_hati` (32), `situs/puncak_petir` (33), `situs/panggung_bintang` (34), `situs/mata_air_roh` (35), `pos/singgah`
  (36), `pos/dagang` (37), `pos/tonggak` (38, zoom min 2), `pos/kemah_begal` (39), live `tempat/kafilah` (40),
  `tempat/acara` (41, ibu kota saat acara).
- **Jalan di peta:** 38W menambah satu kait `PetaPainter.addOverlay(...)` (aditif) yang menggambar sisi jalan yang sudah
  ditemukan (tonggak/ujung dikenal) sebagai garis tinta 1-2 px per kelas; ibu kota pada zoom dekat menampilkan denah 3 × 3
  distrik.
- Rumor dan Gulungan Penunjuk: jenis tempat 38 ditambahkan ke pencarian lewat kait publik yang disediakan M2 (bila
  `KedaiTeh`/`PetaItems` sudah punya pendaftar jenis); bila belum, 38W menambah satu baris pendaftaran di `KedaiTeh`
  setelah M2 mendarat (38.E.2).

#### 38.D.13 Advancement (`data/kultivator/advancement/tempat/*.json`, pola `docs/ADVANCEMENT_TEMPLATE.json`)

`ibu_kota` (masuk ibu kota), `warga_terhormat` (Nama Baik 50), `pahlawan_kota` (80), `buronan` (tersembunyi), `lelang_agung`
(menang lot), `judi_jackpot`, `alkemis_juara`, `lencana_9`, `naga_muda_pertama`, `penjaga_takhta` (alur selesai),
`pertahanan` (menang Gelombang Binatang), `kampung_ikrar`, `tuan_muda`, `tamu_sekte` (masuk aula dalam kompleks),
`murid_pertapa`, `pewaris_makam`, `pembebas` (bebaskan 5 tawanan), `peziarah_jalan` (10 tonggak), `kafilah_untung`,
`lapak_laris`, `tukang_sukarela` (100 poin bantu), `jujur` (bayar tagihan), `ditakuti` (tersembunyi: meter Takut 60).

#### 38.D.14 Fragmen lang (`lang-src/<fragmen>.id_id.json` + `.en_us.json`, kunci identik)

`tempat_inti` (38W), `tempat_kota_besar` (38KS), `tempat_ekonomi` (38EK), `tempat_hidup` (38NL), `tempat_model` (38MD:
nama prop/makhluk/senjata), `tempat_cerita`, `tempat_lore`, `tempat_kalimat`, `tempat_panduan` (38ST), `tempat_perbaikan`
(38PR), `tempat_ui` (38UI), `tempat_kampung` (38DS), `tempat_gua` (38GU). Setiap kunci tepat di satu fragmen (`mergeLang`).

---------------------------------------------------------------------------------------------------------------------

### 38.E Modul, kontrak, urutan kerja dan uji

#### 38.E.0 Prasyarat dan urutan

1. **Sebelum 38 mulai:** modul integrasi M1-M6 (`docs/progres/fix-M*.md`) sudah mendarat (`util/Sibuk`,
   `DuniaApi.safeGround`, `TechniquePower.markRealmFoe`, `Titipan`, kurs `NilaiBatu {1, 9, 81, 729}`,
   `Disciples.addFollowVeto`, `displayOrder`/`minRealm` panduan), 36 selesai, dan 37 (37G pemilik
   `kultivator.mixins.json`, `GuideChapters`, `CheatHelp`) sudah mendarat atau dijadwalkan sebelum baris 38 di berkas itu.
2. **Langkah 1 (tulang punggung, berurutan, ±1 sesi):** 38W mendaratkan `dunia/tempat/**` dengan stub bertanda tangan
   lengkap (semua kontrak C38.* di bawah, badan kosong yang aman), `Tempat.init()` dengan satu baris komentar per modul,
   jenis struktur/placement terdaftar, `tools/tempat_lib.py`, `tools/cek_tempat.py`, `Template.titik` di `tools/nbt.py`;
   38UI mendaratkan `TempatLayar` (payload + registri); 38NL mendaratkan `HidupApi` + konstanta `PropId` + `NpcRole`
   baru (agar semua modul bisa mereferensikan peran); setiap pemilik entitas mendaratkan **stub `EntityType`** (38GU
   `GuaEntities`, 38KS `KotaBesarEntities`, 38EK `EkonomiEntities`, 38W `JalanEntities`) agar 38MD bisa mendaftarkan
   renderer.
3. **Langkah 2:** sepuluh modul bekerja paralel, masing-masing hanya pada berkasnya (38.E.1-38.E.2); setiap modul
   menjaga `docs/progres/kota-<modul>.md` (checklist + langkah berikutnya, aturan progres pengguna).
4. **Langkah 3 (finish):** jalankan generator Python (NBT, JSON, tekstur, model, `gen_voices.py` dengan VOICEVOX untuk
   baris baru), `tools/cek_tempat.py`, harness `scratchpad/final/wg/run_all.sh`, `mergeLang`, lalu satu langkah Gradle
   (`compileJava`, `compileClientJava`, `jar`, `--no-daemon`). Tidak ada tes, `runClient` atau `gradlew build`.

#### 38.E.1 Sepuluh modul (berkas baru tidak bertumpuk)

| Modul | Lingkup | Paket/berkas baru (milik penuh) | Generator, data, lang |
|---|---|---|---|
| **38W** Inti, rencana, jalan & pos | 38.B.2.2-38.B.2.4, 38.C.1, 38.C.6, 38.C.9-10, 38.D (kecuali yang disebut lain) | `dunia/tempat/{Tempat, TempatSpine, TempatApi, TempatInfo, TempatConfig, TempatCommands, TempatZones, TempatKeepOut, RencanaRegistry, RencanaKotaBesar, PutusanTanah, RencanaLama, LotPick, TitikTemplat, TempatNames}.java`, `dunia/tempat/worldgen/{CincinPlacement, RencanaPlacement, TataLetakStructure, TempatTypes}.java`, `dunia/tempat/jalan/**` (`RencanaJalan`, `JalanPlacement`, `JalanStructure`, `PosAnchors`, `Tonggak`, `KuilJalan`, `Penyeberangan`, `RakitEntity`, `JalanEntities`), `mixin/ChunkGeneratorTempatMixin.java`, `client/dunia/tempat/TempatClient.java`, `client/dunia/tempat/jalan/JalanPeta.java` | `tools/{tempat_lib, tempat_palet, gen_tempat_jalan, cek_tempat}.py`; `data/kultivator/worldgen/**/{jalan,pos}/**`, `structure/{jalan,pos}/**`, tag `tempat_*`, `resourcepacks/dunia_jalan`; `lang-src/tempat_inti.*`; advancement `tempat/peziarah_jalan` |
| **38KS** Kota Besar: worldgen, sipil, acara | 38.B (kecuali 38.B.7 isi NPC), 38.B.4-38.B.6 | `dunia/tempat/kotabesar/**` (`KotaBesar`, `KotaBesarData`, `KotaBesarNames`, `KotaBesarAnchors`, `KotaBesarEntities`, `GerbangKotaEntity`, `JamMalam`, `Hukum`, `Istana`, `Teleport`, `Kuil`, `Perpustakaan`, `MenaraPengamat`, `BalaiTabib`, `KotaBesarKalender`, `acara/{FestivalLentera, PertemuanPil, HariGerbang, TurnamenAgung, GelombangBinatang, PertemuanSekte}`, `KotaBesarCommands`), `client/dunia/tempat/kotabesar/**` (lentera langit, kembang api, panji, adegan acara) | `tools/gen_tempat_kota_besar.py`; `structure/kota_besar/**`, `worldgen/**/kota_besar/**`, `resourcepacks/dunia_kotabesar`; `lang-src/tempat_kota_besar.*`; advancement ibu kota/acara |
| **38EK** Lingkar uang | 38.M seluruhnya, layanan ekonomi 38.B.3 | `dunia/tempat/ekonomi/**` (`Ekonomi`, `EkonomiEntities` (gerobak), `SenjataKota`, `Hidangan`, `TokoSenjata`, `PesananTempa`, `RumahUang`, `SuratWesel`, `Gadai`, `Brankas`, `JudiBatu`, `PasarBinatang`, `Penginapan`, `Jasa`, `GildaAlkemis`, `GildaTempa`, `GildaKultivator`, `MisiGilda`, `Lencana`, `DaftarNagaMuda`, `LelangAgung`, `ProfilLelang`, `BukuKas`, `LapakSekte`, `KafilahSekte`, `KafilahJalan`, `KasKota`, `EkonomiApi`, `EkonomiCommands`) | tag item kelas senjata (data), `loot_table` lot Agung; `lang-src/tempat_ekonomi.*`; advancement ekonomi |
| **38NL** Kehidupan NPC & animasi | 38.N | `dunia/tempat/hidup/**` (`HidupApi`, `PropId`, `Penduduk`, `PendudukGen`, `Jadwal`, `Kegiatan`, `TubuhPenduduk`, `Pemanggil`, `PendudukData`, `Saksi`, `HidupConfig`, `HidupCommands`, `PendudukSnapshot`), `client/dunia/tempat/hidup/**` (`NpcKlip`, `KlipKatalog`, `NpcKlipPlayer`, `NpcPropLayer`, `TatapKepala`, `ImpostorRenderer`, `PenontonRenderer`) | `lang-src/tempat_hidup.*` |
| **38MD** Model & tekstur | 38.O | `client/dunia/tempat/model/**` (semua `*Model`, `*Renderer` entitas 38, `NpcFaceLayer`, `ImpostorModel`, `RakSenjataRenderer`, `KainKota`, `LenteraLangitModel`, `ArmilariRenderer`, `KincirRenderer`), `client/dunia/tempat/model/TempatModelClient.java` | `tools/{gen_tempat_models, gen_tempat_gear, gen_tempat_textures, tempat_iso}.py`; `assets/kultivator/{models,items,textures}/**/tempat/**`, `textures/dunia/tempat/**`; `lang-src/tempat_model.*` |
| **38ST** Cerita & suara | 38.S, 38.B.8, 38.D.11 teks | `dunia/tempat/cerita/**` (`CeritaApi`, `CeritaData`, `Dialog`, `DialogNode`, `Syarat`, `Akibat`, `Quest`, `QuestTracker`, `Tokoh`, `KabarTempat`, `AlurTakhta`, `TempatDialog`, `CeritaReload`, `CeritaCommands`), `client/dunia/tempat/cerita/AdeganCerita.java` | `data/kultivator/tempat/{dialog,quest,kabar}/**`; `lang-src/tempat_{cerita,lore,kalimat,panduan}.*`; baris suara di `gen_voices.py` |
| **38PR** Perbaikan & ganti rugi | 38.R | `dunia/tempat/perbaikan/**` (`Perbaikan`, `Cetak`, `CetakStore`, `Rekam`, `PindaiTempat`, `LogAtribusi`, `Gugus`, `PekerjaanPerbaikan`, `Tukang`, `Tagihan`, `Takut`, `Sitaan`, `NilaiBlok`, `KlaimPemain`, `PerbaikanApi`, `PerbaikanConfig`, `PerbaikanCommands`), `client/dunia/tempat/perbaikan/Perancah.java` | `lang-src/tempat_perbaikan.*`; advancement `jujur`, `tukang_sukarela`, `ditakuti` |
| **38UI** Kit & layar | 38.U | `dunia/tempat/ui/{TempatLayar, TempatLayarPayload, TempatAksiPayload}.java`, `client/tempat/ui/kit/**`, `client/tempat/ui/layar/**`, `client/tempat/ui/{UiTempatClient, UiHub, TeksDunia}.java` | `tools/gen_tempat_ui.py`; `textures/gui/tempat/**`; `lang-src/tempat_ui.*` |
| **38DS** Kampung, desa klan, kompleks sekte | 38.C.2, 38.C.3, 38.C.7 | `dunia/tempat/kampung/**` (`Kampung`, `KampungAnchors`, `KepalaDesa`, `FestivalPanen`, `Klan`, `KampungApi`, `KampungCommands`), `dunia/tempat/kompleks/**` (`RencanaKompleks`, `RotasiGerbang`, `KompleksAnchors`, `KompleksSikap`, `PasarSekte`, `KompleksApi`, `KompleksCommands`) | `tools/{gen_tempat_kampung, gen_tempat_kompleks}.py`; `structure/{kampung,kompleks_sekte}/**`, worldgen terkait, `resourcepacks/{dunia_kampung,dunia_kompleks}`; `lang-src/tempat_kampung.*` |
| **38GU** Gua & situs | 38.C.4, 38.C.5, hubungan langit 38.A #20-28 | `dunia/tempat/gua/**` (`Gua`, `GuaEntities`, `GuaAnchors`, `PertapaRun`, `MakamRun`, `SarangBinatang`, `SarangIblis`, `GuaUratRoh`, `CerminHati`, `PuncakPetir`, `PanggungBintang`, `MataAirRoh`, `PenjagaKristalEntity`, `BonekaPenjagaEntity`, `SangkarEntity`, `LempengGlifEntity`, `PintuTebingEntity`, `TikusRohEntity`, `GuaApi`, `GuaCommands`) | `tools/gen_tempat_gua.py`; `structure/{gua,situs}/**`, worldgen terkait, `resourcepacks/dunia_gua`; `lang-src/tempat_gua.*` |

#### 38.E.2 Pemilik berkas lama (satu pemilik di 38; semua suntingan aditif)

| Berkas lama | Pemilik 38 | Suntingan | Syarat waktu |
|---|---|---|---|
| `dunia/DuniaContent.java` | 38W | satu baris `dev.kultivator.dunia.tempat.Tempat.init();` | — |
| `client/dunia/DuniaContentClient.java` | 38W | satu baris `TempatClient.init()` | — |
| `dunia/DuniaMarkers.java`, `tools/gen_dunia_peta_textures.py` (+ `markers.png` hasilnya) | 38W | jenis penanda 23-41 | setelah M2 (M2 menyunting `DuniaMarkers`) |
| `dunia/DuniaCommands.java` | 38W | chip grup `tempat` di `CHIPS` | — |
| `client/dunia/peta/PetaPainter.java` | 38W | `addOverlay` publik | — |
| `kultivator.mixins.json` | 38W | satu baris `ChunkGeneratorTempatMixin` | setelah 37G |
| `guide/GuideChapters.java` | 38W | bab 18-19 di akhir | setelah 37G dan M1 |
| `dunia/kota/KedaiTeh.java` | 38W | satu baris pendaftaran jenis kabar 38 (hanya bila M2 tidak menyediakan registri) | setelah M2 |
| `tools/nbt.py` | 38W | `Template.titik` + kunci akar `kultivator_titik` | — |
| `dunia/kota/Towns.java` | 38KS | jenis `kota/kota_besar`, `SAFE_RADIUS` per jenis | setelah M2 |
| `dunia/kota/BalaiLelang.java`, `dunia/kota/PapanMisi.java` | 38EK | `mulai(..., ProfilLelang)`; pangkat E-S + kait poin gilda | setelah M3 |
| `alchemy/AlchemyFurnace.java` | 38EK | `addFinishListener` publik | — |
| `tempa/Forging.java` | 38EK | `buatStok(...)`, API pesanan | — |
| `dunia/kota/KultivatorNpc.java`, `NpcRole.java`, `NpcAi.java`, `NpcLife.java`; `client/dunia/kota/NpcPoses.java`, `NpcRenderer.java` | 38NL | data animasi, peran di akhir enum, gol titik, delegasi jadwal, kait klip, lapisan wajah/prop, tidur, skala anak | setelah M2/M4 (yang menyentuh `KultivatorNpc` untuk `markRealmFoe`) |
| `client/dunia/kota/KotaGear.java`, `KotaGearLayer.java`, `textures/dunia/kota/entity/perlengkapan.png` | 38MD | perlengkapan pita 16-31 | — |
| `dunia/kota/Kota.java` | 38ST | satu baris kait dialog di awal `interact` | setelah M3 |
| `tools/gen_voices.py`, `voice/VoiceEvents.java` (+ `sounds.json`, `.ogg`, `lang-src/voice.*` yang dihasilkan) | 38ST | baris `kotabesar.*`, `tempat.*`, `cerita.*` | — |
| `kehancuran/WorldBreaker.java`, `kehancuran/ProtectedZones.java` | 38PR | `submitRestore`, `addChangeListener`; `addClaimProvider`, `claimed` | — |
| `cultivation/CaveAbode.java`, `desa/Desa.java` | 38PR | satu baris pendaftaran penyedia klaim | — |
| `desa/Tribute.java` | 38DS | `addBonusProvider` | — |
| `tools/gen_dunia_kota.py` (+ NBT `structure/kota/**` yang dihasilkan ulang, blok identik) | 38DS | panggilan `titik(...)` saja | setelah 38W langkah 1 |
| `cultivation/Tribulation.java`, `trial/HeartDemons.java`, `technique/source/InheritanceTrials.java`, `purba/StarCalamity.java`, `beast/SpiritBeasts.java`, `dunia/langit/TreasureBirths.java`, `dunia/langit/GreatMeteors.java`, `dunia/langit/LangitItems.java` | 38GU | satu kait faktor/penyedia per berkas (`TempatApi`/`GuaApi`), dibatasi 38.0 butir 4 | setelah M2 (HeartDemons, TreasureBirths) |

**Tidak disunting oleh 38:** `network/Payloads.java`, `Net.java`, `KultivatorConfig.java`, `CultivateCommand.java`,
`HubCardsPage.java`, semua berkas `sekte/**` (36), berkas 37, `docs/DESIGN.md`, `sect/build/**`, `musuh/SectRaids.java`.

#### 38.E.3 Kontrak antarmodul (nama tetap; stub di langkah 1)

- **C38.W1 `TempatApi`** (38W): `@Nullable TempatInfo tempatDi(ServerLevel, BlockPos)` (`TempatInfo(long situsId, String
  jenis, BoundingBox kotak, int kekuatan, @Nullable String distrik)`), `List<TempatInfo> dekat(ServerLevel, BlockPos, int r,
  String awalanJenis)`, `@Nullable BlockPos arrival(DuniaSites.Site)`, `@Nullable BlockPos groundSpot(ServerLevel, int x, int z,
  int nearY)`, `@Nullable BlockPos spot(String sekteId, String titik)`, `boolean viewing(ServerPlayer, String apa)`,
  `List<PotonganInfo> potongan(ServerLevel, long situsId)` (`PotonganInfo(Identifier templat, BlockPos pos, Rotation rot,
  BoundingBox kotak)`), `List<Titik> titik(ServerLevel, long situsId, String jenis)`, `double faktor(ServerPlayer, String
  kunci)` (faktor tempat terbatas untuk kait GU: `kesengsaraan`, `iblis_hati`, `warisan`, `bintang`, `binatang_xp`).
- **C38.W2 `RencanaRegistry`**: `register(String id, Rencana r)`; `interface Rencana { boolean isStart(long seed, int cx, int
  cz, String distrik); @Nullable Distrik distrikDi(long seed, int cx, int cz); List<BoundingBox> zona(long seed); }`.
- **C38.UI1 `TempatLayar`**: `buka(ServerPlayer, String layar, int npcId, CompoundTag lembar)`, `daftar(String layar,
  Penangan)`; kunci lembar per layar ditulis di javadoc kelas pembangun lembar milik modul gameplay.
- **C38.NL1 `HidupApi`**: `@Nullable KultivatorNpc tubuh(ServerLevel, long situsId, NpcRole, BlockPos, float yaw)` (tubuh
  layanan/tukang/penagih, ikut topi), `void klip(KultivatorNpc, int klip, int prop, int tick)`, `void tatap(KultivatorNpc,
  @Nullable Entity)`, `void timpaJadwal(long situsId, String acara, long sampai)`, `List<Penduduk> penduduk(long situsId)`,
  `boolean saksi(ServerLevel, BlockPos, ServerPlayer pelaku)`, `void bahaya(ServerLevel, BlockPos, int detik)`.
- **C38.ST1 `CeritaApi`**: `bendera(ServerPlayer, String)`, `setBendera(...)`, `int tahap(ServerPlayer, String quest)`,
  `void kabar(ServerLevel, @Nullable Long situsId, String templat, Object... isian)`, `boolean dialog(ServerPlayer,
  KultivatorNpc, String dialogId)`, `void peristiwa(ServerPlayer, String jenis, String id)` (tujuan quest dari modul lain).
- **C38.EK1 `EkonomiApi`**: `boolean bayar(ServerPlayer, long nb, String alasan)`, `void beri(ServerPlayer, long nb, String
  alasan)`, `long harga(ItemStack)`, `void kas(UUID pemilikAula, String sumber, boolean pk, long jumlah, String catatan)`,
  `void kasKota(long situsId, long jumlah)`, `int lencana(ServerPlayer)`.
- **C38.KS1 `KotaBesarApi`**: `int pangkatNamaBaik(ServerPlayer, long town)`, `void kejahatan(ServerPlayer, long town, String
  jenis, int poin)`, `boolean buronan(ServerPlayer, long town)`, `@Nullable String acaraAktif(long situsId)`.
- **C38.PR1 `PerbaikanApi`**: `int takut(long situsId)`, `boolean adaKerusakan(long situsId)`, `@Nullable Tagihan
  tagihanTerbuka(ServerPlayer)`.
- **C38.DS1 `KampungApi`/`KompleksApi`**: `@Nullable String sekteDi(long situsId)`, `int kekuatan(long situsId)`.
- **C38.GU1 `GuaApi`**: `boolean dalamUji(ServerPlayer)`, `@Nullable BlockPos makamTerdekat(ServerLevel, BlockPos)`.
- **C38.MD1**: indeks prop = `PropId` (38NL) ↔ model `kultivator:fx/tempat/<id>` (38MD); id klip = `KlipKatalog` (38NL);
  wajah/skala dibaca dari `DATA_LOOK`/peran.
- Peristiwa lintas modul memakai `DuniaEvents` yang ada + satu bus baru `TempatEvents` (38W): `TEMPAT_RUSAK(level, situsId,
  gugus)`, `TAGIHAN(player, situsId, jumlah, dibayar)`, `ACARA(level, situsId, acara, mulai)`, `KEJAHATAN(player, town,
  jenis)`, `QUEST(player, id, tahap)`.

#### 38.E.4 Uji terima: bukti konten nyata (setiap baris harus terlihat oleh penguji)

| Fitur | Bukti yang harus terlihat | Cara cepat |
|---|---|---|
| Ibu kota | 9 distrik + 2 luar lengkap, tembok + 3 gerbang, tidak ada lubang, tidak ada struktur vanilla di dalam | `uji kota_besar cari`, terbang keliling |
| Lot dan interior | setiap bangunan layanan punya interior lengkap dan NPC layanannya | `uji kota_besar distrik <id>` |
| Gaya bioma | atap/dinding berbeda di salju, gurun, sakura | seed contoh di `uji tempat rencana` |
| Kampung 7 gaya × 4 ukuran | lonceng, kuil Dewa Tanah, ladang, villager vanilla + NPC 38; Plakat Pelindung Sekte bisa dipasang | `uji kampung cari desa salju` |
| Kompleks sekte | aula, lapangan latihan dengan murid berlatih, paviliun, puncak; nama/lambang sekte D | `uji kompleks cari <sekte>` |
| Gua 6 jenis + situs 3 | ruang, tokoh/makhluk, uji berjalan | `uji gua cari <jenis>` |
| Jalan | ubin mengikuti tanah, jembatan di sungai, tonggak bertulisan, kuil jalan, pos singgah | `uji jalan cari`, `uji jalan cek` |
| Kehidupan NPC | jam 05/07/12/18/22 menunjukkan kegiatan berbeda (sapu, kerja, makan, teh, tidur di kasur) | `uji hidup jam <h>` |
| Animasi | 78 klip tampil tanpa patah, prop di tangan benar | `uji hidup klip semua` |
| Reaksi | hujan → payung; bahaya → lari ke pintu; ranah tinggi → membungkuk | `uji hidup cuaca hujan`, `uji hidup bahaya` |
| Impostor | keramaian jauh di ibu kota dan tribun penuh | dari Menara Pengamat |
| Model | semua makhluk, prop 1-40, 28 senjata, gerbang membuka | `uji model pamer|prop|senjata`, `uji kota_besar jam_malam tutup` |
| Cerita | dialog bercabang Bibi Hong; alur bab 1-7 dapat dilompati dan menampilkan adegan | `uji cerita dialog bibi_hong_ah_lun`, `uji kota_besar alur <bab>` |
| Rumor | rumor berubah setelah Gerhana Darah dan setelah tagihan dibayar | `uji kota langit blood_eclipse`, lalu dengar obrolan |
| Suara | 30 baris ibu kota + 24 baris tempat terdengar (Jepang) dengan subtitel ID/EN | interaksi |
| Perbaikan | kerusakan uji diperbaiki tukang beranimasi, blok kembali sesuai rekaman | `uji tempat rusak 40 pemain` → tagihan muncul |
| Takut | ranah jauh di atas: NPC berlutut, perbaikan lambat, anak bersembunyi | `uji tempat takut 70` |
| Belanja | beli jian roh menengah, pratinjau 3D, banding stat | layar toko |
| Lelang terpadu | Lelang Agung 8 lot, penawar NPC bernama, titipan bertahan setelah restart | `uji kota_besar acara lelang mulai` |
| Misi E-S | papan berpangkat, misi `begal` menunjuk kemah nyata | `uji ekonomi misi C` |
| Buku kas | kolom PK dan Batu terpisah, baris lapak/kafilah | `uji ekonomi lapak 3` |
| UI | semua layar pada skala 2/3/4, keyboard saja | `uji ui skala 3`, `uji ui layar <id>` |

#### 38.E.5 Daftar uji (Indonesia, untuk `docs/uji/FASE-38.md`)

1. Dunia baru: `uji tempat rencana` menyebut ibu kota pertama 1800-3200 blok dari pusat dengan status "layak"; `uji kota_besar
   cari` membawa ke sana; ke-11 distrik ada, tidak ada desa vanilla atau kota lain di dalam tembok.
2. Dunia lama (dibuat sebelum 38, sudah dijelajah 3000 blok): kandidat yang chunk-nya sudah ada berstatus "diblok"; tidak
   ada distrik setengah jadi di mana pun; ibu kota lahir di kandidat berikutnya yang masih baru.
3. Terbang di atas ibu kota: jalan antar-lot rapi, tembok dan menara bersambung di empat sisi, Gerbang Selatan Agung
   menghadap Lapangan Kafilah, jalan Kekaisaran keluar dari gerbang.
4. Pukul 22:00 genderang berbunyi dan gerbang menutup (daun berputar, bukan blok); minta penjaga membuka (2 NB).
5. Lukai warga di depan penjaga: Dicurigai lalu Buronan (poster dengan wajah pemain di papan); bayar denda di Yamen; ulangi dan
   pilih Serahkan Diri: masuk sel, hitung mundur, murid tidak ikut, keluar bersih.
6. Rumah Uang: setor, tarik (biaya 1 %), buat Surat Wesel lalu tukarkan; gadai pusaka lalu tebus.
7. Judi batu: 10 batu Kerikil; animasi belah; hasil wajar; `uji ekonomi judi 100000` → EV 0.86-0.90.
8. Paviliun Seribu Bilah: pratinjau 3D berputar, stat dibanding pedang di tangan; beli jian; Duel Pedang mengenalinya
   sebagai PEDANG.
9. Pesan pusaka mutu 3 ke Empu Ou Ye, tunggu 3 hari dunia (restart server di tengah): pesanan tetap ada dan bisa diambil.
10. Lelang Agung: 8 lot, Gu Lianshan ikut menawar; titip barang, hentikan server saat lelang, masuk lagi: titipan dibayar
    atau dikembalikan.
11. Gilda Kultivator: terima misi C `begal`; penunjuk ke kemah begal nyata di tepi jalan; tikungan moral muncul (20 %).
12. Buku Kas: sewa lapak, isi barang, tunjuk murid penjaga; esok hari ada penjualan; kolom PK dan Batu terpisah; kirim
    kafilah ke kota lain, pulang dengan laba ≤ 30 %.
13. Kehidupan: `uji hidup jam 5|7|12|18|22` di hunian: menyapu, kerja, makan, minum teh, tidur di kasur (kasur tidak berubah
    status `OCCUPIED`); anak bermain, tetua main catur; pedagang berseru.
14. Hujan (alami): payung kertas; badai: anak bersembunyi; Gerhana Darah: jalan kosong, penjaga ganda.
15. Festival Lentera (bulan purnama): lentera langit naik, teka-teki benar memberi Jimat Lentera dan Nama Baik +1.
16. Gelombang Binatang: gerbang tertutup, monster dari luar tembok saja, tidak ada creeper/enderman, tidak ada blok runtuh;
    menang → hadiah dari Kas Kota.
17. Turnamen Agung: juara sekte NPC bernama sekte D; babak pemain lewat Duel Pedang; tribun penuh impostor.
18. Alur "Bayangan di Balik Takhta": bab 1-7 (pakai `uji kota_besar alur <bab>`), ketiga cabang bab 5 menghasilkan bab 6-7
    yang berbeda.
19. Kampung: lonceng ada, Plakat Pelindung Sekte bisa diikrarkan (sistem Desa lama), Festival Panen terjadi pada hari 12;
    desa klan: Tuan Muda menantang pemain berranah rendah.
20. Kompleks sekte: masuk dengan sikap NETRAL hanya halaman; sekutu boleh tanding latih; perang → murid bermusuhan.
21. Gua Pertapa: tiga ujian lintas hari, teknik diajarkan, pertapa pergi, kursi bisa jadi Gua Kediaman.
22. Makam Warisan: penghalang menolak ranah rendah; uji Hati/Kekuatan/Pemahaman; murid menunggu di luar.
23. Puncak Petir: tribulasi di sana tercatat di Batu Catatan; kerusakan ×0.90 terlihat di log uji.
24. Jalan: ikuti jalan dari ibu kota ke kota terdekat: ubin, jembatan di sungai, tonggak setiap ±500 blok, kuil jalan, pos
    singgah; jalan tergambar di peta setelah menyentuh tonggak.
25. Perbaikan: `uji tempat rusak 40 pemain` di kampung → Kepala Desa datang dengan tagihan; Bayar → tukang ×4 cepat; ulangi,
    Tolak → Nama Baik turun, harga +10 %. Naikkan ranah (uji) jauh di atas: tanpa tagihan, NPC berlutut, perbaikan lambat,
    anak bersembunyi. Pasang peti berisi di dalam rumah lalu rusak dinding di sebelahnya: peti tidak pernah dibuang.
26. Mode Kehancuran pemain di dalam ibu kota/kampung: tidak menghancurkan apa pun (zona); di tepi zona kerusakan tercatat.
27. UI: semua layar di skala GUI 2, 3, 4 dan jendela 320 × 240, navigasi hanya keyboard, tidak ada teks di bawah 0.6.
28. Performa: `uji tempat ukur` di laptop uji: waktu chunk baru ≤ +6 %, MSPT di tengah ibu kota tidak melebihi patokan +4 ms;
    dengan Efek Minimal tidak ada lentera/impostor.
29. Keamanan: tidak ada `LightningBolt`, cuaca/waktu tidak berubah, satu-satunya perubahan blok runtime adalah restore 38.R
    lewat WorldBreaker (grep finish).
30. Simpanan: keluar-masuk dunia, restart server: rekening, pesanan, lencana, buku kas, alur cerita, kerusakan dan tagihan
    tetap; mematikan `kotaBesar.worldgen` tidak merusak dunia yang sudah punya ibu kota.

