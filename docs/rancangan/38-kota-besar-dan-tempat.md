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

---------------------------------------------------------------------------------------------------------------------

## 38.T Kota Bertema

Permintaan pengguna (2026-10-23, bahasa Indonesia, mengikat): "Tambah variasi kota: kota bertema. Lanjut, tapi
detail." Lima jenis: (1) **Kota Pelabuhan** di pantai: kapal dagang di dermaga, pasar ikan, mercusuar, galangan kapal,
kampung pelaut, quest dagang laut, tautan ke dimensi Laut Tanpa Batas dan dermaga kapal terbang; (2) **Kota Oasis
Gurun**: kafilah unta, bazar, istana pasir, kuil air, badai pasir, quest reruntuhan gurun; (3) **Kota Air** (kota kanal
seperti Suzhou/Zhouzhuang): kanal, perahu sebagai entitas yang bergerak, jembatan lengkung, taman, dagang sutra dan teh,
festival lentera di atas air; (4) **Kota Gunung**: teras bertingkat di lereng, tangga batu, jalan tebing, kuil puncak,
platform kereta gantung/katrol sebagai entitas, quest petik herba dan ziarah; (5) **Kota Iblis**: kota kultivator jalan
iblis, arena darah, pasar gelap dan pasar tawanan, toko teknik terlarang, gerbang ranah tinggi dan aturan bahaya,
perburuan hadiah oleh sekte lurus.

Preferensi pengguna yang mengikat bagian ini: **selalu dunia baru** (tidak ada migrasi atau kompatibilitas simpanan yang
dikejar), semuanya **konten nyata** (struktur detail, model 3D, animasi detail, cerita bertokoh dan rantai quest, GUI
segar), world generation **hanya di chunk baru**, perubahan blok runtime **hanya** lewat `WorldBreaker`/`BuildPlacer`,
mekanisme sebagai **entitas**, anggaran **laptop lemah**. Bagian ini memakai seluruh mesin 38 (tata letak 38.B.2, NPC
38.N, model 38.O, cerita 38.S, perbaikan 38.R, uang 38.M, UI 38.U, rekayasa 38.D) dan **tidak** membuat mesin kedua.

Urutan: 38.T.0 aturan, 38.T.1 ringkasan, 38.T.2 rencana/putusan/kepadatan, 38.T.3-38.T.7 lima kota (lore dan Sumber,
distrik dan bangunan, NPC dan rutinitas, permainan khas, tokoh dan quest), 38.T.8 klip animasi baru, 38.T.9 model,
38.T.10 dagang antar-kota dan harga, 38.T.11 tautan ke sistem lain, 38.T.12 suara, 38.T.13 config dan perintah,
38.T.14 modul dan kepemilikan berkas, 38.T.15 uji terima.

### 38.T.0 Aturan dan fakta tambahan

1. **Semua aturan 38.0 butir 1-18 berlaku.** Tambahan: tidak ada blok runtime baru; kanal, dermaga, tangga, arena, sel
   tawanan dan semua bangunan lahir dari worldgen; yang bergerak (kapal, perahu, unta, kereta gantung, katrol, pintu air,
   derek, timba sumur, alat tenun, pintu arena, tandu) adalah **entitas** `noSave` yang dimunculkan ulang dari jangkar
   atau titik NBT (pola `KotaPintuEntity`, `KapalDekEntity`, `ColliderEntity`).
2. **Dunia baru.** Tidak ada kode migrasi khusus tema. Rencana tema terdaftar di `RencanaRegistry` sehingga penjaga
   `RencanaLama` (38.D.3) berlaku otomatis tanpa kode tambahan (biaya nol di dunia baru).
3. **Satu sumber per sistem.** Toko, bank, papan misi, lelang, teleport, penginapan, tabib, Nama Baik, hukum, perbaikan
   dan kafilah memakai layanan 38 yang sudah ada lewat jangkar bersama (`kota/*`, `kotabesar/layanan`,
   `kotabesar/cakram`) atau API pendaftaran; 38.T hanya menambah layanan yang **khas tema** (38.T.10).
4. **Fakta kode (audit 2026-10-23, verifikasi ulang saat implementasi):**
   - `util/Sibuk` di 38.0 butir 10 sebenarnya **`musuh/Sibuk.java`**; 38.T memanggil kelas itu.
   - `dunia/dimensi/laut/SeaGate`: `public static boolean enter(ServerPlayer player, boolean free)` (:120),
     `public static @Nullable Component blocker(ServerPlayer)` (:104), `leave` (:185), rute kapal `ROUTE = "laut"`;
     `DimensiLevels.LAUT` (`kultivator:laut_tanpa_batas`, permukaan laut y 99, dermaga tiba di asal).
   - `dunia/kapal/KapalClass`: `PERAHU` (perahu_awan, Inti Emas), `SEKTE` (kapal_sekte, Jiwa Baru Lahir), `BAHTERA`
     (bahtera_langit, Transformasi Dewa); `Voyages.ROUTE_CROSSING = 2000`; `KapalBajakEntity` (perompak langit).
   - `musuh/RogueCultivators`: `VARIANT_DEMONIC = 3`, peran `ROLE_AMBUSH/RAID/CAMP/QUEST/REALM/TEST`;
     `TechniquePower.markRealmFoe(LivingEntity)` (:699).
   - mcsrc 26.3: `world/entity/animal/camel/Camel` (+ `CamelHusk`), `equine/Llama`, `TraderLlama`,
     `vehicle/boat/Raft`; bioma termasuk `dappled_forest`, `pale_garden`, `sulfur_caves` (bawah tanah).
5. **Bahaya hanya PvE.** Kota Iblis tidak pernah memaksa PvP (aturan 2 DESIGN 0): "hukum yang kuat" di sana berlaku
   antara pemain dan NPC, tidak antar-pemain. **Tidak ada manusia yang dijual kepada pemain**: pasar tawanan hanya bisa
   *ditebus dan dibebaskan* (38.T.7.6).

### 38.T.1 Ringkasan lima tema

| Tema (`kota_tema/<id>`) | Nama contoh (kota pertama) | Ukuran | Start | Bioma | Cincin | Bahaya | Ranah disarankan |
|---|---|---|---|---|---|---|---|
| `pelabuhan` | Pelabuhan Cahaya Zaitun | 288 × 288 (sepertiga di atas air) | 9 distrik | pantai: daratan + laut dangkal | R2-R4, cincin pertama 1400-4200 | rendah (bajak laut, badai) | Fana+ |
| `oasis` | Kota Oasis Bulan Sabit | 288 × 288 + Lapangan Kafilah 96 × 96 | 10 distrik | gurun, badlands | R2-R4, cincin pertama 1400-4200 | sedang (badai pasir, reruntuhan) | Pemurnian Qi+ |
| `air` | Kota Air Seribu Jembatan | 288 × 288 | 9 distrik | dataran rendah datar dekat air | R2-R4, cincin pertama 1400-4200 | rendah (hantu air, pencuri) | Fana+ |
| `gunung` | Kota Teras Awan | 288 × 288 (naik 30-80 blok) | 9 distrik | lereng bukit dan gunung | R2-R4, cincin pertama 1400-4200 | sedang (longsor, tebing) | Pemurnian Qi+ |
| `iblis` | Kota Darah Senja | 288 × 288 | 9 distrik | hutan gelap, taman pucat, badlands, rawa | R3-R4, cincin pertama 3500-6000 | tinggi (seluruh kota) | Inti Emas+ |

Nama kota lain: `KotaTemaNames.of(seed, tema, index)` (kunci `kultivator.kota_tema.<tema>.nama.a.<i>|.b.<j>`), mis.
pelabuhan {Cahaya Zaitun, Mutiara Timur, Angin Selatan, Karang Giok, Teluk Naga} × {Pelabuhan, Bandar, Labuhan}; oasis
{Bulan Sabit, Mata Air Giok, Pasir Bernyanyi, Kurma Emas} × {Kota Oasis, Benteng Pasir}; air {Seribu Jembatan, Teratai
Putih, Benang Sutra, Kabut Kanal} × {Kota Air, Desa Air}; gunung {Teras Awan, Seribu Anak Tangga, Kabut Pinus, Lonceng
Puncak} × {Kota Gunung, Kota Teras}; iblis {Darah Senja, Tulang Hitam, Bulan Merah, Seribu Rantai} × {Kota, Benteng}.

Pendekatan struktur untuk semua tema: **tata letak** (`kultivator:tata_letak`, 38.B.2.3), bukan jigsaw. Alasan:
(1) kanal, dermaga dan teras harus **bersambung tepat** antar-lot; rotasi jigsaw acak dan jangkauan ≤ 116 tidak
menjamin; (2) rotasi kota tema **diturunkan dari medan** (arah laut, arah menanjak), bukan dari hash; (3) kota 288 × 288
dengan tembok tidak muat satu start jigsaw berkualitas. **Jigsaw** hanya untuk satelit kecil (`kota_tema/reruntuhan_gurun`,
`kota_tema/pos_pemburu`, 38.T.2.6).

**Distrik tema = 2 × 2 lot** (96 × 96, lot tetap 48 × H × 48 dengan lantai jalan di y 4 lot, 38.B.2.5); kota = 3 × 3
distrik (grid sebelum diputar, utara = −z). Pusat distrik `(x, z)` = pusat kota + `(96x, 96z)`; tembok sebagai lot
tambahan di tepi luar (ofset di luar sel 96, seperti ibu kota). Semua potongan satu distrik ≤ ±72 blok dari chunk start
(sel 96 + tembok 8 + menara 8), di dalam jangkauan referensi ±8 chunk; `dy` gunung tidak mengubah jangkauan horizontal.
Di tabel distrik di bawah, lot ditulis `(lx,lz)` dengan lx, lz ∈ {0, 1} di dalam distrik.

### 38.T.2 Rencana, putusan medan, kepadatan dan anggaran (modul 38TK)

#### 38.T.2.1 Rencana (`dunia/tempat/tema/RencanaKotaTema`)

- Satu kelas, lima instans terdaftar: `RencanaRegistry.register("kota_tema_<tema>", RencanaKotaTema.of(Tema.X))`.
  `Tema` = enum `PELABUHAN, OASIS, AIR, GUNUNG, IBLIS` dengan field `id`, `ukuranSel`, `peluangSel`, `cincinPertama
  (min, lebar)`, `prioritas`, `distrik[]`.
- **Cincin pertama** (empat tema damai): 16 kandidat di sudut `turn(tema) + i × 22.5°`, jari-jari `1400 + hash × 2800`;
  kandidat pertama yang **layak** (putusan tema, 38.T.2.2) menjadi kota tema pertama; sisanya di cincin gugur. Iblis:
  12 kandidat, jari-jari `3500 + hash × 2500`.
- **Sel jauh:** grid per tema `ukuranSel` (pelabuhan 3072, oasis 3584, air 3072, gunung 3072, iblis 4096) digeser per
  tema (`ofset = hash(seed, tema) mod ukuranSel`) agar sel lima tema tidak sejajar; satu kandidat di titik hash dalam
  60 % tengah sel, hidup dengan `kotaTema.<tema>.peluangSel` (pelabuhan 0.7, oasis 0.7, air 0.5, gunung 0.6, iblis 0.45).
- **Jarak:** ≥ 1200 dari setiap ibu kota yang layak (`RencanaKotaBesar`), ≥ 900 dari kota tema lain yang layak, ≥ 700 dari
  setiap gerbang `GerbangPlan`, ≥ 400 dari setiap kandidat sekte langit 37. Urutan penilaian deterministik: ibu kota dulu,
  lalu tema menurut `prioritas` (iblis, gunung, pelabuhan, oasis, air), lalu urutan sel spiral; yang lebih dulu menang.
  Kandidat yang bertabrakan bergeser 240 blok menjauh (maks 3 kali) sebelum dinilai.
- **Putar** = dari putusan (pelabuhan: arah laut menjadi utara grid; gunung: arah menanjak menjadi utara grid; lainnya:
  `hash & 3`).
- Kontrak tambahan pada C38.W2 (milik 38W, 38.T.14.3): `Rencana` mendapat dua metode default
  `default List<SimpulJalan> simpul(long seed, int selX, int selZ) { return List.of(); }` (titik gerbang kota tema untuk
  `RencanaJalan`, sehingga jalan desa/kekaisaran tersambung ke gerbang darat kota tema) dan
  `default List<BoundingBox> zonaAman(long seed) { return zona(seed); }` (Kota Iblis hanya mengembalikan Zona
  Perjanjian, 38.T.7.8).

#### 38.T.2.2 Putusan medan (`dunia/tempat/tema/PutusanTema`)

Semua memakai `getBaseHeight(x, z, WORLD_SURFACE_WG | OCEAN_FLOOR_WG, ...)` dan `biomeResolver` seperti 38.B.2.4,
deterministik dari seed, cache `ConcurrentHashMap` (kunci `seed ^ tema ^ index`, maks 64 per tema). Sampel 9 × 9 titik
berjarak 36 blok di kotak ±144 di sekitar pusat (81 kolom × 2 heightmap per kandidat, sekali per seed).

| Tema | Layak bila | `dasarY` | Rotasi |
|---|---|---|---|
| `pelabuhan` | ada satu arah mata angin yang tiga baris luarnya (27 sampel) ≥ 80 % air dengan dasar laut di `seaLevel − 4 .. seaLevel − 20`; enam baris lainnya ≥ 85 % darat, median darat `seaLevel + 1 .. +14`, selisih darat ≤ 14; pusat distrik darat di `#kultivator:kota_tema/pelabuhan/darat` (beach, snowy_beach, plains, sunflower_plains, forest, birch_forest, dappled_forest, savanna, sparse_jungle, meadow, cherry_grove, mangrove_swamp), pusat distrik laut di `#…/pelabuhan/laut` (ocean, lukewarm_ocean, warm_ocean, cold_ocean, beach, river) | `clamp(median darat, seaLevel + 1, seaLevel + 8)` | arah laut → utara grid |
| `oasis` | ≥ 7 dari 9 pusat distrik di `#…/oasis` (desert, badlands, eroded_badlands, wooded_badlands); ≤ 2 sampel air; selisih ≤ 16; median `seaLevel + 2 .. +40` | median | `hash & 3` |
| `air` | 9 pusat distrik di `#…/air` (plains, sunflower_plains, forest, birch_forest, flower_forest, dappled_forest, swamp, river, cherry_grove); selisih ≤ 6; median `seaLevel + 1 .. +6`; ≤ 12 sampel air (sungai kecil boleh, danau besar tidak) | `clamp(median, seaLevel + 1, seaLevel + 4)` | `hash & 3` |
| `gunung` | ada arah mata angin dengan median baris naik monoton antar tiga baris distrik (tiap langkah +10 .. +30, total +30 .. +80); selisih dalam satu baris ≤ 18; pusat distrik di `#…/gunung` (windswept_hills, windswept_forest, windswept_gravelly_hills, meadow, grove, snowy_slopes, taiga, old_growth_pine_taiga; baris atas boleh stony_peaks); tidak ada air | median baris bawah | arah menanjak → utara grid |
| `iblis` | jarak ≥ 3000 dari pusat; ≥ 6 dari 9 pusat di `#…/iblis` (dark_forest, pale_garden, badlands, eroded_badlands, wooded_badlands, swamp, mangrove_swamp, windswept_gravelly_hills, old_growth_spruce_taiga); ≤ 3 sampel air; selisih ≤ 22 | median | `hash & 3` |

- **Kota Gunung:** `dy` per lot = `round((tinggiPusatLot − dasarY) / 6) × 6`, dijepit monoton per baris (baris atas ≥
  baris tengah + 6 ≥ baris bawah + 6) dan ≤ 72; sambungan antar-baris memakai lot `tangga_*` yang tinggi anak tangganya
  dipilih dari selisih `dy` (varian naik 6, 12, 18, 24). Fondasi lot gunung 12 lapis (dinding penahan teras
  `stone_bricks` + `mossy_stone_bricks`) agar `beard_box` tidak meninggalkan lubang di sisi lereng.
- **Kota Pelabuhan:** distrik baris laut memakai struktur dengan `terrain_adaptation: none`; dermaga berdiri di
  **tiang pancang** (`stripped_spruce_log`/`stripped_mangrove_log`) sedalam 24 dari lantai dermaga (dasar laut
  ≥ `seaLevel − 20` menjamin tiang menyentuh dasar; bagian tiang yang masuk tanah tidak terlihat); semua sel kosong
  templat laut = `structure_void` sehingga air laut tetap (verifikasi: `StructureTemplate` melewati `structure_void`, dan
  blok waterloggable di bawah permukaan dipasang `waterlogged=true` lewat `LiquidSettings.APPLY_WATERLOGGING`).
- **Kota Air:** air kanal diletakkan templat (sumber air di saluran berbatu 5 lebar × 2 dalam), muka air = lantai jalan
  − 1. **Kode sisi lot** (38.T.2.4) menjamin kanal bersambung antar-lot dan antar-distrik.
- Server (peta, kabar, teleport) memanggil `PutusanTema.of(ServerLevel, Kandidat)` dengan generator dan `RandomState`
  overworld, hasilnya sama dengan worldgen (pola 38.B.2.4).

#### 38.T.2.3 Struktur, set, placement

- Struktur `kultivator:kota_tema/<tema>/<distrik>` bertipe `kultivator:tata_letak` (`"rencana": "kota_tema_<tema>"`,
  `"distrik": "<id>"`, `lot[]` 4 lot + lot tembok, `gaya[]`), `step surface_structures`, `terrain_adaptation beard_box`
  (kecuali distrik laut pelabuhan: `none`), `spawn_overrides` monster kosong di semua tema (Kota Iblis juga: bahayanya
  datang dari NPC iblis yang dimunculkan sistem, bukan mob alami; 38.T.7.8).
- Set `kota_tema/<tema>/<distrik>` placement `kultivator:rencana_tempat` (`rencana`, `distrik`), `spacing 256`,
  `separation 0`. Salt: pelabuhan 380401-380409, oasis 380411-380420, air 380421-380429, gunung 380431-380439, iblis
  380441-380449, satelit 380451-380452 (unik di seluruh repo, diperiksa `cek_tempat.py`).
- Pack bawaan `kultivator:dunia_kotatema` (`resourcepacks/dunia_kotatema`, `DuniaPacks.worldgen("kotatema",
  kotaTema.worldgen)`); per tema dimatikan lewat `kotaTema.<tema>.worldgen` (rencana tema itu menjawab "tidak layak"
  untuk semua kandidat, jadi tidak pernah ada distrik setengah).
- Tag: `#kultivator:kota_tema` (semua 46), dan `kota_tema/*` dimasukkan ke `#kultivator:tempat_berpenghuni` dan
  `#kultivator:tempat_dirawat` (baris tag milik 38W, 38.T.14.3).

#### 38.T.2.4 Lot, kode sisi, gaya dan titik NBT

- **Kode sisi lot** (validasi `tools/cek_tempat.py`, aturan baru 38W): setiap lot tema menyatakan empat sisi dengan kode
  `J` (jalan 3 blok), `K` (kanal 5 + jalan setapak 2), `T` (tembok), `A` (air terbuka/laut), `G` (tangga naik), `B` (batas
  bebas, mis. taman). Dua sisi yang berhadapan harus kodenya sama setelah rotasi; kode ditulis generator ke kunci akar
  NBT `kultivator_sisi` (diabaikan `StructureTemplate.load` seperti `kultivator_titik`, verifikasi). `cek_tempat.py`
  menyusun grid tiap kota tema dan gagal bila ada sisi tak cocok atau kanal buntu (kecuali di lot `kolam`/`danau`).
- **Jenis titik baru** (ditambah ke daftar sah `cek_tempat.py` oleh 38W): `tambatan` (kapal/perahu; data `kelas`),
  `jalur_kapal`, `jalur_perahu` (simpul spline kanal; data `urut`, `jalur`), `stasiun_lift` (data `pasangan`, `jenis`
  gantung|katrol), `jalur_tangga` (simpul kuli/peziarah), `kanal` (kotak kanal untuk 38.R), `lentera_air`,
  `kandang_unta`, `sumur_timba`, `alat_tenun`, `arena_pintu`, `sel_tawanan`, `zona_perjanjian` (kotak), `tandu`,
  `pandang` (titik kamera adegan), `jaga_suar`.
- **Gaya per bioma** (pool berakhiran, NBT sama, processor berbeda, seperti 38.B.2.5):

| Tema | Gaya | Bioma | Ciri |
|---|---|---|---|
| pelabuhan | `selatan` (dasar) | hangat dan sedang | rumah bata merah gaya Minnan (`bricks`, `red_terracotta`, dinding `white_terracotta`), atap pelana dengan bubungan **ekor walet** (ujung bubungan naik: `stairs` bertumpuk + `end_rod`), kayu `acacia`/`jungle` di dermaga |
| pelabuhan | `utara` | cold_ocean, snowy_beach, taiga | batu `stone_bricks`, kayu `spruce`, atap `dark_oak` berlapis salju |
| oasis | `pasir` (dasar) | desert | bata lumpur (`mud_bricks`, `packed_mud`), `smooth_sandstone`, kubah `cut_sandstone`, tenda kain `orange_wool`/`red_wool`, palma (`jungle_log` + `jungle_leaves persistent=true`) |
| oasis | `merah` | badlands | `terracotta` merah/oranye, kubah `red_sandstone`, kayu `acacia` |
| air | `jiangnan` (dasar) | semua kecuali sakura | **dinding putih genteng hitam**: dinding `white_terracotta`/`calcite`, genteng `deepslate_tile_*` dan `polished_blackstone_*`, kayu `dark_oak` tua, jendela kisi, tembok kuda bertangga (`stone_brick_wall` + slab) |
| air | `sakura` | cherry_grove | aksen `cherry`, kelopak `pink_petals` |
| gunung | `batu` (dasar) | windswept_*, stony_peaks | `stone_bricks`, `andesite`, genteng `dark_oak`, rumah panggung kayu di tepi tebing (tiang `spruce_log` ke teras bawah) |
| gunung | `salju` | grove, snowy_slopes | `spruce`, salju di bubungan |
| gunung | `hutan` | meadow, taiga, old_growth_pine_taiga | `spruce` + plester, lumut |
| iblis | `hitam` (dasar) | dark_forest, pale_garden, windswept_gravelly_hills, taiga | `blackstone`, `polished_basalt`, `nether_bricks`, aksen `red_nether_bricks`, rantai, `soul_lantern` |
| iblis | `merah` | badlands | `red_sandstone`, `terracotta` gelap, `bone_block` |
| iblis | `rawa` | swamp, mangrove_swamp | panggung `mangrove_*`, `mud_bricks`, akar, kabut (klien) |

- Processor: `kota_tema/lapuk_ringan` (pola 38.B.2.5), `kota_tema/utuh` (kuil, istana, mercusuar), `kota_tema/iblis_lapuk`
  (retak 8 %, `cracked_*`, noda darah kering = `red_concrete_powder` 2 % di lantai luar; **tanpa** api).
- **Anggaran templat** per tema: ±40 NBT lot (36 lot inti + varian rumah + tembok/menara/gerbang), ≤ 1.2 MB gzip per
  tema, ≤ 5.5 MB semua tema; titik ≤ 96 per lot; jangkar ≤ 6 per lot, ≤ 24 per distrik.
- **Jangkar bersama** (dilayani modul pemiliknya tanpa kode baru): `kota/situs` (data `situs="kota/kota_tema"`,
  `tema=<id>`, protect 100; **tidak** di Kota Iblis), `kota/alun`, `kota/teh`, `kota/kursi_pengembara`, `kota/papan`,
  `kota/penjaga`, `kota/pil`, `kota/pusaka`, `kotabesar/layanan` (data `layanan` = `penginapan`, `rumah_makan`, `tabib`,
  `bank_cabang`, `papan_gilda`), `kotabesar/cakram` (stasiun teleport), `kotabesar/tukang` (38.R). Jangkar milik 38TK:
  `kota_tema/situs` (semua tema, data `tema`, `kota` = indeks), `kota_tema/zona` ×9-10 (protect 72), `kota_tema/<layanan>`
  per layanan khas (38.T.3-38.T.7), `kota_tema/tambatan`, `kota_tema/lift`, `kota_tema/arena`, `kota_tema/sel`.

#### 38.T.2.5 Kepadatan (baris tambahan untuk tabel 38.C.9)

| Tempat | Bioma cocok | R1 | R2 | R3 | R4 | Catatan |
|---|---|---|---|---|---|---|
| Kota Pelabuhan | pantai (daratan + laut dangkal) | — | 1 dijamin di 1400-4200 bila ada pantai layak; lalu 0.025 | 0.03 | 0.03 | 1 per sel 3072² × 0.7 × layak (±35 % kandidat pantai lulus) |
| Kota Oasis | gurun, badlands | — | 1 dijamin di 1400-4200 bila ada gurun layak; lalu 0.04 | 0.05 | 0.05 | 1 per sel 3584² × 0.7 × layak |
| Kota Air | dataran rendah datar | — | 1 dijamin di 1400-4200; lalu 0.03 | 0.03 | 0.03 | 1 per sel 3072² × 0.5 × layak |
| Kota Gunung | lereng | — | 1 dijamin di 1400-4200; lalu 0.03 | 0.04 | 0.04 | 1 per sel 3072² × 0.6 × layak |
| Kota Iblis | hutan gelap, taman pucat, badlands, rawa | — | — | 1 dijamin di 3500-6000; lalu 0.015 | 0.025 | 1 per sel 4096² × 0.45 × layak |
| `kota_tema/reruntuhan_gurun` | desert, badlands | 0.08 | 0.2 | 0.25 | 0.3 | jigsaw `cincin_tempat`, `min_chunks` 12, salt 380451 |
| `kota_tema/pos_pemburu` | ikut Kota Iblis | — | — | 1 per Kota Iblis | 1 per Kota Iblis | rencana, 500-700 blok dari Gerbang Tengkorak ke arah sekte Lurus terdekat, salt 380452 |

"Dijamin" berarti: bila cincin pertama punya satu pun kandidat layak; seed tanpa pantai/gurun di cincin itu tidak
mendapat jaminan (uji `uji tema rencana` mencetak alasannya). "Penuh tapi tidak sesak" (38.C.9) tetap: zona kota tema
+ 3 chunk menolak struktur permukaan lain lewat `TempatKeepOut` (38.D.4); kota tema dihitung sebagai tempat berpenghuni
(bobot 4) dalam batas ≤ 6 per km².

#### 38.T.2.6 Satelit jigsaw

- `kota_tema/reruntuhan_gurun` (`size 4`, `max_distance 60`, `step surface_structures`, `beard_box`): kota terkubur
  pasir: menara jaga roboh, gerbang setengah tertimbun, aula bawah tanah tiga ruang (tangga turun ke y −14 relatif),
  mural gua (billboard klien bergambar penari dan kafilah), peti (`loot_table kultivator:tema/reruntuhan_<tier>`), ruang
  sarang **Raja Kalajengking** (bos, 38.T.4.6). Pintu aula = `PintuPasirEntity` (pola `PintuTebingEntity` 38GU, milik
  38TK). Tidak dirawat 38.R. Ranah disarankan: tier 1 Pemurnian Qi (R1-R2), tier 2 Fondasi (R3), tier 3 Inti Emas (R4).
- `kota_tema/pos_pemburu` (`size 2`): pos sekte Lurus: tenda komando, Papan Buronan Iblis (billboard), pandai, kuda,
  4 NPC PEMBURU_LURUS (38.T.7.9); dihuni, aman (`TempatZones.safe`).

#### 38.T.2.7 Anggaran (tambahan 38.C.10 dan 38.D.7)

| Hal | Anggaran |
|---|---|
| Starts | 46 struktur tata letak (9/10/9/9/9) + 2 satelit; putusan sekali per kandidat (cache); placement O(1) per chunk |
| Tubuh NPC | `kotaTema.tubuhMaks` 20 per kota (12-36), jenis `DuniaBudget` `npc_kota_tema`; per pemain ikut 38.N.7 (24 total 38) |
| Penduduk data | pelabuhan 260, oasis 220, air 240, gunung 180, iblis 200 |
| Impostor | 0/32/48 per pemain (Min/Nor/Maks) |
| Entitas mekanisme hidup | per kota ≤ 12 (kapal ≤ 3, perahu kanal ≤ 6, unta ≤ 8 per kafilah terlihat, kereta gantung ≤ 2, katrol ≤ 2, pintu/derek/timba/tenun ≤ 8); hanya bila pemain ≤ 96 blok; `DuniaBudget` jenis `mekanisme_tema` cap 24 global |
| Gerak entitas | spline dari titik NBT, **tanpa** pencarian jalur; posisi = fungsi waktu (`t = gameTime − mulai`), dikirim ke klien tiap 20 tick + interpolasi |
| Klien | lentera air 0/40/120, partikel badai pasir 0/400/900 quad per detik (`EpicBudget.takeQuads`), tali kereta gantung 1 batch, sinar mercusuar 1 quad aditif |
| Server | ≤ 0.6 ms/tick untuk semua kota tema yang hidup (`uji tempat ukur` bagian `tema`) |

### 38.T.3 Kota Pelabuhan (`kota_tema/pelabuhan`)

#### 38.T.3.1 Lore dan Sumber

- **Lore** (`tempat_tema_lore.pelabuhan.*`): pelabuhan dagang tertua di pesisir, "tempat seribu bendera berlabuh".
  Didirikan para pedagang fana yang membayar sekte untuk menjaga mercusuar; kini dipimpin **Syahbandar** dari Kantor
  Urusan Laut, dengan Dewan Saudagar sebagai penyeimbang. Mercusuar menyimpan **Pelita Penunjuk Angin**, lampu roh yang
  konon dinyalakan dari mutiara yang dibawa dari Laut Tanpa Batas; selama ia menyala, kapal fana tidak pernah tersesat
  dalam kabut. Kampung pelaut di barat dihuni keluarga nelayan yang menyembah **Dewi Laut** di Kuil Laut.
- **Sumber** (`kultivator.kota_tema.pelabuhan.source`): "Sumber: Quanzhou (dikenal pedagang asing sebagai 'Zayton'),
  salah satu pelabuhan tersibuk dunia pada masa Song-Yuan (abad 10-14) dan simpul Jalur Sutra Maritim; Warisan Dunia
  UNESCO 2021 'Quanzhou: Emporium of the World in Song-Yuan China'. Kuil Tianhou di Quanzhou dipersembahkan bagi dewi
  laut Mazu; Pagoda Wanshou adalah penanda navigasi yang terkenal pada masa itu; di Kuil Zhenwu para pejabat
  mempersembahkan kurban laut. Tata kota, tokoh, harga dan angkanya rancangan game."
- Sumber gaya (`kultivator.kota_tema.pelabuhan.gaya.inspired`): "Terinspirasi dari rumah bata merah bergaya Minnan di
  Fujian dengan bubungan ekor walet; rancangan game."

#### 38.T.3.2 Tata letak (grid sebelum diputar; laut di utara)

```
             x = -1                x = 0                 x = +1
 z = -1   [dermaga_nelayan]    [dermaga_agung]       [galangan]          ← di atas air (terrain_adaptation none)
 z =  0   [kota_awak]          [pasar_laut]          [gudang_dagang]     ← tepi pantai (tembok laut)
 z = +1   [kuil_laut]          [gerbang_darat]       [hunian_saudagar]   ← darat (tembok darat, gerbang ke jalan)
```

Tembok: tembok darat 3 sisi (`tembok_lurus` 48 × 14 × 6 bata, lebih rendah dari ibu kota), **tembok laut** di antara
baris laut dan tepi berupa tanggul batu dengan tangga ke dermaga; Gerbang Darat di selatan tengah (`gerbang_darat`
48 × 20 × 12) menyambung jalan 38.C.6 (`simpul` rencana).

| Distrik | Lot (lx,lz) dan isi (semua berinterior lengkap, cahaya ≥ 8, titik NBT) |
|---|---|
| `dermaga_nelayan` | (0,1) **Pasar Ikan Pagi**: dermaga lebar 40 × 30 di atas tiang, 14 lapak beratap kanvas (titik `lapak` ×14, peti es = `blue_ice` + `barrel`, keranjang ikan), timbangan, jemuran ikan (`kerja:jemur`); (1,1) **Tambatan Nelayan**: titian bercabang, 8 `tambatan` perahu nelayan, gudang jala (titik `kerja:jala` ×4); (0,0) **Pemecah Ombak** batu `cobblestone` + `stone_bricks` melengkung 44 blok; (1,0) **Mercusuar Pelita Angin** di ujung pemecah ombak: menara 11 × 42 × 11 (ruang dasar penjaga suar: kasur, meja peta, tangga melingkar; tiga lantai gudang minyak dan lensa; puncak ruang lampu terbuka berpagar dengan entitas `api_suar` (lampu berputar, 38.T.9), titik `jaga_suar`), jangkar `kota_tema/suar` |
| `dermaga_agung` | (0,0)+(1,0) **Dermaga Agung**: tiga tambatan kapal dagang (`tambatan` kelas `jung`, panjang 28 masing-masing) dengan tangga kapal, bollard (`polished_andesite_wall`), lentera tiang; derek injak (`kota_tema/derek` ×2, entitas); (0,1) **Kantor Urusan Laut** (Shibosi): aula loket bea (meja ×4, timbangan, rak dokumen), ruang Syahbandar lantai dua (meja besar, peta laut di dinding = billboard klien, balkon menghadap dermaga), sel sitaan barang selundupan; (1,1) **Pos Kapal Lintas Laut**: loket, ruang tunggu (kursi ×12), tambatan khusus kapal `jung_laut` (jangkar `kota_tema/lintas_laut`) |
| `galangan` | (0,0)+(1,0) **Galangan Kapal**: dua landasan miring (slipway) dari tanah ke air dengan rangka lunas setengah jadi = entitas `kapal_galangan` (tahap 0-4, 38.T.9), perancah kayu (blok, statis), bengkel tali (titik `kerja:tali` ×4), tungku ter (`cauldron` + `campfire[lit=true]` daftar putih dapur/bengkel), gudang kayu; (0,1) **Rumah Tukang Kapal**: meja gambar lunas, model kapal di rak (pajangan item), kamar tukang; (1,1) **Dok Kering** kecil (kolam batu kosong 30 × 10 dengan pintu air = entitas `pintu_air`) |
| `kota_awak` | (0,0) **Kedai Jangkar Patah**: kedai pelaut dua lantai (meja ×10, panggung kecil penyanyi, kamar sewa ×6) jangkar `kotabesar/layanan` `penginapan` + `rumah_makan`; (1,0) **Lorong Tali**: jalan pembuat tali panjang 40 (titik `kerja:tali` ×6), rumah awak bersusun; (0,1)+(1,1) rumah pelaut dan nelayan (pool `kota_tema/pelabuhan/rumah_awak`: rumah bata merah kecil, rumah panggung, rumah dengan jemuran jala), sumur, kuil kecil Dewi Laut keluarga |
| `pasar_laut` | (0,0)+(1,0) **Pasar Seberang**: aula pasar terbuka 80 × 30 bertiang merah, 16 kios barang luar negeri (rempah, porselen, kaca, mutiara; titik `lapak` ×16), papan kurs kapal datang (billboard), jangkar `kota_tema/toko_seberang`; (0,1) **Alun Pelabuhan**: alun 40 × 40 dengan patung jangkar raksasa (`anvil` + `chain` + `iron_block`), Papan Kabar, jangkar `kota/situs`, `kota/alun`, `kota/papan`; (1,1) **Kedai Teh Ombak** (jangkar `kota/teh`, `kota/kursi_pengembara` ×2) |
| `gudang_dagang` | (0,0) **Gudang Bersama**: gudang tiga lantai (rak `barrel`, katrol = entitas `katrol_gudang` kecil), jangkar `kota_tema/gudang` (titip muatan, 38.T.10); (1,0) **Balai Dewan Saudagar**: aula meja panjang (kursi ×12), ruang arsip kontrak; (0,1) **Cabang Rumah Uang** (`kotabesar/layanan` `bank_cabang`) + **Papan Gilda** salinan (`papan_gilda`, misi E-C); (1,1) **Menara Tambat Langit**: menara kayu-batu 12 × 40 × 12 dengan dua anjungan di y +28 untuk kapal terbang (jangkar `kota_tema/tambat_langit`, kelas perahu/sekte), loket bahan bakar |
| `kuil_laut` | (0,0)+(1,0) **Kuil Dewi Laut**: halaman batu dengan pembakar dupa, aula utama (altar, patung Dewi Laut `quartz` + `light_blue_terracotta` 6 tinggi, dua penjaga mata seribu li dan telinga angin = patung batu), paviliun lonceng, jangkar `kota_tema/kuil_laut`; (0,1) **Pagoda Penunjuk** (pagoda batu lima tingkat 13 × 34 × 13 di bukit kecil, penanda kapal; puncak terbuka titik `meditasi` ×4, `pandang`); (1,1) rumah pendeta, kebun dupa |
| `gerbang_darat` | (0,1) gerbang darat + pos jaga (jangkar `kota/penjaga` ×2); (0,0) **Penginapan Angin Selatan** (12 kamar, `penginapan`); (1,0) **Rumah Makan Sup Ikan** (`rumah_makan`, dapur `smoker`); (1,1) **Halaman Kafilah Darat** (kandang kuda, gerobak, `kotabesar/cakram` stasiun teleport kecil 9 × 9) |
| `hunian_saudagar` | (0,0)+(1,0) **Rumah Saudagar Zhao**: rumah halaman bertingkat dua (siheyuan bata merah, taman batu, ruang tamu dengan pajangan porselen); (0,1) **Kampung Saudagar Seberang**: rumah-rumah bergaya asing (kubah kecil `cut_sandstone`, lengkung jendela), rumah ibadah kecil saudagar seberang (ruang tenang tanpa patung, karpet, lentera), jangkar `kota_tema/saudagar_seberang`; (1,1) rumah, `bengkel` (`kotabesar/tukang`) |

#### 38.T.3.3 Penduduk, peran, rutinitas dan animasi

Peran baru (38.T.14.3, ditambah 38NL di akhir `NpcRole`): `SYAHBANDAR` (tier 3, PEJABAT, topi pejabat laut + jubah biru
laut), `PETUGAS_BEA` (1, PEDAGANG, topi petugas + buku segel), `PELAUT` (1, PEKERJA, kain kepala + rompi bergaris + kalung
gigi hiu), `NAKHODA_LAUT` (2, PEKERJA, topi lebar + jubah mantel), `PENJAGA_SUAR` (2, TETUA, mantel minyak + lentera),
`TUKANG_KAPAL` (1, PEKERJA, celemek ter + sabuk alat + palu dempul), `PENJUAL_IKAN` (1, PEDAGANG, celemek basah + sanggul
kain), `PEDAGANG_SEBERANG` (2, PEDAGANG, serban + jubah panjang bersulam), `PENDETA_LAUT` (2, RAHIB, jubah biru-putih +
mahkota kecil). Nelayan memakai `NELAYAN` yang ada. Penduduk data 260: nelayan 50, pelaut 60, pedagang 40, tukang
kapal 20, keluarga 70, pejabat/penjaga 20.

Rutinitas khas (menimpa kolom kelompok 38.N.3 hanya pada jam yang disebut):

| Jam | Nelayan / penjual ikan | Pelaut | Tukang kapal | Pedagang seberang | Penjaga suar | Pendeta laut |
|---|---|---|---|---|---|---|
| 03-05 | berangkat: `angkat_bawa` jala ke perahu, perahu nelayan keluar dari `tambatan` (entitas, 38.T.3.4) | tidur di kamar sewa | tidur | tidur | `jaga_suar`: menuang minyak (`tuang_teh` varian ember), lampu terang | `sujud_dupa` subuh menghadap laut |
| 05-08 | **perahu pulang**, `tarik_jala` di dermaga, penjual ikan `seru_dagang` di Pasar Ikan Pagi (pasar ikan buka 05-09 saja) | bangun, `basuh_muka` di sumur | `palu_lunas` di galangan | sarapan di kedai teh | `teropong_laut` di balkon | menyapu halaman kuil |
| 08-12 | `jemur_ikan`, memperbaiki jala (`ikat_tali` duduk) | memuat/membongkar kapal: `jalan_pikul` peti dari kapal ke gudang lewat titian, `ikat_tali` di bollard | lunas, tali (`kerja:tali`) | jaga kios Pasar Seberang, `tawar` | tidur | menerima peziarah |
| 12-14 | makan siang bersama di dermaga (`makan` duduk di bollard) | makan di Kedai Jangkar Patah | makan | makan | — | — |
| 14-18 | tidur siang, anak-anak `main_air` di tangga dermaga | memuat; kapten di Kantor Urusan Laut (`menulis` surat muatan) | lunas | jaga kios | bangun, `teropong_laut` | doa sore |
| 18-23 | `minum_teh`, `cerita` | Kedai Jangkar Patah: `minum_teh` (arak), nyanyi pelaut (`tertawa` bergiliran, `bicara_tangan` lebar) | pulang | pulang | menyalakan lampu (`jaga_suar`), sinar mercusuar menyala 12500-23500 | doa senja, lonceng |

Cuaca: badai petir (dibaca dari level) → semua perahu tetap tertambat, nelayan `tatap_langit` ke laut, pendeta
`sujud_dupa` berulang; hujan → pelaut bekerja tetap (mantel), pedagang menutup kios setengah.

#### 38.T.3.4 Permainan khas

- **Kapal dagang terjadwal** (`KapalDagangEntity`, 38.T.9.1): tiga tambatan; tiap tambatan punya siklus 3 hari dunia:
  **Datang** (hari 0, 2000-4000: kapal muncul 160 blok di laut pada `jalur_kapal` awal, layar terkembang, berlayar
  sepanjang spline ke tambatan dalam 90 s, layar digulung, jangkar turun = model) → **Tambat** (bongkar/muat:
  kuli berjalan dengan peti, derek berputar; papan geladak bisa dinaiki lewat `ColliderEntity` geladak pola
  `KapalDekEntity`; NAKHODA_LAUT di geladak menjual muatan kapal itu) → **Berangkat** (hari 2, 14000: jangkar naik,
  layar terkembang, kapal mundur 40 blok lalu berlayar keluar dan memudar di 160 blok). Asal kapal (data): "Negeri
  Rempah Selatan", "Kepulauan Mutiara", "Kota Pelabuhan <nama lain>" (bila pemain tahu kota pelabuhan lain → muatan khas
  kota itu, 38.T.10). Tanpa pemain dalam 128 blok, siklus berjalan sebagai data (stok tetap berganti).
- **Muatan kapal** (layar `tema_kapal`, 38.T.13): 4-6 barang luar (rempah, porselen, kaca warna, mutiara, garam laut,
  **Kompas Pasang** (item baru, menunjuk Pusaran Laut terdekat, 38.T.10)), harga 38.T.10; stok per kapal.
- **Kapal Lintas Laut** (`kota_tema/lintas_laut`, NAKHODA_LAUT "Kapten Gao Fan"): sekali per hari dunia berangkat
  12000 ke **Laut Tanpa Batas**. Syarat: pita ≥ 2 (Fondasi), ongkos `40 × (1 + band/4)` NB, tidak buronan; adegan 6 s
  (kapal berlayar ke kabut, bisa dilewati, 7b) lalu `SeaGate.enter(player, true)` (verifikasi arti `free`: tanpa
  syarat mutiara/ranah gerbang pusaran). Pulang lewat Gerbang Pulang yang ada (kembali ke titik berangkat = dermaga ini,
  `extra.laut.ret`). Murid pengikut ikut (maks 2) dengan ongkos sama. `Sibuk` `tema.pelayaran` selama adegan.
- **Menara Tambat Langit**: tujuan rute kapal terbang `DuniaApi.routes().registerDestination("kota_tema_pelabuhan_" +
  index, nama, (p, l) -> diketahui(p), (p, ents) -> tibaDiAnjungan)`; layanan isi bahan bakar (jual batu roh dengan harga
  `NilaiBatu.VALUE`, tidak lebih murah) dan **Kontrak Muatan Langit** (38.T.10.3): antar peti dagang dengan kapal terbang
  pemain ke kota tema lain, bayaran lebih besar dari kafilah darat.
- **Galangan**: pesan **lunas** perahu awan (`LunasItem` kelas `PERAHU`, pita ≥ 3) 900 NB, kapal sekte (pita ≥ 4) 4000
  NB; jadi dalam 2/4 hari dunia (pesanan persisten di `KotaTemaData`); selama menunggu, entitas `kapal_galangan` di
  landasan naik tahap 0 → 4 (rangka lunas → gading → papan → tiang → layar) sehingga pemain **melihat** kapalnya
  dibangun. Juga: ganti gaya layar (kosmetik `KapalStyle` bila ada API publik, verifikasi; bila tidak, layanan ini tidak
  ada) 60 NB.
- **Memancing berbayar**: sewa perahu nelayan (`perahu_nelayan`, entitas, 2 NB/jam dunia) yang bisa didayung pemain di
  teluk (gerak seperti perahu vanilla, implementasi sendiri; tidak keluar > 200 blok dari dermaga, kembali sendiri);
  hasil memancing vanilla + 10 % **Ikan Roh** (item makanan + bahan pil, nilai 6 NB).
- **Festival: Hari Dewi Laut** (`d % 28 == 23`, 6000-12000): arak-arakan `TanduEntity` membawa patung Dewi Laut kecil
  dari Kuil Laut ke Dermaga Agung (8 pemikul NPC `jalan_pikul`, genderang, petasan = quad klien), sembahyang di dermaga,
  lalu **Lomba Perahu Nelayan** (6 perahu entitas pada `jalur_perahu` di teluk, pemain boleh ikut dengan perahu sewaan:
  biaya 5 NB, juara 30 NB + Jimat Ombak kosmetik + Nama Baik +2). Lentera merah di semua tiang dermaga (klien). Sibuk untuk
  adegan sembahyang (≤ 4 s).
- **Bahaya: Serbuan Bajak Laut** (tiap 18 ± 4 hari, malam; atau 50 % setelah pemain menolak suap Kapten Hiu Hitam di
  quest): `KapalBajakLautEntity` (jung layar hitam) berlayar ke Dermaga Agung, papan titian turun, band bajak laut
  (`RogueCultivators.spawnBand` varian ROGUE, `ROLE_RAID`, 6 + 3 × pemain, maks 18 hidup, `markRealmFoe`) menyerbu
  dermaga dan gudang; penjaga dan pelaut bertarung; warga lari (38.N.4 butir 11). Menang = Nama Baik +4 (+8 kontributor
  teratas), `25 × (band + 1)` NB dari Kas Kota, peti rampasan kapal bajak (loot `kultivator:tema/bajak_laut`). Kalah =
  Pasar Seberang tutup 1 hari, satu kapal dagang tidak datang siklus berikutnya. Tidak ada blok berubah (kerusakan oleh
  bajak laut dari ledakan = **tidak ada**: bajak laut tidak memakai TNT/api); `Sibuk` `tempat.pertahanan`; tekanan harian
  M2.
- **Bahaya: Badai Laut** (dibaca dari `level.isThundering()`, tidak diubah): kapal tidak berangkat/datang, Kapal Lintas Laut
  ditunda, sinar mercusuar menyala siang hari; pemain dalam perahu sewaan di teluk saat badai: ombak klien + dorongan
  acak kecil pada perahu (tanpa tenggelam).

#### 38.T.3.5 Tokoh dan rantai quest "Pelita yang Padam"

| Tokoh | Peran | Watak, suara | Muncul |
|---|---|---|---|
| **Lin Haichao** | SYAHBANDAR | perempuan tegas berusia 40-an, mantan nakhoda, tidak percaya sekte; `f2` | quest utama, Kantor Urusan Laut |
| **Zhao Wanli** | PEDAGANG_SEBERANG (kepala Dewan Saudagar) | ramah, licin, utang budi pada siapa saja; `m2` | dewan, cabang quest |
| **Kakek Deng Ming** | PENJAGA_SUAR | tua, setengah tuli, bercerita tentang laut tanpa batas; `elder` | mercusuar |
| **A-Lan** | PELAUT (gadis awak) | pemberani, ingin jadi nakhoda; `f1` | kedai, lomba perahu |
| **Mbah Lunas (Gong Shumu)** | TUKANG_KAPAL | nenek pembuat kapal, kasar tapi jenius; `elder` | galangan |
| **Ibrahim dari Negeri Barat** | PEDAGANG_SEBERANG | saudagar seberang yang sopan dan teliti, pencatat bintang; `m1` | kampung saudagar, Pasar Seberang |
| **Kapten Hiu Hitam (Hei Sha)** | pemimpin bajak laut (tubuh bos `KultivatorNpc` peran `NAKHODA_LAUT` varian bajak, tier 4) | kejam, berkode, dulu murid Kakek Deng; `m3` | Serbuan Bajak Laut, bab 4-5 |
| **Kapten Gao Fan** | NAKHODA_LAUT | pembawa Kapal Lintas Laut, penakut kecuali di laut; `m2` | lintas laut |

Rantai `tema_pelabuhan_pelita` (7 tahap, data `quest/tema_pelabuhan_pelita.json`, tidak pernah buntu: tiap tahap
bertenggat 5 hari dunia dengan hasil bawaan lewat rumor):

| # | Pemicu / pemberi | Isi dan tujuan | Cabang | Hadiah |
|---|---|---|---|---|
| 1 | masuk kota pertama kali; Kakek Deng menyapa di dermaga | "Pelitaku redup tiap malam." Naik mercusuar, lihat lensa retak; bicara dengan Lin Haichao | — | situs, Nama Baik +2 |
| 2 | Lin Haichao | tiga kapal hilang di kabut dalam sebulan; periksa catatan bea (layar `tema_dokumen`: cocokkan tiga surat muatan, satu palsu) → muatan selundupan **Minyak Bayangan** yang memadamkan lampu roh | (a) lapor ke Lin; (b) bicara dulu dengan Zhao Wanli (dia menyuap 120 NB × (band+1) agar diam) | (a) Nama Baik +3; (b) uang, bendera `pelabuhan_suap` |
| 3 | Mbah Lunas | lensa baru butuh **Kaca Garam Laut** (dari pasir pantai + Ikan Roh, racik di bengkel galangan: mini-gim `tema_racik` 3 langkah) atau beli dari Ibrahim (300 NB, Ibrahim minta pemain mengamati bintang bersamanya 1 malam di Pagoda Penunjuk) | racik / beli / malam bintang | lensa |
| 4 | malam setelah lensa dipasang | Serbuan Bajak Laut khusus: Kapten Hiu Hitam mencari **Mutiara Pelita** di mercusuar; bertahan sampai gelombang 3; Kakek Deng mengenali Hei Sha | bila `pelabuhan_suap`: Zhao membuka gudang bagi bajak laut (+1 gelombang) | Nama Baik +5 |
| 5 | Kakek Deng | Mutiara Pelita sudah habis cahayanya; yang baru hanya ada di **Laut Tanpa Batas**: naik Kapal Lintas Laut (gratis untuk quest), temukan Pulau Pasar atau Istana Naga laut (situs modul D, `SeaGate.tagOf`) dan dapatkan **Mutiara Fajar** (dibeli dari pedagang pulau 200 NB atau hadiah mengalahkan Yaksha laut, monster D yang ada) | beli / kalahkan | Mutiara Fajar |
| 6 | kembali | Hei Sha menunggu di Pemecah Ombak: Duel (bos tier 4, ranah = band pemain + 1; aturan Duel Pedang ramah bila pemain memilih "bicara dulu" dan berhasil cek Nama Baik ≥ 30: Hei Sha mau mendengar kisah gurunya) | (a) kalahkan dan serahkan ke Lin (dihukum); (b) damaikan dengan Kakek Deng (Hei Sha menjadi nakhoda Kapal Lintas Laut kedua, bajak laut berhenti menyerbu kota ini); (c) bunuh (Nama Baik −5 di kalangan pelaut, serbuan berhenti juga) | pusaka Hei Sha (golok `dao` roh menengah unik "Gigi Hiu") |
| 7 | upacara | Pelita Angin dinyalakan ulang (adegan 4 s: sinar mercusuar biru-emas menyapu teluk, semua NPC di dermaga `tatap_langit`), Hari Dewi Laut berikutnya diadakan untuk pemain | — | gelar "Penjaga Pelita", diskon Pasar Seberang 10 %, Kapal Lintas Laut −50 % selamanya, Nama Baik +10 |

Quest samping: `tema_ikan_roh` (Penjual Ikan Bibi Yu: 10 Ikan Roh untuk sup festival), `tema_tali_putus` (A-Lan: kalahkan
A-Lan dalam lomba perahu → dia dapat restu jadi nakhoda), `tema_bintang_ibrahim` (tiga malam pengamatan di Pagoda
Penunjuk, hadiah **Astrolab Kecil** kosmetik yang menunjuk utara sejati), `tema_lunas_pertama` (pesan perahu awan pertama,
Mbah Lunas menamainya bersama pemain).

### 38.T.4 Kota Oasis Gurun (`kota_tema/oasis`)

#### 38.T.4.1 Lore dan Sumber

- **Lore** (`tempat_tema_lore.oasis.*`): kota di tepi **Danau Bulan Sabit**, mata air yang tidak pernah tertimbun pasir
  selama seribu tahun. Para kafilah menyebutnya "Mata Gurun". Kota ini pintu terakhir sebelum Lautan Pasir Barat; di
  baliknya ada **Kota Terkubur** yang dulu lebih besar dari kota ini, lenyap dalam satu malam badai. Para pendeta Kuil
  Air menjaga **Batu Ukur Air** yang menandai tinggi muka danau; bila tandanya turun, kota bersiap haus. Para pelukis
  gua mengecat kisah kafilah di dinding tebing batu pasir sejak berdirinya kota.
- **Sumber** (`kultivator.kota_tema.oasis.source`): "Sumber: Dunhuang di Jalur Sutra; Mata Air Bulan Sabit (Yueyaquan),
  kolam berbentuk bulan sabit di antara bukit pasir Mingsha yang dikenal 'bernyanyi' saat angin bertiup dan konon tidak
  pernah tertimbun pasir; Gerbang Giok (Yumenguan) dan Yangguan, pos terakhir bagi pengembara yang menuju barat.
  Terinspirasi dari mural gua Mogao dan kafilah unta Jalur Sutra; tata kota, tokoh, harga dan angkanya rancangan game."

#### 38.T.4.2 Tata letak (grid sebelum diputar; gerbang di selatan)

```
             x = -1              x = 0               x = +1
 z = -1   [kuil_air]         [istana_pasir]      [kebun_palma]
 z =  0   [bazar_barat]      [mata_air]          [bazar_timur]
 z = +1   [hunian_tanah]     [gerbang_giok]      [karavanserai]
 z = +2                      [lapangan_kafilah]  (di luar Gerbang Giok)
```

Tembok: tanah padat (`packed_mud` + `mud_bricks`, 12 tinggi, menara sudut kubah), merlon segitiga; Gerbang Giok
(`gerbang_giok` 48 × 22 × 14, lengkung tunggal berlapis `prismarine_bricks` hijau giok di ambang, menara pengawas dua
sisi). Bukit pasir di luar adalah medan alami.

| Distrik | Lot (lx,lz) dan isi |
|---|---|
| `kuil_air` | (0,0)+(0,1) **Tebing Seribu Lukisan**: tebing batu pasir buatan 96 × 30 (`smooth_sandstone`, `cut_sandstone`, lapis `terracotta`) dengan 12 gua ceruk bertangga kayu (tiap gua 7 × 6 × 9: patung duduk `sandstone`, dinding = mural billboard klien bertema terbang-menari, kafilah, iblis kalah), titik `meditasi` ×12, `kerja:kuas` ×4, jangkar `kota_tema/mural`; (1,0) **Kuil Air**: aula kubah di tepi danau, kolam suci di tengah (air + `sea_lantern` di dasar), **Batu Ukur Air** (stela `prismarine` + entitas penanda tinggi air), jangkar `kota_tema/kuil_air`; (1,1) asrama pendeta, sumur timba (entitas `timba_sumur`) |
| `istana_pasir` | (0,0)+(1,0) **Istana Pasir**: istana tiga halaman (gerbang dalam, aula audiensi berkubah 28 × 18 × 24 dengan tiang `smooth_sandstone` berukir dan karpet merah, takhta bertirai), taman air dalam (kanal kecil, air mancur = partikel klien di atas `water`), menara angin (penangkap angin `cut_sandstone` 7 × 22 × 7); (0,1) barak pengawal gurun (kasur ×12, rak tombak); (1,1) **Perbendaharaan**: gudang air (tong `barrel` berderet, label kertas), ruang pajang hadiah kafilah |
| `kebun_palma` | (0,0)+(1,0) **Kebun Kurma**: 24 palma, saluran irigasi kecil dari danau (`water` dalam parit batu), titik `kerja:panen` ×8, pondok penjaga; (0,1) **Kandang Unta Istana** (kandang `oak_fence`, jerami, palung air; jangkar `kota_tema/kandang` → sewa unta); (1,1) rumah petani kurma, lumbung |
| `bazar_barat` | (0,0)+(1,0) **Bazar Rempah dan Permadani**: lorong beratap kain (tenda `orange/red/yellow_wool` di atas `oak_fence`, kain berkibar klien), 18 kios (titik `lapak` ×18): rempah dalam karung (`composter` berisi), permadani tergantung (`*_carpet` vertikal di dinding = `*_wool`), lampu kuningan (`lantern`); jangkar `kota_tema/bazar` ×2; (0,1) **Kedai Teh Unta** (`kota/teh`, `kota/kursi_pengembara` ×2) dengan bantal duduk (`*_carpet`) dan meja rendah; (1,1) rumah penenun permadani (`loom`, titik `kerja:tenun` ×3) |
| `mata_air` | (0,0)+(1,0)+(0,1)+(1,1) **Danau Bulan Sabit**: danau berbentuk bulan sabit 70 × 30 (dalam 4, dasar `sand` + `sea_lantern` jarang), tepi tangga batu, alang-alang (`sugar_cane`, `tall_grass`), paviliun minum di ujung bulan sabit, **Alun Oasis** di lengkung dalam (jangkar `kota/situs`, `kota/alun`, `kota/papan`), panggung tari (bundar 11 batu `cut_sandstone`, jangkar `kota_tema/panggung`) |
| `bazar_timur` | (0,0) **Bazar Kaca dan Giok**: kios kaca berwarna (`stained_glass` pajangan), kios giok (`kota/pusaka`), kios timbangan logam; (1,0) **Toko Air**: penjual air dan es (`blue_ice`), jangkar `kota_tema/toko_air`; (0,1) **Cabang Rumah Uang** + **Papan Gilda** (`kotabesar/layanan` `bank_cabang`, `papan_gilda`); (1,1) **Rumah Pandai Kuningan** (`anvil`, titik `kerja:palu` ×2), `kota/pil` |
| `hunian_tanah` | 4 lot rumah halaman tanah (pool `kota_tema/oasis/rumah`: rumah atap datar dengan tangga ke atap, tempat tidur di atap untuk malam panas = titik `kasur` luar ruang, ruang tengah sejuk), sumur timba, oven tanah (`smoker`), `bengkel` (`kotabesar/tukang`) |
| `gerbang_giok` | (0,1) Gerbang Giok + pos bea (jangkar `kota/penjaga` ×2, `kota_tema/bea_gurun`); (0,0) **Penginapan Pasir Bernyanyi** (`penginapan`, 10 kamar, halaman berpancuran); (1,0) **Rumah Makan Bakar** (`rumah_makan`); (1,1) **Stasiun Formasi** kecil (`kotabesar/cakram`) |
| `karavanserai` | (0,0)+(1,0)+(0,1)+(1,1) **Karavanserai Agung**: bangunan segi empat 80 × 80 berhalaman dalam, lantai bawah kandang unta dan gudang (titik `kandang_unta` ×12), lantai atas kamar pedagang (kasur ×16), menara penangkap angin di sudut, sumur tengah, jangkar `kota_tema/karavanserai` (kantor kafilah, kontrak kawal) |
| `lapangan_kafilah` | (di luar tembok) **Lapangan Kafilah**: perkemahan kafilah datang (tenda bundar kain `white_wool` + `brown_wool`, api unggun **mati**), palung air panjang, 2 tambatan kafilah (titik `kandang_unta` ×8), awal Jalan Kafilah (simpul jalan 38.C.6 kelas Jalan Desa berpasir) |

#### 38.T.4.3 Penduduk, peran, rutinitas dan animasi

Peran baru: `TUAN_BENTENG` (tier 4, PEJABAT, serban emas + jubah sutra bergaris + sabuk pedang melengkung),
`KAFILAH_BASHI` (2, PEDAGANG, topi bulu lebar + mantel panjang + cambuk), `PENUNTUN_UNTA` (1, PEKERJA, kain kepala
pelindung pasir menutup mulut + rompi), `PEDAGANG_BAZAR` (1, PEDAGANG, topi bundar bersulam + rompi), `PENDETA_AIR`
(2, RAHIB, jubah biru air + kerudung tipis + kendi), `PELUKIS_GUA` (1, CENDEKIA, celemek bernoda cat + kuas di sanggul),
`PENARI_GURUN` (1, PEKERJA, selendang panjang + gelang lonceng), `PENGAWAL_GURUN` (2, PENJAGA, helm runcing + baju
sisik + tombak). Penduduk data 220.

Rutinitas khas: **jeda panas** 11-15 (semua sipil di dalam atau di bawah atap teduh: `duduk_lantai`, `kipas`,
`tidur_duduk`; bazar tutup), bazar pagi 06-11 dan **Pasar Malam Bulan Sabit** 17-24 (tiap malam, lebih besar pada
`moonPhase` 2 dan 6 = bulan sabit: +6 kios tamu); malam panas (bioma desert, musim panas 38.N.3) separuh penduduk
tidur di atap.

| Jam | Penuntun unta / kafilah | Pedagang bazar | Pendeta air | Pelukis gua | Penari |
|---|---|---|---|---|---|
| 05-07 | memberi minum unta di palung (`angkat_air`), memasang muatan (`ikat_tali`) | `angkat_bawa` karung ke kios | `timba_sumur` lalu doa di kolam suci (`sujud_dupa` menghadap air) | naik tangga tebing | tidur |
| 07-11 | kafilah berangkat/datang (38.T.4.4), `pimpin_unta` | `seru_dagang`, `tawar_bazar` | membaca Batu Ukur Air, mencatat (`menulis`) | `lukis_dinding` di gua ceruk | latihan di halaman (`tari_putar` setengah) |
| 11-15 | jeda panas di karavanserai: `minum_teh`, `catur` | jeda panas | jeda | `lukis_dinding` (gua sejuk) | tidur siang |
| 15-17 | perawatan unta (`sapu` badan unta = klip `sapu` varian) | buka lagi | upacara sore di tepi danau | `membaca` | dandan (`kipas`) |
| 17-24 | `cerita` di api unggun lapangan kafilah | Pasar Malam: lampu kuningan menyala (klien), `seru_dagang` | lentera di tepi danau | pulang | **pertunjukan** di panggung 19-21 (`tari_putar`, penonton impostor duduk) |

Reaksi badai pasir (38.T.4.6): semua sipil `lari` ke rumah/karavanserai, penuntun membuat unta berlutut (animasi unta
`berlutut`) dan berlindung di baliknya (`takut_sembunyi`), penjaga menutup Gerbang Giok (entitas gerbang pola
`GerbangKotaEntity` lebar 7).

#### 38.T.4.4 Kafilah unta

- **Kafilah** = data yang berjalan sepanjang sisi jalan 38.C.6 antara Kota Oasis dan simpul lain (kota tema, ibu kota,
  `pos/dagang`, kota 34) dengan kecepatan 0.7 blok/s data; **rupa** kafilah ditentukan kota asal atau tujuan bertema
  oasis: 4-8 `UntaKafilahEntity` beriring (tali antar-unta digambar klien), 1 KAFILAH_BASHI, 1-2 PENUNTUN_UNTA, 2
  PENGAWAL_GURUN. Tubuh hanya bila pemain ≤ 64 blok dari posisi data (pola 38.C.6.3 dan `KafilahJalan` 38EK; 38EK
  membaca `TemaApi.rupaKafilah(situsAsal, situsTujuan)` → `GEROBAK | UNTA | KAPAL`, 38.T.14.3).
- Jadwal: Kafilah Agung datang ke Kota Oasis tiap 5 hari dunia (`d % 5 == 1`, 8000) lewat Gerbang Giok dengan arak-arakan
  (genderang, lonceng unta = suara `block.note_block.bell` pitch acak), membongkar di karavanserai → stok Bazar Timur dan
  Barat berganti, harga barang yang dibawa turun 10 % selama 2 hari. Kafilah berangkat `d % 5 == 3`.
- **Ikut kafilah** (layar `tema_kafilah`): pemain mendaftar sebagai pengawal (misi gilda `kawal_unta`, 38.T.11) atau
  sebagai penumpang (8 NB, naik unta sewaan dalam kafilah; perjalanan nyata berjalan bersama tubuh kafilah, bisa
  dipercepat "tidur di pelana": layar gelap 3 s, pemain dipindah ke posisi kafilah 300 blok di depan; tidak tersedia
  saat ada bahaya dalam 64 blok).
- **Sewa unta** (Kandang Unta Istana / karavanserai): `UntaKafilahEntity` mode tunggang, 12 NB per hari dunia, kecepatan
  0.16 (lebih cepat dari jalan kaki, lebih lambat dari kuda), lari 4 s tiap 10 s (pola dash vanilla Camel), tidak tenggelam
  di pasir isap; habis sewa atau ditinggal > 64 blok selama 2 menit → unta pulang (berjalan menjauh lalu lenyap). Unta
  vanilla juga ada di kandang sebagai entitas templat (bisa dijinakkan dengan cara vanilla).

#### 38.T.4.5 Layanan dan permainan khas

- **Tawar-menawar bazar** (layar `tema_tawar`, semua kios bazar): harga buka `1.30 × harga dasar`; pemain menawar
  hingga 3 putaran (geser harga); pedagang punya **suasana hati** 0-100 (watak 38.S.2 + Nama Baik + jam: pagi ramah, jeda
  panas ketus). Tawaran ≥ batas rahasia (`0.90 .. 1.10 × dasar`, hash pedagang+hari) → diterima; terlalu rendah
  (< batas − 15 %) → suasana −25, pedagang `geleng` dan `marah_tunjuk`; suasana < 20 → "tidak jual hari ini" untuk barang
  itu. Harga akhir tidak pernah < `0.90 × dasar`; beli-kembali tetap ≤ 0.5 (38.M.5).
- **Kuil Air**: **Restu Mata Air** 3 NB (maks 1 per hari dunia): Regenerasi I 60 s + kebal "Tertiup Pasir" 30 menit
  dunia; **air suci** (botol `potion` air bertanda, item `kultivator:tema/air_suci`, 2 NB): bahan pil penyejuk dan
  syarat quest; meditasi di tepi kolam suci: situs `SpiritSites` ekstra "Mata Gurun" ×1.25 (dalam CAP).
- **Tebing Seribu Lukisan**: membaca mural (klik billboard) membuka **Kisah Mural** (12 kisah, 38.T.4.7) dan sekali per
  mural **+0.5 pemahaman**; pelukis menerima **pesanan mural**: pemain membayar 50 NB dan memilih satu prestasinya
  (gelar, bos terkuat, kesengsaraan), dan dalam 3 hari mural pemain muncul di gua ceruk ke-12 (billboard klien bergambar
  ikon prestasi + nama pemain; data di `KotaTemaData`), dilihat pemain lain di server itu.
- **Istana Pasir**: audiensi Tuan Benteng (Nama Baik ≥ 50) → **Surat Jalan Gurun** (item: bebas bea gerbang semua kota
  oasis, pengawal kafilah tidak menyerang bila salah paham, 30 hari dunia) dan Perintah Benteng (tugas mingguan: bersihkan
  satu `reruntuhan_gurun`, kawal satu kafilah, buru satu kawanan binatang gurun).
- **Bea gerbang** untuk barang dagang yang dibawa masuk: 3 % nilai peti dagang (38.T.10), dibebaskan oleh Surat Jalan.
- **Festival: Malam Tari Putar** (bulan purnama `moonPhase == 0`, 18000-21000; batal saat badai): panggung tari di
  danau, 4 PENARI_GURUN `tari_putar` serempak, musik (suara note block berirama dari titik panggung), lentera kuningan di
  tepi danau, penonton impostor; pemain boleh ikut **Tari Putar** (mini-gim irama 30 s: tekan tombol arah sesuai
  ketukan; skor ≥ 70 → Selendang Penari kosmetik + Nama Baik +1, maks sekali per festival).
- **Pasar Bulan Sabit** (bulan sabit, 38.T.4.3): +6 kios tamu dengan barang langka (giok Barat, peta reruntuhan,
  sesaji binatang gurun) stok 1 per kios.

#### 38.T.4.6 Bahaya: badai pasir dan reruntuhan

- **Badai Pasir** (`BadaiPasir`, 38TK; tiap 6 ± 2 hari dunia per Kota Oasis, siang; atau dipicu quest): pusat kota,
  jari-jari 600 blok, 2400 tick. Pertanda 60 s sebelumnya: dinding pasir di cakrawala (quad klien raksasa coklat
  bergerak), angin bersiul (suara), baris `tema.oasis.badai`; NPC berlindung (38.T.4.3), Gerbang Giok menutup, kafilah di
  jalan berhenti dan unta berlutut.
  - Klien (38TV): kabut pasir (warna `#C8A060`, jarak pandang 16 blok pada Normal dan Maksimal; Minimal
    hanya overlay warna tanpa partikel), partikel pasir horizontal, suara angin loop; jarum kompas dan penunjuk quest bergetar ±20°.
  - Server: pemain di luar tembok kota yang melihat langit (`canSeeSky`) dan tanpa Restu Mata Air mendapat
    **Tertiup Pasir** (Lambat I, diperbarui tiap 10 s) dan 0.5 kerusakan per 20 s bila tidak memakai helm/kerudung (item
    `kultivator:tema/kerudung_pasir`, 15 NB); di dalam tembok tanpa efek. **Cuaca dunia tidak diubah** (tidak ada
    `setWeatherParameters`): badai adalah keadaan lokal 38TK + gambar klien.
  - Sesudah badai: 2-4 **Temuan Pasir** (entitas berkilau pola `TandaEntity`, 38TK `TemuanPasirEntity`) muncul di
    bukit pasir 60-300 blok dari kota: Pecahan Mural, Koin Kuno, Lempeng Peta (bagian peta ke `reruntuhan_gurun`
    terdekat), dijual ke Pelukis atau dipakai quest.
  - `Sibuk`: badai tidak membuat sibuk; peristiwa musuh lain tidak dimulai selama badai di jari-jarinya.
- **Reruntuhan Gurun** (satelit 38.T.2.6): Lempeng Peta dan Kisah Mural menunjuk ke sana (`DuniaSites.locateFor`
  `kota_tema/reruntuhan_gurun`); bos **Raja Kalajengking** (`KalajengkingRajaEntity`, 38.T.9, tier menurut cincin,
  `markRealmFoe`) muncul saat pemain pertama ≤ 20 blok dari sarangnya; hadiah: Ekor Racun Kalajengking (bahan pil/pusaka),
  Giok Barat, peti tier; sekali per pemain per reruntuhan.

#### 38.T.4.7 Tokoh dan rantai quest "Mata Air yang Menyusut"

| Tokoh | Peran | Watak, suara | Muncul |
|---|---|---|---|
| **Sha Wuyan** | TUAN_BENTENG | pendiam, berwibawa, takut kotanya mati seperti Kota Terkubur; `boss` | istana, bab 1, 6 |
| **Bulat Khan** | KAFILAH_BASHI | pemimpin kafilah tua, tertawa keras, tahu semua bintang penunjuk jalan; `m1` | karavanserai, bab 2, 5 |
| **Pendeta Shui Lian** | PENDETA_AIR | lembut, tegas soal air, membaca Batu Ukur Air; `f3` | kuil air, bab 1, 3, 6 |
| **Mo Hua** | PELUKIS_GUA | pelukis muda pelupa, mendengar "suara" dari mural; `m3` | tebing lukisan, bab 3-4 |
| **Si Kecil Ajun** | ANAK (pembaca pasir) | anak yatim kafilah yang bisa "membaca" arah angin di pasir; `f1` | lapangan kafilah, bab 2, 5 |
| **Xie Duo, Pertapa Kalajengking** | TETUA_IBLIS varian gurun | dulu pendeta air yang diusir; memelihara Raja Kalajengking dengan urat air kota; `demon` | bab 4-6 |

Rantai `tema_oasis_mata_air` (6 tahap):

| # | Pemicu / pemberi | Isi | Cabang | Hadiah |
|---|---|---|---|---|
| 1 | masuk kota; Shui Lian | Batu Ukur Air turun dua tanda dalam sebulan; antre air dijatah (NPC antre di sumur = adegan hidup) | — | situs, Nama Baik +2 |
| 2 | Bulat Khan + Ajun | ikut Kafilah Agung keluar 600 blok (atau kawal sendiri); Ajun membaca pasir: jejak air menuju barat; badai pasir dipicu quest di tengah jalan: lindungi unta (6 binatang gurun menyerang) | menemukan **Lempeng Peta** / kehilangan kafilah (cari Ajun yang tersesat di badai: entitas jejak kaki klien) | 60 × (band+1) NB |
| 3 | Mo Hua | mural ke-7 "Pendeta yang Diusir" bergerak di mata pemain (billboard beranimasi 2 frame): baca 4 mural lain untuk merangkai kisah Xie Duo | — | +1 pemahaman |
| 4 | Lempeng Peta | masuk `reruntuhan_gurun` terdekat; aula ketiga: formasi penyedot air (lingkar rune klien + 4 tiang = entitas `PilarRuneEntity` 38TK) dijaga Raja Kalajengking | hancurkan 4 tiang (formasi pecah) / segel dengan air suci (Shui Lian) | Ekor Racun, peti |
| 5 | Xie Duo muncul | Xie Duo menuntut Kuil Air menerimanya kembali; dialog bercabang (watak ditentukan pilihan bab 4) | (a) duel (bos, ranah band+1); (b) bujuk: Nama Baik ≥ 40 dan bawa air suci → Xie Duo menyerah, menjadi pertapa di gua ke-12; (c) serahkan Ajun sebagai "pembaca air" (ditolak sistem: pilihan ini membuat Ajun lari, Nama Baik −10, Xie Duo tetap harus dilawan) | (a) pusaka kebut `kebut` unik "Ekor Angin"; (b) teknik air Xie Duo (giok) |
| 6 | Shui Lian | upacara pemulihan: Batu Ukur Air naik (entitas penanda bergerak naik 4 tanda selama adegan 4 s), Malam Tari Putar diadakan untuk pemain | — | gelar "Pembawa Air", Surat Jalan Gurun gratis selamanya, Nama Baik +10 |

Quest samping: `tema_unta_hilang` (unta Bulat Khan kabur saat badai; temukan dengan Ajun), `tema_mural_pesanan`
(mural pemain), `tema_permadani_ratu` (penenun butuh pewarna dari Ekor Racun dan bunga gurun), `tema_kurma_emas`
(panen kurma di Kebun Kurma 5 hari berturut, hadiah Kurma Emas: makanan pemulih Qi 10 %).

### 38.T.5 Kota Air (`kota_tema/air`)

#### 38.T.5.1 Lore dan Sumber

- **Lore** (`tempat_tema_lore.air.*`): kota kanal yang dibangun di atas "seribu jembatan" (sebenarnya 64; orang suka
  membesar-besarkan). Setiap rumah punya pintu depan ke jalan dan pintu belakang ke air. Kekayaannya dari **sutra** (Rumah
  Sutra Su sudah lima generasi) dan **teh** dari bukit di utara. Legenda kota: **Xiu Niang**, penenun yang tenggelam di
  malam pernikahannya lima puluh tahun lalu, kainnya tak pernah selesai; pada malam tanpa bulan, ada satu lentera yang
  berlayar melawan arus.
- **Sumber** (`kultivator.kota_tema.air.source`): "Sumber: Zhouzhuang di Kunshan dekat Suzhou, kota air dengan Jembatan
  Kembar dari era Ming: Jembatan Shide berlengkung bundar dan Jembatan Yong'an berlengkung persegi yang bertemu di sudut
  dua kanal; Kanal Besar dan taman-taman klasik Suzhou; sutra Suzhou dan teh hijau Biluochun dari bukit Dongting di Danau
  Tai. Lentera air: tradisi melepas lentera teratai di sungai pada malam Festival Zhongyuan di Jiangsu dan Fujian untuk
  menerangi jalan arwah. Jadwal festival, tokoh, harga dan angkanya rancangan game."

#### 38.T.5.2 Tata letak dan jaringan kanal (grid sebelum diputar)

```
             x = -1               x = 0                 x = +1
 z = -1   [taman_sastra]  ~~~~ [rumah_sutra]  ~~~~~~ [bukit_teh]
             ‖                    ‖                     ‖
 z =  0   [jembatan_kembar] ~~ [danau_lentera] ~~~~~ [pasar_kanal]
             ‖                    ‖                     ‖
 z = +1   [hunian_kanal]  ~~~~ [gerbang_air]  ~~~~~~ [dermaga_perahu]
                                  ║ (Kanal Utama keluar lewat Pintu Air)
```

`~~` dan `‖` = kanal 5 lebar dengan jalan setapak 2 di satu sisi (kode sisi `K`); setiap persilangan kanal punya
jembatan lengkung batu (`stone_brick_stairs` + `stone_bricks`, puncak +4, pagar `stone_brick_wall`) di lot sudut; antar-
distrik juga jalan 3 (kode `J`) di sisi lain lot. **Kanal Utama** masuk dari luar lewat Pintu Air (`gerbang_air`) ke
Danau Lentera; kanal lingkar mengitari ketiga baris. Tembok kota rendah (6 tinggi, `white_terracotta` + genteng hitam)
dengan dua gerbang darat (timur, barat) dan satu **Pintu Air** (entitas `pintu_air`, terbuka 05:00-21:00).

| Distrik | Lot (lx,lz) dan isi |
|---|---|
| `taman_sastra` | (0,0)+(1,0)+(0,1)+(1,1) **Taman Sang Sastrawan**: taman klasik 90 × 90: kolam berliku (air + `lily_pad`), gunungan batu berongga (gumpalan `tuff`/`calcite`/`diorite` berlubang 6-9 tinggi), gerbang bulan (lingkar `stone_bricks` 5 diameter di dinding putih), lorong beratap berjendela kisi, Paviliun Menyimak Hujan, Paviliun Bulan di Air (lantai di atas kolam), perpustakaan kecil, bambu, pinus pot; titik `meditasi` ×8, `obrol` ×6, `pandang` ×4, jangkar `kota_tema/taman` |
| `rumah_sutra` | (0,0)+(1,0) **Rumah Sutra Su**: rumah usaha tiga halaman: halaman ulat sutra (rak nampan bertingkat `oak_slab` + `moss_carpet` "daun murbei", ulat = klien), ruang pemintalan (titik `kerja:pintal` ×4), aula tenun dengan 6 `AlatTenunEntity` (38.T.9), galeri pajang kain (spanduk kain berwarna = `*_banner` + kain berkibar klien), kantor Nyonya Su; jangkar `kota_tema/sutra`; (0,1) kebun murbei (pohon `oak` rendah berdaun `azalea_leaves`); (1,1) rumah pekerja sutra |
| `bukit_teh` | (0,0)+(1,0) **Teras Teh**: bukit teh buatan bertingkat 4 teras (`azalea`, `flowering_azalea` berbaris di atas `rooted_dirt`), jalan setapak batu, pondok pengering teh (rak `bamboo_mosaic`, tungku pemanggang `smoker`, titik `kerja:sangrai` ×2); (0,1) **Paviliun Teh Awan** (rumah teh dua lantai, jangkar `kota/teh` + `kota_tema/teh_awan`, meja upacara teh ×6, balkon di atas kanal); (1,1) gudang teh + rumah pemetik |
| `jembatan_kembar` | (0,0)+(1,0)+(0,1)+(1,1) **Sudut Jembatan Kembar**: persilangan dua kanal dengan **Jembatan Lengkung Bundar** (lengkung setengah lingkaran 7 lebar, tinggi +6) dan **Jembatan Lengkung Persegi** (lengkung persegi 7 lebar) yang bertemu di sudut; jalan toko di tepi kanal (12 toko kecil bertingkat dua: kipas, payung kertas, kue beras, tahu; titik `lapak` ×12), rumah bangsawan **Wisma Zhang** (enam halaman kecil, ruang tamu, ruang belajar), titik `pandang` (lukisan terkenal: pemain berdiri di sana → kartu judul "Kenangan Kampung Halaman" kecil) |
| `danau_lentera` | (0,0)+(1,0)+(0,1)+(1,1) **Danau Lentera** 64 × 50 (dalam 3, dasar `clay`, teratai), **Panggung Air** di tengah (panggung kayu di atas tiang dengan atap pelana, jangkar `kota_tema/panggung_air`), dermaga lingkar dengan 8 titik `lentera_air`, pagoda kecil di pulau, **Alun Tepi Danau** (jangkar `kota/situs`, `kota/alun`, `kota/papan`), kuil kecil Dewa Air |
| `pasar_kanal` | (0,0)+(1,0) **Pasar Air Pagi**: kanal lebar 9 dengan 10 tambatan perahu-lapak (perahu kanal tertambat menjadi kios, titik `lapak` di perahu), tangga cuci ke air (titik `kerja:cuci` ×6); (0,1) **Cabang Rumah Uang** + **Papan Gilda** (`bank_cabang`, `papan_gilda`), `kota/pil`; (1,1) **Rumah Makan Ikan Danau** (`rumah_makan`, jendela ke kanal) |
| `hunian_kanal` | 4 lot rumah tepi kanal (pool `kota_tema/air/rumah`: rumah dua lantai berdinding putih, tangga batu turun ke air di belakang, perahu kecil tertambat = entitas templat diam), sumur, kuil leluhur kecil, `bengkel` (`kotabesar/tukang`) |
| `gerbang_air` | (0,1) **Pintu Air dan Gerbang Air**: menara gerbang di atas kanal (lengkung air 7 + gerbang darat 3 di samping), entitas `pintu_air` (daun kayu turun-naik); (0,0) **Penginapan Bulan di Kanal** (`penginapan`, kamar dengan jendela ke air); (1,0) `kotabesar/cakram` stasiun kecil di halaman; (1,1) pos jaga (`kota/penjaga` ×2) |
| `dermaga_perahu` | (0,0)+(1,0) **Galangan Perahu**: rumah perahu (perahu terbalik di atas kuda-kuda, titik `kerja:palu` ×3), **Rumah Perahu Naga** (dua perahu naga = entitas diam saat bukan lomba, kepala naga berukir), jangkar `kota_tema/sewa_perahu`; (0,1) **Halte Perahu Pos** pusat (tambatan ×4); (1,1) rumah pendayung |

**Perahu Pos** (rute `jalur_perahu` titik NBT): empat halte (Gerbang Air, Jembatan Kembar, Danau Lentera, Rumah Sutra),
satu perahu per rute berputar tiap 6 menit dunia; 1 NB, duduk sebagai penumpang (entitas `PerahuKanalEntity` 3 kursi,
pendayung = tubuh NPC PENDAYUNG penumpang di buritan dengan klip `dayung_yuloh`), `Sibuk` `tema.perahu`, turun di halte
mana pun.

#### 38.T.5.3 Penduduk, peran, rutinitas dan animasi

Peran baru: `NYONYA_SUTRA` (tier 2, PEDAGANG, sanggul tusuk giok + jubah sutra bersulam), `PENENUN_SUTRA` (1, PEKERJA,
lengan baju diikat + celemek), `PEMELIHARA_ULAT` (1, PEKERJA, keranjang daun), `AHLI_TEH` (2, CENDEKIA, topi sarjana
lembut + jubah hijau teh), `PEMETIK_TEH` (1, PEKERJA, caping + keranjang punggung kecil), `PENDAYUNG` (1, PEKERJA, caping
biru + kain kepala), `SASTRAWAN` (2, CENDEKIA, topi sarjana + kipas). Penduduk data 240.

| Jam | Pendayung | Penenun / pemelihara ulat | Pemetik / ahli teh | Sastrawan | Warga |
|---|---|---|---|---|---|
| 04-06 | menyiapkan perahu (`sapu`, `ikat_tali`) | pemelihara memberi daun (`lempar_pakan` ke nampan) | **petik teh pagi** (musim semi: `petik_teh` di teras, embun klien) | tidur | `basuh_pakaian` di tangga cuci kanal |
| 06-10 | Pasar Air Pagi: perahu-lapak tertambat, `seru_dagang` dari perahu; Perahu Pos jalan | `tenun` di alat tenun (entitas ikut bergerak) | `sangrai` (klip `masak` varian) di pondok | `membaca` di Paviliun Menyimak Hujan | belanja ke pasar air |
| 10-14 | `dayung_yuloh` + `nyanyi_perahu` (gelembung kalimat lagu perahu) | tenun; makan siang di halaman | upacara teh pertama (`seduh_teh` di Paviliun Teh Awan) | `menulis` puisi di gazebo | makan, tidur siang |
| 14-18 | Perahu Pos | tenun, pajang kain | tamu teh | `catur` dengan sastrawan lain di Paviliun Bulan di Air | `obrol` di jembatan, anak `memancing` dari jembatan |
| 18-22 | menambat perahu, menyalakan lentera buritan (klien) | pulang | pulang | `tatap_langit` (bulan), `kipas` | `minum_teh` di jendela kanal, lentera rumah |

Hujan (sering di bioma ini): payung kertas (prop 25), Perahu Pos tetap jalan dengan atap anyaman (model), sastrawan
justru keluar ke Paviliun Menyimak Hujan (`duduk_kursi` + `tatap_langit`).

#### 38.T.5.4 Permainan khas

- **Sewa perahu** (`kota_tema/sewa_perahu`): `PerahuKanalEntity` yang **didayung pemain** sendiri (kendali seperti perahu
  vanilla, kecepatan 0.08, hanya mengikuti air; implementasi sendiri, entitas tidak disimpan, kembali ke tambatan bila
  ditinggal 2 menit) 2 NB per jam dunia; atau **Tur Kanal** berpemandu (3 NB, perahu berjalan sendiri sepanjang rute
  wisata 6 menit dengan Bibi Chuan menyanyi dan menceritakan 6 tempat; pertama kali: +1 pemahaman, `tema.air.tur`).
- **Rumah Sutra** (layar `tema_sutra`): beli **Sutra Awan** (bahan dagang/tempa, 38.T.10), **pesanan jubah sutra**
  (kosmetik jubah dengan warna dan motif pilihan: awan, bangau, teratai, naga; 80 NB, jadi 2 hari; memakai slot kosmetik
  jubah `KotaGear`-pola pemain bila ada, verifikasi; bila tidak: item zirah dada kain tanpa atribut yang bisa diwarnai),
  **Kipas Sutra** (prop kosmetik di tangan), magang tenun (mini-gim irama 20 s di alat tenun; skor → sutra gratis 1-3,
  maks 1 per hari).
- **Paviliun Teh Awan** (layar `tema_teh`): upacara teh dengan AHLI_TEH (adegan 4 s, klip `seduh_teh` + `tuang_teh`),
  **teh khas**: Teh Bi Luo (8 NB: pemahaman +0.03/menit meditasi selama 10 menit; tidak bertumpuk dengan teh lain, yang
  terbesar berlaku), Teh Embun Gunung (5 NB: regen Qi +8 % 5 menit), Teh Seribu Tahun (60 NB, pita ≥ 3, stok 1/hari:
  sekali per hari dunia mengurangi tekanan iblis hati 5 bila API publik ada, verifikasi; selain itu pemahaman +0.05/menit
  10 menit). **Petik teh** musim semi (pemain di teras 04-08, 10 petikan) → daun teh segar (bahan, dijual 1 NB/lembar ke
  ahli teh, maks 20/hari).
- **Taman Sang Sastrawan**: meditasi di taman: situs ekstra "Taman Klasik" ×1.2 (dalam CAP); **Pasangan Bait** (layar
  `tema_bait`, 30 bait milik 38TK ditulis sendiri: pilih bait kedua yang serasi dari tiga; 3 benar berturut-turut → +1
  pemahaman, maks sekali per hari).
- **Festival: Malam Lentera Air** (bulan mati `moonPhase == 4`, 13000-18000): warga melepas lentera teratai dari titik
  `lentera_air`; lentera (quad klien, 38.T.9.4) hanyut mengikuti spline kanal ke Danau Lentera dan berputar perlahan di
  danau; pemain membeli lentera (1 NB, maks 3) dan menulis **harapan** (≤ 32 huruf; teksnya hanya tampil di layar pemain
  itu; pemain lain melihat lentera tanpa teks), lalu melepasnya di titik mana pun → `+1 Nama Baik` (maks 1), rumor kecil.
  Pertunjukan di Panggung Air (penyanyi + musik), perahu-perahu berlentera berkeliling. Lentera milik pemain dibatasi
  `EpicBudget` dan hilang setelah 600 tick.
- **Festival: Lomba Perahu Naga** (`d % 14 == 6`, 8000-10000): 4 `PerahuNagaEntity` (38.T.9.1) di jalur danau + Kanal
  Utama (2 putaran); pemain boleh menjadi **penabuh genderang** satu perahu (mini-gim irama 60 s: ketepatan menaikkan
  kecepatan perahunya 0-12 %); juara → 20 NB per anggota + Nama Baik +2 + Pita Naga kosmetik; penonton impostor di tepi
  kanal dan jembatan.
- **Bahaya: Tikus Air** (geng pencuri kanal; tiap 4-7 hari, malam): pencuri merampok perahu-lapak atau Rumah Sutra
  (adegan: NPC `tunjuk` + "Pencuri!"), lalu kabur dengan **sampan cepat** (`PerahuKanalEntity` varian hitam) di jalur
  kanal; pemain mengejar (dengan perahu sewaan, di jalan setapak, atau terbang); menghentikannya (serangan ke sampan
  atau pencuri) → barang kembali, Nama Baik +3, 15 × (band + 1) NB. Pencuri `markRealmFoe` hanya bila melawan; tidak pernah
  mencuri dari pemain.
- **Bahaya kecil: Arwah Kanal** (hanya saat quest `tema_air_benang` bab 4 aktif atau Malam Lentera Air): 2-4 arwah lemah
  (tubuh `KultivatorNpc` peran `SISA_JIWA` varian air, tembus pandang, `markRealmFoe`) muncul di danau; padam bila lentera
  doa dinyalakan di dekatnya.

#### 38.T.5.5 Tokoh dan rantai quest "Benang Merah di Bawah Air"

| Tokoh | Peran | Watak, suara | Muncul |
|---|---|---|---|
| **Nyonya Su Jin** | NYONYA_SUTRA | anggun, teliti, keras pada diri sendiri; `f3` | rumah sutra, bab 1, 5 |
| **Su Jin'er** | PENENUN_SUTRA (putri Su Jin) | ceria, pemberontak kecil, penenun berbakat; `f1` | rumah sutra, bab 5-6 |
| **Lu Yuqing** | AHLI_TEH | lembut bicara, menyimpan rahasia kota; `m1` | paviliun teh, bab 2, 4 |
| **Bibi Chuan** | PENDAYUNG | penyanyi perahu, tahu setiap sudut kanal; `f1` (nada lebih rendah) | perahu, bab 2-3 |
| **Sastrawan Wen Shi** | SASTRAWAN | tua, suka puisi sedih, kakek Xiu Niang dari pihak ibu; `elder` | taman, bab 4 |
| **Xiu Niang** | SISA_JIWA (arwah penenun) | sedih, sopan, terikat kain yang tak selesai; `f2` (bergema) | danau, bab 4-6 |
| **Lao Shu "Tikus Tua"** | pemimpin Tikus Air (`KultivatorNpc`, tier 2) | licik, pengecut, menjual apa saja; `m3` | bab 2-3 |

Rantai `tema_air_benang` (6 tahap):

| # | Pemicu | Isi | Cabang | Hadiah |
|---|---|---|---|---|
| 1 | masuk kota; Su Jin | ulat sutra Rumah Su sakit (nampan klien: ulat diam, daun menghitam); periksa daun murbei dan air kanal | — | situs, Nama Baik +2 |
| 2 | Bibi Chuan | tur kanal malam: noda hitam di air dekat gudang tua; Lu Yuqing mengenalinya sebagai **Minyak Bayangan** (penyelundupan yang sama dengan quest pelabuhan; bila pemain sudah tahu dari pelabuhan, dialog tambahan) | — | — |
| 3 | malam berikutnya | kejar sampan Lao Shu (bahaya Tikus Air versi quest); tangkap → Lao Shu mengaku menerima minyak dari "orang berjubah merah" dari Kota Iblis (penunjuk ke 38.T.7) | serahkan ke penjaga / lepaskan dengan janji jadi mata-mata (bendera `air_lao_shu_mata`) | 30 × (band+1) NB |
| 4 | Malam Lentera Air berikutnya (atau `uji tema acara air lentera`) | lentera yang melawan arus; Xiu Niang muncul di Paviliun Bulan di Air; Wen Shi menceritakan kisahnya; tenangkan 3 Arwah Kanal | bicara dengan Xiu Niang (Nama Baik ≥ 20) / usir dengan kekuatan (dia lenyap sementara, bab 5 sulit) | — |
| 5 | Su Jin + Jin'er | selesaikan **Kain Xiu Niang**: kumpulkan Sutra Awan ×6 (rumah sutra), Daun Teh Embun ×10 (petik), Air Suci ×1 (dari Kota Oasis bila diketahui, atau air mata air roh 38.C.5); tenun bersama Jin'er (mini-gim tenun 3 babak) | — | — |
| 6 | Malam Lentera Air | lepas kain sebagai lentera perahu; Xiu Niang tenang (adegan 4 s: lentera besar hanyut, arwah memudar menjadi cahaya, warga `sujud_dupa`); ulat sutra pulih (nampan hidup lagi) | bila bab 4 "usir": Xiu Niang tidak muncul, kain dilepas tanpa adegan perpisahan, hadiah lebih kecil | gelar "Penenun Malam", Jubah Sutra Benang Merah (kosmetik unik), diskon Rumah Sutra 15 %, Nama Baik +10 |

Quest samping: `tema_teh_pertama` (petik teh musim semi bersama Lu Yuqing; hadiah 5 Teh Bi Luo dan izin
menyeduh sendiri di Paviliun: Teh Bi Luo −25 % untuk pemain itu),
`tema_bait_tua` (Wen Shi kehilangan bait puisi terakhirnya di Pasangan Bait), `tema_perahu_naga` (menangkan lomba sebagai
penabuh), `tema_anak_jembatan` (anak jatuh ke kanal saat hujan deras → selamatkan: berenang ke titik dan
bawa ke tangga cuci; Nama Baik +3).

### 38.T.6 Kota Gunung (`kota_tema/gunung`)

#### 38.T.6.1 Lore dan Sumber

- **Lore** (`tempat_tema_lore.gunung.*`): kota yang tumbuh di sepanjang jalan ziarah ke **Aula Emas** di puncak. Di kaki
  ada pasar dan penginapan peziarah; di lereng, rumah-rumah panggung bertumpu pada tiang di atas jurang dan teras herba;
  di puncak, biara tua yang menjaga **Lonceng Awan**. Barang naik lewat punggung para kuli atau lewat **Kereta Gantung
  Tali Besi** yang dibangun Paman Tie Suo dua puluh tahun lalu; para biksu menganggapnya "jalan malas", para pedagang
  menganggapnya keajaiban. Kera-kera gunung menganggap semua orang membawa makanan untuk mereka.
- **Sumber** (`kultivator.kota_tema.gunung.source`): "Sumber: Gunung Emei, salah satu gunung suci Buddhisme Tiongkok,
  dengan Puncak Emas (Jinding) setinggi ±3.077 m, aula emas dan patung Samantabhadra (Puxian) bersepuh; Kuil Baoguo di
  kaki gunung adalah titik awal ziarah; matahari terbit, lautan awan dan 'cahaya Buddha' dikenal sebagai keajaiban
  puncaknya. Jalan papan di tebing terinspirasi dari jalan papan di Puncak Selatan Huashan; kota bertingkat dengan rumah
  panggung di tepi tebing terinspirasi dari kota gunung Chongqing. Tata kota, tokoh, harga dan angkanya rancangan game."

#### 38.T.6.2 Tata letak (grid sebelum diputar; menanjak ke utara)

```
             x = -1                 x = 0                  x = +1
 z = -1   [tebing_papan]   ═G═  [puncak_emas]    ═G═   [biara_awan]        baris atas  (dy +30 .. +72)
             ║G                     ║G  ↑ kereta gantung   ║G
 z =  0   [teras_hunian]   ═G═  [aula_herba]     ═G═   [teras_kebun]       baris tengah (dy +12 .. +42)
             ║G                     ║G  │                  ║G
 z = +1   [penginapan_peziarah] ═ [kaki_gerbang]  ═══   [pasar_kaki]        baris bawah (dy 0 .. +12)
```

`G` = sambungan tangga (kode sisi `G`): lot tepi baris memuat **tangga batu** 5 lebar dengan bordes tiap 12 anak tangga,
gapura kecil (`paifang` kayu merah) di tiap bordes, pagar batu di sisi jurang, lentera batu. Antar-teras yang lebih dari
24 tinggi memakai tangga zig-zag di dalam lot `tangga_tinggi`. **Kereta Gantung** dari stasiun bawah (`kaki_gerbang`) ke
stasiun atas (`puncak_emas`) melewati tiang tengah di `aula_herba` (dua bentang); tali = gambar klien (38.T.9.3). Tembok
hanya di baris bawah (gerbang kaki); baris atas dibatasi tebing alami + pagar.

| Distrik | Lot (lx,lz) dan isi |
|---|---|
| `kaki_gerbang` | (0,1) **Gerbang Kaki Gunung**: gapura batu tiga pintu, pos jaga (`kota/penjaga` ×2), tugu "Seribu Anak Tangga" (stela), awal `jalur_tangga`; (0,0) **Stasiun Bawah Kereta Gantung**: rumah roda besar (roda kayu Ø 9 = entitas `roda_katrol` berputar saat kereta jalan), loket (jangkar `kota_tema/lift` data `stasiun=bawah`), ruang tunggu; (1,0) **Pos Kuli**: gudang muatan, rak pikulan, papan jasa angkut (jangkar `kota_tema/kuli`), kasur kuli ×8; (1,1) **Alun Kaki** (jangkar `kota/situs`, `kota/alun`, `kota/papan`) dengan pohon tua besar (catatan di bawah tabel) |
| `pasar_kaki` | (0,0)+(1,0) **Pasar Peziarah**: jalan menanjak landai dengan 14 kios (dupa, tasbih, tongkat bambu, payung, makanan kering; titik `lapak` ×14), kedai bakpao kukus (`smoker`, uap klien); (0,1) **Cabang Rumah Uang** + **Papan Gilda** (`bank_cabang`, `papan_gilda`), `kota/pil`; (1,1) **Kedai Teh Kabut** (`kota/teh`, `kota/kursi_pengembara` ×2) |
| `penginapan_peziarah` | (0,0)+(1,0) **Kuil Kaki Gunung** (titik awal ziarah: aula Samantabhadra kecil, pembakar dupa, ruang daftar ziarah = jangkar `kota_tema/ziarah`); (0,1) **Penginapan Pondok Pinus** (`penginapan`, kamar bersama 16 kasur + 4 kamar); (1,1) **Rumah Makan Vegetarian** (`rumah_makan`) |
| `teras_hunian` | 4 lot rumah panggung teras (pool `kota_tema/gunung/rumah`: rumah kayu bertiang yang separuhnya menggantung di atas teras bawah, dapur, tempat tidur loteng, balkon), sumur bambu (pipa `bamboo_block` + air), `bengkel` (`kotabesar/tukang`), titik `jalur_tangga` |
| `aula_herba` | (0,0)+(1,0) **Aula Herba Gunung**: aula pengering (rak jemur herba bertingkat, titik `kerja:jemur` ×6), toko herba (jangkar `kota_tema/aula_herba`), ruang penilaian umur herba (timbangan, kaca pembesar = item frame); (0,1) **Tiang Tengah** kereta gantung: menara kayu-batu 9 × 34 × 9 dengan roda pemandu (entitas `roda_katrol` kecil); (1,1) **Rumah Pemetik**: tali panjat, keranjang, peta tebing di dinding (billboard) |
| `teras_kebun` | (0,0)+(1,0) **Teras Kebun Herba dan Teh Gunung**: 5 teras sempit (`farmland` + tanaman vanilla, `azalea` berbaris), saluran air bambu; (0,1) **Katrol Teras**: katrol vertikal (`KatrolEntity` keranjang, 38.T.9.3) dari teras kebun ke `biara_awan` di atas (naik sampai 30 blok); (1,1) gudang pupuk, rumah petani teras |
| `tebing_papan` | (0,0)+(0,1) **Jalan Papan Tebing**: jalan papan 1 lebar (`spruce_slab` di atas `spruce_fence` sebagai penyangga yang tertanam di dinding tebing buatan `stone`/`andesite` 30 tinggi), rantai pegangan (`chain` mendatar), tangga besi; 6 **ceruk meditasi** di tebing (titik `meditasi`), **tumbuhan tebing** = entitas `HerbaTebingEntity` (38.T.6.4); (1,0) **Gua Pertapa Tebing** kecil (ruang 7 × 5 × 7, titik `meditasi` ×2, `pandang`); (1,1) **Katrol Tebing** (`KatrolEntity` dari bordes ke ceruk tinggi) |
| `puncak_emas` | (0,0)+(1,0) **Aula Emas**: aula 24 × 18 × 20 berdinding dan beratap `gold_block`/`raw_gold_block` di genteng teratas + `yellow_terracotta`, patung Bodhisattva gajah putih (patung `quartz_block` + `calcite` 10 tinggi di atas "gajah" `white_concrete`), teras pandang tepi tebing berpagar (titik `pandang` ×4, `meditasi` ×8), jangkar `kota_tema/aula_emas`; (0,1) **Stasiun Atas Kereta Gantung** (jangkar `kota_tema/lift` `stasiun=atas`); (1,1) **Menara Lonceng Awan** (lonceng `bell` besar di bingkai kayu, jangkar `kota_tema/lonceng_awan`) |
| `biara_awan` | (0,0)+(1,0) **Biara Awan**: aula Dharma (bantal duduk ×24, `kerja:ikan_kayu` ×2), asrama biksu (kasur ×12), dapur biara, perpustakaan sutra (rak kitab), jangkar `kota_tema/biara`; (0,1) **Kolam Pelepasan** (kolam ikan dan kura-kura), taman batu; (1,1) **Rumah Tamu Biara** (penginapan biara, 3 NB) + stasiun teleport kecil (`kotabesar/cakram`) |

Catatan isi `kaki_gerbang` (1,1): pohon besar alun = `oak` raksasa buatan tangan dengan `azalea_leaves` dan
`flowering_azalea_leaves` (bukan warna khusus).

#### 38.T.6.3 Penduduk, peran, rutinitas dan animasi

Peran baru: `KEPALA_BIARA` (tier 4, RAHIB, jubah kasaya merah-emas + tongkat sembilan cincin), `BIKSU_GUNUNG` (2, RAHIB,
jubah abu + tasbih + topi caping biksu saat turun gunung), `PENJAGA_LIFT` (1, PEKERJA, sarung tangan tebal + kunci roda),
`KULI_GUNUNG` (1, PEKERJA, kain kepala + rompi + pikulan/rangka punggung), `PEMETIK_HERBA` (1, PEKERJA, tali panjat
melingkar + keranjang punggung + sabit kecil), `PEZIARAH` (0, TETUA/PEKERJA, ikat kepala kuning + tas dupa + tongkat
bambu). Penduduk data 180 + peziarah tamu 10-30 (naik saat Musim Ziarah).

| Jam | Kuli gunung | Peziarah | Biksu | Pemetik herba | Penjaga lift |
|---|---|---|---|---|---|
| 04-06 | sarapan, mengikat muatan (`ikat_tali`) | bangun di penginapan, dupa di Kuil Kaki | **lonceng subuh** di Menara Lonceng Awan, `tabuh_ikan_kayu` di aula | turun ke teras | memeriksa roda (`putar_katrol` lambat, `palu` ringan) |
| 06-11 | **naik** sepanjang `jalur_tangga` dengan muatan (`daki_pikul`, berhenti tiap 2 bordes `basuh_muka`-varian lap keringat), turun kosong (`jalan_santai`) | **mendaki**: sebagian `sujud_ziarah` (tiga langkah satu sujud) di 9 bordes terakhir, sebagian berjalan + berhenti mengatur napas | sarapan bubur, kerja (`sapu` halaman, `rak_susun` kitab) | **memetik di tebing**: berjalan di jalan papan, `petik_herba_tebing` di ceruk | kereta gantung jalan tiap 4 menit dunia (`putar_katrol` saat berangkat) |
| 11-13 | makan di Pos Kuli | makan vegetarian | makan siang hening (`makan` tanpa obrolan) | makan di ceruk | makan bergantian |
| 13-17 | angkut kedua | di Aula Emas: `sujud_dupa`, foto pemandangan (`pandang`: `tatap_langit` lebar) | ceramah di aula (penonton biksu `duduk_lantai`) | `jemur` herba di aula herba | jalan |
| 17-20 | `minum_teh`, `catur` | turun atau menginap di Rumah Tamu Biara | doa senja, `tabuh_ikan_kayu` | menilai herba di aula | kereta gantung terakhir 18:00 |
| 20-04 | tidur | tidur | meditasi malam (`napas_meditasi`) | tidur | tidur |

Cuaca: **kabut** (pagi hari, hujan, atau bioma tinggi: klien kabut putih di baris atas), hujan → jalan papan ditutup
(penjaga di ujung `berdiri_jaga`, rantai melintang = entitas `PalangEntity` kecil), kereta gantung tetap jalan pelan;
badai petir → kereta gantung berhenti, peziarah berlindung di aula.

#### 38.T.6.4 Permainan khas

- **Kereta Gantung** (`KeretaGantungEntity`, 38.T.9.3): kabin kayu 6 penumpang bergerak di garis lurus antar-stasiun
  (dua bentang lewat Tiang Tengah, kurva gantung klien), 40 s sekali jalan, berangkat tiap 4 menit dunia (bila ada
  penumpang) 05:00-18:00; ongkos 1 NB naik, gratis turun; `Sibuk` `tema.lift`. Penumpang duduk (seat), pintu kabin
  tertutup (collider), dari dalam terlihat lautan awan (klien: lapis awan quad di bawah puncak saat pagi). Kereta tidak
  pernah menjatuhkan penumpang; peristiwa **Lift Macet** (quest) berhenti di tengah bentang, pemain boleh keluar
  (terbang/turun dengan tali `TaliTurunEntity` yang dijatuhkan penjaga).
- **Katrol** (`KatrolEntity`): keranjang terbuka 2 orang/1 muatan naik-turun vertikal 30 blok dalam 12 s, dijalankan
  PENJAGA_LIFT memutar engkol (`putar_katrol`) di atas; gratis.
- **Jasa kuli**: titip barang (peti 27 slot dari inventori, bukan wadah berisi) ke Pos Kuli 2 NB → muncul di peti titipan
  `kota_tema/titipan` di Biara Awan atau Aula Emas setelah kuli selesai berjalan (±3 menit dunia; kuli yang membawanya
  terlihat mendaki); barang disimpan `KotaTemaData` (persisten), diambil oleh pemilik saja.
- **Petik herba tebing**: `HerbaTebingEntity` (tanaman berkilau di ceruk dan tepi jalan papan; 12 titik per kota, muncul
  ulang tiap hari dunia per pemain = data, tubuh hanya klien-tampak bagi pemain yang belum memetik): klik → herba berumur
  (tabel `harta` herba gunung, umur 60-300 tahun, batas M3 ≤ 300) + 5 % **Rumput Awan Seribu Tahun** (bahan langka, umur
  tetap 300). Jatuh dari tebing = jatuh biasa (pemain bertanggung jawab); Aula Herba menjual **Tali Panjat** (15 NB,
  sekali pakai: mencegah kerusakan jatuh satu kali saat memetik di tebing kota ini).
- **Aula Herba** (layar `tema_herba`): beli/jual herba gunung (jual ke aula 0.5 × nilai, 38.M.5), **penilaian umur** gratis,
  **peringkat Pemetik Tebing** 1-5 (poin dari petikan) → izin titik herba tambahan dan diskon 5-15 %.
- **Ziarah Seribu Anak Tangga** (jangkar `kota_tema/ziarah`): daftar di Kuil Kaki (gratis), daki dari gerbang kaki ke Aula
  Emas **tanpa terbang, kereta gantung, katrol, teleport** (diperiksa: bendera terbang/kendaraan/teleport selama ziarah
  membatalkan), nyalakan dupa di 9 bordes (jangkar `kota_tema/bordes` ×9, klik → asap klien), tiba di Aula Emas dalam satu
  hari dunia → **Restu Puncak** (7 hari dunia): situs meditasi ekstra "Aula Emas" ×1.4 saat bermeditasi di teras Aula
  Emas dan +10 % XP meditasi di mana saja (dalam CAP 38.0 butir 4), sekali per 7 hari; pertama kali: +2 pemahaman,
  advancement `tema/peziarah`.
- **Festival / fenomena: Fajar Cahaya Budha** (pagi 23000-1000, tidak hujan, peluang 30 % per hari): lautan awan di bawah
  puncak + **lingkaran cahaya pelangi** di awan (quad aditif klien, 38.T.9.4) dengan bayangan pemain di tengahnya bila
  pemain berdiri di titik `pandang` Aula Emas; pemain yang melihatnya (≥ 10 s) mendapat **Restu Cahaya** (pemahaman +1,
  maks sekali per 7 hari dunia), peziarah NPC `sujud_ziarah` serempak. Baris `tema.gunung.cahaya`.
- **Musim Ziarah** (`d % 30` di 0..4): peziarah ×3, kios pasar kaki +4, upacara Lonceng Awan siang hari (adegan pendek:
  108 pukulan dipadatkan 4 s, `Sibuk`).
- **Bahaya: Longsor** (saat hujan, peluang 25 % per jam dunia per kota): `BatuRuntuhEntity` (3-6 batu, 38.T.9.3)
  menggelinding dari tebing di atas `tebing_papan` dan tangga baris atas di jalur tertentu (spline dari titik), 4-8
  kerusakan bila mengenai, terpental dari pagar (tidak menghancurkan blok, tidak meninggalkan blok); NPC menutup jalur
  (`PalangEntity`), pengumuman. Pemain dengan Mode Kehancuran atau pukulan teknik bisa memecah batu (entitas mati,
  partikel).
- **Bahaya: Kera Gunung** (`KeraGunungEntity`, 4-8 ekor di baris atas): kera mendekati pemain yang **memegang makanan**,
  merebutnya (makanan di tangan saja, 1 buah) lalu kabur ke tebing; bila dipukul atau diberi umpan buah (lempar ke tanah)
  menjatuhkan barang curian. Tidak pernah mengambil barang lain. Membunuh kera di wilayah biara = Nama Baik −3 (biksu
  marah).

#### 38.T.6.5 Tokoh dan rantai quest "Tali yang Terakhir"

| Tokoh | Peran | Watak, suara | Muncul |
|---|---|---|---|
| **Kepala Biara Jue Yuan** | KEPALA_BIARA | tenang, humor tersembunyi, kultivator Penyatuan Dao yang tak pernah bertarung; `elder` | biara, bab 1, 6 |
| **Paman Tie Suo** | PENJAGA_LIFT | keras kepala, bangga pada kereta gantungnya, berutang pada pedagang; `m1` | stasiun, bab 2-5 |
| **Shan Ling** | PEMETIK_HERBA | gadis pemberani, hafal setiap ceruk tebing, berteman dengan kera; `f1` | tebing, bab 3-4 |
| **Lao Dan** | KULI_GUNUNG | kuli tua, kaki besi, bijak sederhana; `m2` | tangga, bab 2 |
| **Pei Wenzhi** | PEZIARAH (tuan muda sastrawan) | sombong lalu tobat, mendaki untuk mendiang ibunya; `m3` | ziarah, quest samping |
| **Biksuni Jing Yue** | BIKSU_GUNUNG (penjaga Aula Emas) | lembut, tegas soal kesucian puncak; `f3` | aula emas, bab 6 |
| **Gou Tie "Cakar Besi"** | pemimpin pemburu herba liar (`KultivatorNpc`, tier 3) | rakus, ingin monopoli herba tebing; `m3` | bab 3-5 |
| **Kera Putih Tua** | binatang roh penjaga (entitas `KeraGunungEntity` varian putih besar, tier 4, tidak bermusuhan kecuali diserang) | kuno, bijak, tidak bicara (gestur); — | bab 4-5 |

Rantai `tema_gunung_tali` (6 tahap):

| # | Pemicu | Isi | Cabang | Hadiah |
|---|---|---|---|---|
| 1 | naik kereta gantung pertama kali | **Lift Macet** di tengah bentang (adegan: kabin bergoyang); Tie Suo menurunkan tali; di stasiun: tali besi terpotong rapi, bukan aus | — | situs, Nama Baik +2 |
| 2 | Tie Suo + Lao Dan | jaga Tiang Tengah semalam (pemain di titik `jaga`); tiga sosok datang dengan gergaji roh; kalahkan atau usir | tangkap satu (interogasi: Gou Tie) / biarkan kabur (jejak kaki ke tebing) | 40 × (band+1) NB |
| 3 | Shan Ling | Gou Tie ingin kereta gantung rusak agar kuli harus memakai jasanya dan pemetik tebing takut naik; ia memasang **jerat roh** di 6 ceruk; lepaskan jerat (6 entitas `JeratEntity` di jalan papan) | — | herba tebing ×3 |
| 4 | jerat dilepas | Kera Putih Tua muncul (adegan 4 s: dari kabut), membawa pemain ke ceruk tersembunyi lewat Katrol Tebing: sarang kera dengan **Tali Sutra Kera** (serat roh yang dipintal kera dari akar tebing) | ambil sopan (beri 10 buah) / ambil paksa (Kera Putih bermusuhan; Shan Ling kecewa, bab 6 berubah) | Tali Sutra Kera |
| 5 | Tie Suo | Gou Tie dan 4 pemburu menyerbu stasiun saat tali baru dipasang (pertahanan kecil; Kera Putih membantu bila bab 4 sopan) | (a) kalahkan, serahkan ke biara (Gou Tie menjadi kuli tobat); (b) kalahkan dan usir; (c) terima suapnya (monopoli herba: harga herba +20 %, Shan Ling pergi dari kota) | (a)/(b) Nama Baik +5 |
| 6 | Kepala Biara | upacara pemberkatan tali (adegan: Lonceng Awan berbunyi, kabin pertama naik membawa pemain, lautan awan, Cahaya Budha muncul pasti) | — | gelar "Penjaga Tali Awan", kereta gantung gratis selamanya, Restu Puncak tanpa ziarah sekali, Nama Baik +10 |

Quest samping: `tema_ziarah_ibu` (temani Pei Wenzhi mendaki; ia meminta pemain membawakan dupa pesanan ibunya dari Kota
Air bila diketahui), `tema_kuli_sehari` (pikul 3 muatan ke atas tanpa terbang: hadiah 30 NB dan Tongkat Kuli kosmetik),
`tema_kera_nakal` (ambil kembali 5 barang curian kera untuk peziarah), `tema_rumput_awan` (temukan Rumput Awan Seribu
Tahun untuk ramuan Kepala Biara).

### 38.T.7 Kota Iblis (`kota_tema/iblis`)

#### 38.T.7.1 Lore dan Sumber

- **Lore** (`tempat_tema_lore.iblis.*`): kota bebas para kultivator jalan iblis, didirikan di atas medan perang tempat
  sekte-sekte lurus pernah mengalahkan Pemimpin Iblis Darah tiga ribu tahun lalu. Tidak ada hukum selain **Perjanjian
  Pasar**: di dalam pasar dan kedai, darah tidak boleh tumpah; di luar itu, yang kuat benar. Kota dikuasai **Penguasa
  Kota** yang memungut upeti dari setiap pintu, menyewakan arena dan menyimpan **Menara Kitab Terlarang**. Sekte-sekte
  lurus memasang hadiah atas kepala penghuninya, dan setiap beberapa purnama pasukan pemburu mengepung temboknya.
- **Sumber** (`kultivator.kota_tema.iblis.inspired`): "Terinspirasi dari Wilayah Sudut Hitam (Black-Corner Region) dalam
  Battle Through the Heavens karya Tian Can Tu Dou, wilayah tanpa pemimpin tempat faksi-faksi saling bunuh demi untung,
  dengan Kota Tanda Hitam (Black Mark City) yang memungut biaya masuk tinggi (terjemahan Wuxiaworld, sekitar bab 383-387);
  dan dari kiasan umum novel xianxia tentang kota jalan iblis, arena darah dan perburuan hadiah oleh sekte lurus. Tata
  kota, tokoh, harga dan angkanya rancangan game."

#### 38.T.7.2 Tata letak (grid sebelum diputar)

```
             x = -1                 x = 0                  x = +1
 z = -1   [menara_terlarang]    [istana_darah]        [penjara_tawanan]
 z =  0   [pasar_gelap] (ZP)    [arena_darah]         [sarang_kontrak]
 z = +1   [kumuh_iblis]         [gerbang_tengkorak]   [kedai_racun] (ZP)
```

ZP = **Zona Perjanjian** (titik kotak `zona_perjanjian`, dijaga PENEGAK_IBLIS; satu-satunya tanah aman, 38.T.7.8).
Tembok: `blackstone` + `polished_basalt` 16 tinggi berduri (paku `iron_bars` + `end_rod` di merlon; tanpa `pointed_dripstone` karena bisa jatuh),
menara sudut beratap runcing, panji hitam-merah (kain klien). **Gerbang Tengkorak**: rahang tengkorak raksasa dari
`bone_block` + `basalt` (22 tinggi), pintu ganda besi = entitas `GerbangKotaEntity`-pola (38TK `GerbangTengkorakEntity`,
model rahang membuka).

| Distrik | Lot (lx,lz) dan isi |
|---|---|
| `gerbang_tengkorak` | (0,1) Gerbang Tengkorak + rumah bea (meja upeti, timbangan, rak senjata sitaan sementara), jangkar `kota_tema/gerbang_iblis` (upeti, gerbang ranah); (0,0) **Lapangan Peringatan**: tiang-tiang batu berantai dengan senjata patah dan topeng retak (tanpa tubuh), papan "nama-nama yang menantang Penguasa"; (1,0) pos penegak (kasur ×8, rak rantai); (1,1) kandang serigala bayangan (binatang tunggangan penegak, entitas templat `wolf` vanilla bertali, tidak dijinakkan) |
| `pasar_gelap` (ZP) | (0,0)+(1,0) **Pasar Seribu Bayangan**: lorong sempit beratap kain hitam, 20 kios bertirai (titik `lapak` ×20): racun, pil darah, tulang binatang iblis, senjata curian, peta sarang, **kios topeng**; lampu `soul_lantern` dan `red_candle`; jangkar `kota_tema/pasar_gelap` ×2, `zona_perjanjian`; (0,1) **Balai Lelang Gelap** (aula kecil bertribun, panggung bertirai merah, jangkar `kota_tema/lelang_gelap` → `BalaiLelang.mulai` profil `GELAP`); (1,1) **Rumah Tukar Makelar** (meja tukar, brankas, ruang belakang Bai She) |
| `arena_darah` | (0,0)+(1,0)+(0,1)+(1,1) **Arena Darah**: lubang arena 31 × 31 turun 5 blok (lantai `polished_blackstone` + noda `red_concrete_powder`), dinding dengan 4 gerbang kurungan (`arena_pintu` → `PintuArenaEntity`), tribun 6 undakan (titik `kursi` ×140, penonton impostor), **Bilik Penguasa** di sisi utara (takhta, tirai), ruang gladiator di bawah tribun (kasur ×10, rak senjata, titik `latihan` ×6), kandang binatang iblis (`iron_bars`), meja taruhan (jangkar `kota_tema/arena`, `kota_tema/taruhan`); obor tinggi = `soul_lantern` di tiang `chain` |
| `istana_darah` | (0,0)+(1,0) **Istana Darah**: aula takhta 30 × 18 × 26 (tiang `red_nether_bricks` 2 × 2, kolam darah dangkal di depan takhta (catatan di bawah tabel), ruang perjamuan, ruang semedi darah (titik `meditasi` ×4); (0,1) barak penegak (kasur ×12); (1,1) **Perbendaharaan Upeti** (peti, rak, jangkar `kota_tema/perbendaharaan`, target quest) |
| `menara_terlarang` | (0,0)+(0,1) **Menara Kitab Terlarang**: menara sembilan lantai 15 × 52 × 15 (dinding `blackstone_bricks`, jendela sempit, tangga dalam melingkar), tiap lantai rak kitab berantai (`chiseled_bookshelf` + `chain`), lantai 9 tertutup segel (entitas `SegelPintuEntity`, terbuka saat Pesta Darah atau rute quest), jangkar `kota_tema/kitab_terlarang`; (1,0) rumah Si Buta Mo Wen; (1,1) **Kolam Semedi Darah** (kolam merah, titik `meditasi` ×6, situs ekstra iblis) |
| `penjara_tawanan` | (0,0)+(1,0) **Kandang Tawanan**: halaman berpagar `iron_bars`, 10 sel kecil (titik `sel_tawanan` ×10, pintu = `SangkarEntity` pola 38GU, milik 38TK `SelTawananEntity`), menara jaga; (0,1) ruang penjaga; (1,1) **Terowongan Tua** (lorong bawah tanah dari penjara ke luar tembok, ditutup reruntuhan = entitas `PintuTebingEntity`-pola; dipakai rute penebusan quest) |
| `sarang_kontrak` | (0,0)+(1,0) **Sarang Kontrak** (gilda pembunuh bayaran): aula dengan dinding kontrak (billboard poster), loket bertirai, ruang rahasia (jangkar `kota_tema/kontrak`); (0,1)+(1,1) rumah-rumah kultivator iblis (pool `kota_tema/iblis/rumah`: rumah batu hitam bergembok, ruang semedi, peti) |
| `kumuh_iblis` | 4 lot gubuk dan lorong (penghuni: pengikut iblis rendahan, pedagang kecil yang terlilit utang), dapur umum, sumur tercemar (air + `crimson_roots`), titik `main` anak-anak liar, `bengkel` (`kotabesar/tukang`, tukang bayaran) |
| `kedai_racun` (ZP) | (0,0)+(1,0) **Kedai Cawan Racun**: kedai dua lantai (meja ×12, bar panjang, kamar sewa ×8: `kotabesar/layanan` `penginapan` + `rumah_makan`, harga ×1.5), panggung musik; (0,1) **Kedai Teh Asap** (`kota/teh` untuk kabar; Kota Iblis tidak punya `kota/situs`, tetapi jangkar `kota/teh` dilayani K tanpa kota: verifikasi bahwa `KedaiTeh` tidak menuntut `Towns`; bila menuntut, layanan kabar dipindah ke `kota_tema/kabar_gelap` milik 38TK); (1,1) **Stasiun Formasi Hitam** (`kotabesar/cakram`; tujuan dari sini hanya situs yang diketahui; ke kota ini hanya bila pemain sudah pernah masuk) |

Catatan kolam darah istana: **tidak ada fluida baru dan tidak ada lampu menyala oleh redstone**; kolam = lantai
`red_concrete` dangkal di bawah lapisan `red_stained_glass` dengan `shroomlight` tersembunyi di bawah tepi (cahaya ≥ 8)
dan riak klien (quad aditif).

#### 38.T.7.3 Penduduk, peran, rutinitas dan animasi

Peran baru: `PENGUASA_IBLIS` (tier 5, —, jubah naga hitam-merah + mahkota tanduk), `PENEGAK_IBLIS` (3-4, PENJAGA,
topeng besi + rantai di bahu + golok), `JAGAL_ARENA` (3, PEJABAT, celemek kulit berdarah + kapak besar), `GLADIATOR_IBLIS`
(2-5, KULTIVATOR, zirah tak lengkap + bekas luka tekstur), `MAKELAR_GELAP` (2, PEDAGANG, cadar + perhiasan ular),
`PENJAGA_KITAB_TERLARANG` (4, CENDEKIA, penutup mata kain + jubah bertulisan), `PEMBUNUH_BAYARAN` (3, KULTIVATOR, tudung +
pisau ganda), `WARGA_IBLIS` (1, PEKERJA/PEDAGANG, jubah gelap), `PEMBURU_LURUS` (3-4, KULTIVATOR, jubah lambang sekte
Lurus + pedang; hanya di pos pemburu dan penyerbuan). `TAWANAN`, `ANAK`, `PENGEMIS` dipakai ulang. Penduduk data 200
(+ 4-10 tawanan).

**Kota malam:** jadwal kelompok digeser 12 jam untuk peran iblis (jam 18-06 = "siang" mereka), siang hari jalan sepi,
jendela tertutup, hanya penegak berpatroli dan pedagang kecil di Pasar Seribu Bayangan.

| Jam | Penegak | Gladiator / jagal | Makelar / pedagang gelap | Warga iblis | Tawanan |
|---|---|---|---|---|---|
| 06-12 | patroli siang berpasangan (`baris_jaga` rantai) | tidur di ruang gladiator | tidur | tidur | `berlutut_rantai`, `duduk_lantai` lesu; penjaga memberi makan (`lempar_pakan`) 08:00 |
| 12-18 | jaga gerbang (`berdiri_jaga`, `ancam` bila pemain lemah lewat) | latihan (`latihan_pedang_*`, `asah_pisau`) | buka pasar 15:00 (`hitung_koin_gelap`) | bangun, `semedi_darah` di rumah | diam |
| 18-24 | patroli Zona Perjanjian; menonton arena | **pertandingan** 19-23 (`sorak_arena` penonton, gladiator masuk lewat gerbang kurungan) | Pasar Seribu Bayangan ramai: `seru_dagang` berbisik (`bisik`), `tawar` | ke arena atau kedai, `minum_teh` (cawan racun), `berdebat` | `takut_sembunyi` saat sorak |
| 00-06 | patroli lentera jiwa | `makan` pesta di kedai | lelang gelap (hari lelang), menghitung | pulang 04:00 | tidur gelisah |

#### 38.T.7.4 Gerbang ranah dan aturan masuk

- **Upeti Gerbang** `10 × (band + 1)` NB per masuk per hari dunia (gratis bila Gentar ≥ 60 atau gelar "Tamu Darah");
  **gerbang ranah**: pita < 2 (di bawah Fondasi) ditolak ("Mangsa sekecil ini tidak berharga di sini."), pita 2 diizinkan
  dengan kartu peringatan merah (`TitleCard.banner`), ranah disarankan Inti Emas; buronan ibu kota **diterima** (kota ini
  tidak peduli).
- Melompati tembok / terbang masuk tanpa upeti dan terlihat penegak = **Penyusup**: penegak bermusuhan 1 hari dunia di
  kota ini (bukan kejahatan ibu kota).
- Senjata tidak disita. Murid pengikut boleh masuk (diveto hanya di arena dan sel).

#### 38.T.7.5 Arena Darah

- **Tangga Darah** (peringkat 1-9 per pemain, `KotaTemaData`): pertarungan pemain vs GLADIATOR_IBLIS bernama (nama dari
  `TempatNames` + julukan, mis. "Han si Taring Patah"), tier lawan = `clamp(band − 1 + peringkat / 3, 1, 8)`, AI duelist
  (35.7 pola `Rivals`), `markRealmFoe`. Aturan "darah" (`DuelChallenges.challenge` dengan aturan arena, verifikasi
  `DuelRuleSet`): NPC kalah = **roboh** (mati di arena, tubuh diseret keluar oleh jagal = klip), **pemain tidak pernah
  mati**: pada HP 5 % pertandingan dihentikan, pemain "diseret pengawas" ke ruang gladiator dengan efek Lemah I 3 menit dan
  kehilangan biaya. Biaya `8 × (band + 1)` NB; hadiah menang `2.2 × biaya` (dari biaya + Kas Kota Iblis; tidak mencetak:
  bila Kas kurang, hadiah = 2 × biaya) + Gentar +5 + naik peringkat; peringkat 9 → gelar "Juara Tangga Darah", senjata
  arena unik `gada` roh tinggi "Palu Darah Senja" (sekali). Maks 5 pertandingan per hari dunia. Murid diveto; `Sibuk`
  `tema.arena`.
- **Lawan Binatang**: binatang elit iblis dari kandang (`elite/*` verifikasi API spawn), tier menurut peringkat; hadiah
  inti binatang.
- **Taruhan** (layar `tema_taruhan`): pertandingan NPC vs NPC tiap 20 menit dunia pada 19-23; peluang dari tier dan
  watak, pembayaran dengan **EV 0.90** (`odds = 0.9 / p`), maks taruhan `20 × (band + 1)` NB, 6 taruhan per malam;
  pertandingan tampil hidup bila pemain di tribun, selain itu data.
- **Tebusan Darah**: pertarungan khusus untuk membebaskan satu tawanan tanpa membayar (38.T.7.6); lawan tier +1.

#### 38.T.7.6 Pasar Seribu Bayangan, Lelang Gelap dan Kandang Tawanan

- **Pasar Seribu Bayangan** (layar `tema_pasar_gelap`; harga 38.T.10.2): racun (bahan pil dan pelapis senjata), **pil
  darah** (pulih cepat dengan efek samping), tulang dan inti binatang iblis, **peta sarang** (gulungan penunjuk
  `gua/sarang_iblis` atau `reruntuhan_gurun`), **topeng** (kosmetik; Topeng Tanpa Wajah: selama dipakai di Kota Iblis,
  penantang Jam Berburu ½ lebih jarang), senjata curian (senjata kota 38EK mutu acak dengan harga 0.85 × tetapi komponen
  `kultivator:asal_gelap`: toko kota lurus menolak membelinya). Markup dasar 1.3 (Gentar ≥ 30: 1.2; Infami ≥ 30: 1.15;
  "Tamu Darah": 1.1). Beli-kembali ≤ 0.5 (38.M.5).
- **Lelang Gelap** (`d % 9 == 4`, 18000-20000): `BalaiLelang.mulai(level, juru, new ProfilLelang(5, 2, 0.15,
  List.of("saingan", "iblis_tokoh"), "gelap"))` (profil dibuat 38TK; mesin 38EK, tidak disunting); tabel lot `gelap`
  (data 38TK): racun langka, teknik terlarang, pusaka iblis mutu 2-3, kunci Alam Rahasia, **Kontrak Kepala** (lot yang
  isinya informasi letak buronan bernilai tinggi untuk perburuan lurus). Komisi 15 % ke Kas Kota Iblis.
- **Kandang Tawanan** (layar `tema_tawanan`): 4-10 TAWANAN (orang fana dari kampung yang diculik; data: asal kampung
  nyata terdekat yang diketahui sistem, nama dari `TempatNames`). Satu-satunya interaksi pemain: **Tebus** (`25 × (1 +
  kekuatanKota / 2)` NB per orang, maks 3 per hari dunia) atau **Tebusan Darah** (arena). Tawanan yang ditebus mengikuti
  pemain (tubuh NPC `TAWANAN` pengikut pola kawal 38.S.4 `tawanan_pulang`) dan harus dikawal keluar gerbang lalu ke
  kampung asalnya (atau kampung/kota mana pun yang aman); tiba → Nama Baik +3 di tempat tujuan, kesetiaan desa +5 bila
  terikrar, Infami −5, `DiplomasiService.adjust(owner, sekteLurusTerdekat, +2, "tebus")`. Tawanan **tidak pernah** menjadi
  milik, pelayan, murid paksa atau barang pemain; bila pemain menolak mengawal, tawanan berjalan sendiri ke Pos Pemburu.
  Tawanan baru datang 1-2 per 3 hari dunia (data "penculikan"; kampung terikrar pemain di dekat Kota Iblis mendapat kabar
  "warga hilang" = kait quest).

#### 38.T.7.7 Menara Kitab Terlarang dan perburuan

- **Menara Kitab Terlarang** (layar `tema_kitab_terlarang`, Si Buta Mo Wen; lantai 1-8 terbuka sesuai Gentar: lantai n
  butuh Gentar ≥ 10 × (n − 1)):
  - **Gulungan Terlarang** (item habis pakai milik 38TK, 38.T.10.2): *Bakar Darah* (korbankan 30 % HP → Kekuatan II 30 s,
    jeda 5 menit), *Langkah Hantu* (Tak Terlihat 12 s + kecepatan, biaya 20 % Qi), *Rantai Jiwa* (tarik satu musuh ≤ 12 blok
    ke depan, `markRealmFoe` musuh kebal), *Topeng Iblis Hati* (selama 60 s kerusakan +15 %, setelahnya tekanan iblis hati
    +5 bila API publik ada, verifikasi). Setiap pembelian Infami +2; setiap pemakaian di luar Kota Iblis disaksikan NPC
    sekte Lurus = Infami +1.
  - **Giok teknik terlarang**: giok teknik Dao bernuansa darah/jiwa/racun lewat API publik pemberian giok
    (`JadeSlips`/`TechniqueBooks`, verifikasi; bila tidak ada API publik, layanan ini hanya menjual gulungan); harga `3 ×`
    nilai giok biasa, pita ≥ 3, Infami +8 saat dipelajari (komponen `kultivator:noda_terlarang` pada giok).
  - **Lantai 9** (segel): terbuka hanya saat Pesta Darah atau setelah rute quest "iblis": satu **Kitab Darah Senja**
    (teknik unik pemimpin iblis kuno; stok 1 per dunia per kota; Infami +20).
- **Sarang Kontrak** (layar `tema_kontrak`): kontrak pembunuhan **hanya** terhadap: binatang iblis dan elit di alam liar,
  kelompok kultivator iblis saingan (`RogueCultivators` `VARIANT_DEMONIC`, `ROLE_QUEST`, di kemah nyata), band begal, dan
  regu **Pemburu Lurus** yang sedang memburu penghuni kota (38.T.7.9). **Tidak pernah** warga kota, kampung, murid,
  villager atau pemain lain. Imbalan `MisiGilda` pita × 1.3; Infami +5 per kontrak (kecuali binatang: +1); Gentar +3.

#### 38.T.7.8 Aturan bahaya

1. **Tanah aman hanya Zona Perjanjian** (`pasar_gelap`, `kedai_racun`; `Rencana.zonaAman` → `TempatZones.safe`): tidak ada
   pertarungan; pelanggar (pemain menyerang NPC, atau NPC lain) diburu semua penegak (`markRealmFoe`) dan dilarang masuk
   Zona Perjanjian 3 hari. Di luar ZP, kota **bukan** tanah aman: Penyergapan, Saingan dan peristiwa musuh 38 boleh terjadi
   (tetap lewat tekanan harian M2 dan `Sibuk`).
2. **Jam Berburu** (13000-23000, luar ZP): sekali per malam per pemain, 1-3 kultivator iblis (`RogueCultivators`
   `VARIANT_DEMONIC`, tier `band ± 1`, `markRealmFoe`) menghadang: dialog "Upeti jalan, atau darahmu?" → **Bayar**
   `5 × (band + 1)` NB atau **Lawan** (menang: Gentar +10, rampasan biasa). Gentar ≥ 30 atau Topeng Tanpa Wajah (½):
   lebih jarang; Gentar ≥ 60: tidak ada.
3. **Gentar** (0-100, per pemain per Kota Iblis): + menang arena, + menang Jam Berburu, + selesai kontrak, + bab quest;
   −10 menolak tagihan darah; turun 1 per 3 hari dunia. Ambang: 30 (penantang berkurang, diskon pasar), 60 (audiensi
   Penguasa, upeti gratis), 80 (gelar "Yang Ditakuti di <kota>", penegak `hormat` saat pemain lewat).
4. **Infami** (0-100, per pemain, global, `KotaTemaData.infami`; tampil di hub kartu Kota Bertema): sumber 38.T.7.6-7.7;
   turun: tebus tawanan −5, perburuan lurus −3, donasi 50 NB di Kuil Langit ibu kota −1 (maks −5 per minggu), bab quest.
   Akibat: ≥ 20 gerbang kompleks Lurus memperingatkan; ≥ 40 **Pemburu Lurus** dapat mencegat pemain (38.T.7.9); ≥ 60
   kompleks sekte Lurus menolak masuk dan Kuil Langit ibu kota menolak Restu Kuil; Nama Baik kota lain **tidak** diubah
   langsung (dua sistem terpisah, tidak saling menghukum dua kali).
5. **Kematian** di Kota Iblis mengikuti aturan vanilla/mod yang ada; tidak ada penyitaan tambahan.
6. **Perbaikan** (38.R) gaya `iblis`: penagih = PENEGAK_IBLIS, **Harga Darah** = tagihan × 2 (faktor tempat 2.0), tolak →
   penegak bermusuhan 1 hari + Gentar −10 (bukan Nama Baik); pemain jauh lebih kuat (38.R.5) → penegak `berlutut`, tanpa
   tagihan, perbaikan lambat oleh tukang bayaran.
7. **Mode Kehancuran** tetap tidak bisa merusak kota (zona `TempatZones`), seperti tempat lain.

#### 38.T.7.9 Perburuan hadiah oleh sekte lurus

- **Papan Buronan Iblis** (billboard di Gilda Kultivator ibu kota, Papan Gilda semua kota tema, gerbang kompleks Lurus,
  `kota_tema/pos_pemburu`): 3-6 **buronan iblis bernama** per Kota Iblis yang diketahui (tokoh data: nama, julukan, ranah
  `tier`, kejahatan: "membakar Dusun Batu Hijau", "menculik 12 warga"), diterbitkan atas nama sekte Lurus dari
  `DiplomasiService` (sekte Lurus terdekat; bila tidak ada, Gilda Kultivator).
- Buronan **keluar** dari Kota Iblis pada jadwal data (berburu, berdagang) dan bisa ditemui: di jalan (posisi data,
  tubuh bila pemain ≤ 64), di `gua/sarang_iblis` terdekat, atau di luar ZP di dalam kota (membunuhnya di dalam kota =
  penegak bermusuhan 1 hari, Gentar −10). Tubuh `KultivatorNpc` peran `PEMBUNUH_BAYARAN`/`WARGA_IBLIS` dengan nama,
  tier ranah, `markRealmFoe`, 2-3 pengawal.
- Imbalan: `MisiGilda.imbalan(pangkat B..S menurut tier)` + `DiplomasiService.adjust(owner, sekte, +3, "buronan")` +
  Infami −3 + 20 % peluang barang (pusaka buronan). Dihitung juga sebagai misi gilda (poin lencana, 38.M.3) lewat jenis
  misi `buronan_iblis` yang didaftarkan 38TK (`EkonomiApi.daftarJenisMisi`, 38.T.14.3).
- **Pemburu Lurus memburu pemain** berinfami ≥ 40: maks sekali per 5 hari dunia, di luar tempat aman, lewat tekanan harian
  M2 dan `Sibuk`: regu 2-4 PEMBURU_LURUS (tier pemain ± 0..1, `markRealmFoe`) dipimpin Han Qingshuang bila dia masih
  hidup dalam cerita. Dialog dulu: **Menyerah dan menebus** (`50 × (band + 1)` NB "denda tobat", Infami −20) /
  **Lawan** (menang: Infami +5, `DiplomasiService.adjust(−3)`) / **Lari**.
- **Pos Pemburu** (satelit): Han Qingshuang dan regunya; menerima laporan, membeli Kontrak Kepala, tempat tawanan yang
  ditebus bisa diantar (setara kampung tujuan).

#### 38.T.7.10 Acara Kota Iblis

| Acara | Jadwal | Isi |
|---|---|---|
| Malam Pertandingan | tiap malam 19000-23000 | arena, taruhan, penonton impostor ≤ 140 |
| Lelang Gelap | `d % 9 == 4`, 18000-20000 | 38.T.7.6 |
| **Pesta Darah** | selama peristiwa langit Gerhana Darah (`DuniaApi.sky()`, dibaca) | lentera kota merah, Turnamen Darah (8 gladiator + pemain, gugur, hadiah dari pendaftaran ×4), lantai 9 menara terbuka, Jam Berburu ganda, PENGUASA_IBLIS hadir di Bilik Penguasa |
| **Penyerbuan Lurus** | tiap 20 ± 5 hari dunia (hash), atau bab 5 quest | koalisi sekte Lurus terdekat (8-14 PEMBURU_LURUS hidup bila pemain ≤ 128 blok, selain itu data) mengepung Gerbang Tengkorak dalam 3 gelombang; pemain memilih pihak lewat dialog (Han Qingshuang di luar, atau Tu Gang di dalam): **bantu Lurus** (Infami −10, standing +5, tawanan dibebaskan di akhir bila menang) atau **bela kota** (Infami +10, standing −5, Gentar +10). Tidak ada blok berubah; gerbang = entitas; hasil data: Lurus menang → kandang kosong 5 hari dan pasar tutup 2 hari; kota menang → upeti gerbang ×1.5 selama 3 hari. `Sibuk` `tempat.pertahanan` |

#### 38.T.7.11 Tokoh dan rantai quest "Rantai yang Putus"

| Tokoh | Peran | Watak, suara | Muncul |
|---|---|---|---|
| **Xue Yao, Penguasa Darah Senja** | PENGUASA_IBLIS (ranah: Jiwa Baru Lahir di R3, Transformasi Dewa di R4) | dingin, sopan mematikan, menganggap kota ini "satu-satunya tempat jujur di dunia"; `boss` | istana, bab 6 |
| **Tu Gang "Si Jagal"** | JAGAL_ARENA | kasar, adil menurut aturannya sendiri, menghormati yang kuat; `m1` | arena, bab 2, 5 |
| **Nyonya Bai She** | MAKELAR_GELAP | ramah seperti ular, pemilik jaringan Minyak Bayangan; `f3` | pasar gelap, bab 3 |
| **Si Buta Mo Wen** | PENJAGA_KITAB_TERLARANG | tua, buta, "melihat" lewat kitab; netral sejati; `elder` | menara, bab 4 cabang |
| **Ye Wuhen** | WARGA_IBLIS (pembelot) | kultivator iblis muda yang ingin keluar; dulu diculik dari kampung saat kecil; `m3` | bab 1, 4-7 |
| **A-Fu** | TAWANAN (anak) | anak kampung pemberani yang terus mencoba kabur; `f1` | kandang, bab 2 |
| **Han Qingshuang** | PEMBURU_LURUS (murid inti sekte Lurus) | lurus, keras, tidak percaya penghuni kota iblis; `f2` | pos pemburu, bab 4-6 |
| **Hong Pao** | PEMBUNUH_BAYARAN (kurir Bai She) | "orang berjubah merah" dari quest pelabuhan dan kota air; `m3` | bab 3 |

Rantai `tema_iblis_rantai` (7 tahap, tiga rute: **Lurus**, **Penebusan**, **Darah**):

| # | Pemicu | Isi | Cabang | Hadiah |
|---|---|---|---|---|
| 1 | bayar upeti pertama | Ye Wuhen menyapa di Lapangan Peringatan, memperingatkan aturan kota (ZP, Jam Berburu), meminta pemain "melihat kandang" | — | situs, Gentar +2 |
| 2 | Kandang Tawanan | A-Fu (dari kampung yang diketahui pemain, atau kampung terdekat) memohon; Tu Gang menawarkan Tebusan Darah | tebus (NB) / Tebusan Darah (arena) / abaikan (A-Fu mencoba kabur sendiri dan tertangkap: rute Penebusan sulit) | A-Fu dikawal pulang: Nama Baik +3, Infami −5 |
| 3 | Bai She | Hong Pao mengirim Minyak Bayangan ke pelabuhan dan kota air (bila pemain sudah menyelesaikan bab 2 quest pelabuhan atau bab 3 quest kota air, dialog mengenali); temukan gudang minyak di `sarang_kontrak` | **sabotase** gudang (entitas tong minyak dihancurkan; Infami −5, Bai She memusuhi: pasar ×1.2) / **beli bagian** (Infami +10, 200 × (band+1) NB keuntungan bertahap) | — |
| 4 | Han Qingshuang di Pos Pemburu (atau Ye Wuhen) | Han meminta **Peta Gerbang Rahasia** (didapat dari Mo Wen di lantai 4 menara: teka-teki 3 kitab) | serahkan ke Han (rute **Lurus**) / serahkan ke Ye Wuhen (rute **Penebusan**: dia akan membawa tawanan lewat Terowongan Tua) / laporkan ke Xue Yao (rute **Darah**) | — |
| 5 | Penyerbuan Lurus khusus | **Lurus:** buka Gerbang Tengkorak dari dalam (entitas tuas di rumah bea, dijaga 6 penegak) · **Penebusan:** kawal 6-10 tawanan lewat Terowongan Tua saat penyerbuan (pintu tebing-pola dibuka, penegak mengejar) · **Darah:** bela gerbang, kalahkan Han Qingshuang dalam duel (tidak mati: ia mundur terluka) | — | Lurus/Penebusan: Infami −15; Darah: Infami +15, Gentar +20 |
| 6 | Istana Darah | **Lurus/Penebusan:** hadapi Xue Yao (bos 3 fase: pedang darah, kolam darah = gelombang lingkar klien + kerusakan area, panggil 2 gladiator; ranah band + 2, `markRealmFoe`) · **Darah:** jamuan Xue Yao, ujian kesetiaan: menolak membunuh Ye Wuhen membuka cabang duel Ye Wuhen yang tidak mematikan | — | pusaka Xue Yao (kipas besi roh tinggi unik "Kipas Senja"), atau Kitab Darah Senja (Darah) |
| 7 | sesudahnya | **Lurus:** Tu Gang menjadi penguasa, kandang ditutup **permanen**, pasar ×1.2, Pos Pemburu menjadi markas tetap; gelar "Pemutus Rantai"; Infami −30; standing Lurus +10 · **Penebusan:** Ye Wuhen menjadi penguasa, ZP meluas ke seluruh kota pada siang hari, kandang menjadi **Rumah Tebusan** (tawanan dibebaskan setengah harga dan dikawal NPC), Jam Berburu berhenti bagi pemain; gelar "Pembawa Fajar" · **Darah:** gelar "Tamu Darah" (upeti gratis, pasar ×1.1, lantai 9 terbuka bagi pemain), Infami +25, kompleks Lurus menutup gerbang sampai Infami < 40 | — | gelar + Gentar/Nama Baik seperti disebut |

Rantai tidak pernah buntu: tiap tahap bertenggat 6 hari dunia; tanpa pemain, Xue Yao tetap berkuasa dan rumor bercerita
tentang penyerbuan yang gagal. Quest samping: `tema_iblis_topeng` (Bai She: ambil kembali topeng curian dari regu
pemburu), `tema_iblis_gladiator_tua` (gladiator tua "Lao Kuang" ingin mati terhormat atau pensiun: cabang), `tema_iblis_kitab`
(Mo Wen meminta tiga kitab kuno dari Perpustakaan Agung ibu kota, ditukar akses lantai), `tema_iblis_anak_liar` (anak-anak
kumuh mencuri roti dari dapur umum; bantu mereka atau laporkan ke penegak).

### 38.T.8 Klip animasi, perlengkapan dan prop baru (data untuk 38NL dan 38MD)

#### 38.T.8.1 Klip baru (`KlipKatalog`, id 79-107; format dan kanal persis 38.N.5; ditambah 38NL)

| Id | Nama | Durasi | Kunci (derajat; t dalam tick) | Prop / peristiwa / sinkron |
|---|---|---|---|---|
| 79 | `dayung_yuloh` | loop 40 | berdiri di buritan; RAX −70, LAX −70, RAY −30, LAY 10; TWIST −20 (t0) → +20 (t20) → −20; LEAN 10 ↔ 20 | prop `dayung_yuloh` dua tangan; dayung entitas perahu ikut fase yang sama (`DATA_ANIM_T0`) |
| 80 | `tarik_jala` | loop 30 | LEAN 25 → 5 (t15); RAX/LAX −100 → −40 (t15, easeInOutSine) | prop `jala` (tali jala quad klien ke air) |
| 81 | `jemur_ikan` | loop 40 | RAX −150, LAX −60 → −150 (t20, menggantung ikan) | prop `ikan_tali` |
| 82 | `ikat_tali` | loop 24 | jongkok LIFT −0.3, LEAN 35; RAX −60 ↔ −45, RAZ ±10 (t0/t6/t12/t18), LAX cermin | prop `tali` |
| 83 | `palu_lunas` | loop 16 | LEAN 20; RAX −110 → −50 (t5, easeOutBack) → −110; LAX −50 (memegang pahat dempul) | `palu_dempul` kanan, `pahat` kiri; suara `block.wood.hit` t5 |
| 84 | `teropong_laut` | loop 120 | RAX −95, RAY −15, LAX −95, LAY 15 (dua tangan ke mata); HX −5; HY −30 → +30 (t60) → −30 | `teropong` (prop 37 yang ada) |
| 85 | `pimpin_unta` | loop 30 | lengan kanan ke belakang memegang tali: RAX 25, RAZ 10; HY −40 (menoleh ke unta) tiap 60 tick selama 10 | tali klien ke kepala unta pertama |
| 86 | `tawar_bazar` | loop 24 | LEAN 8; RAX −90 ↔ −40, RAZ 30; LAX −70 (hitung jari, RAZ ±4); mod SHAKE_HEAD t12-t20 | gelembung angka harga |
| 87 | `tari_putar` | loop 40 | **badan berputar 9°/tick** (yaw render klien, bukan kanal); RAX −160, RAZ 40, LAX −160, LAZ −40 bergantian naik-turun 20 tick; LIFT 0 ↔ 0.05 tiap 10 | `selendang` (2 pita quad klien mengikuti putaran, tertinggal 6 tick) |
| 88 | `lukis_dinding` | loop 36 | HX −15; RAX −120 → −90, RAY −20 → +20 (goresan, t0-t18), kembali; LAX −40 (palet) | `kuas_besar`, `palet`; mural billboard bertambah satu goresan per siklus (klien) |
| 89 | `timba_sumur` | loop 60 | RAX/LAX −170 → −60 (t30, menarik tali turun), LEAN 10 → 0 | sinkron `TimbaSumurEntity` (balok miring ±25°) |
| 90 | `tenun` | loop 20 | duduk (`duduk_kursi`); RAX −60 ↔ −40, LAX −40 ↔ −60 bergantian tiap 10 (lempar teropong); RLX −90 ↔ −80 (injak pedal) | sinkron `AlatTenunEntity` |
| 91 | `petik_teh` | loop 16 | LEAN 20; RAX −80 → −95 (t4) → −80 (petik cepat); LAX −40 (keranjang di pinggul) | `keranjang_teh` |
| 92 | `seduh_teh` | once 80 | duduk; t0-t20 angkat teko RAX −70; t20-t40 tuang (seperti 22); t40-t60 tutup cangkir LAX −60, RAZ −15; t60-t80 sodorkan dua tangan RAX/LAX −80, RAY −30, LAY 30, LEAN 15 | `teko`, `cangkir`; uap klien |
| 93 | `nyanyi_perahu` | lapisan kepala | HX −10 ↔ 0 (periode 40), wajah "bicara A/B" bergantian 10 tick | gelembung lirik (kalimat lagu perahu, `tempat_tema_kalimat`) |
| 94 | `lepas_lentera_air` | once 60 | `berlutut`; RAX/LAX −60 → −30 (t30, lepas); HX 25 (menatap lentera) | `lentera_teratai`; lentera klien mulai hanyut di t30 |
| 95 | `daki_pikul` | loop 24 | LEAN 22; LIFT +0.04 di t6 dan t18 (langkah tangga); RAX −150, RAZ 20 (pikulan di bahu); LAX −20 ↔ 10 berayun | `pikulan` atau `rangka_punggung` |
| 96 | `putar_katrol` | loop 30 | LEAN 15; RAX = −90 + 30 sin(2πt/30), RAY = 20 cos(2πt/30); LAX sama, fase sama (dua tangan di engkol) | sinkron engkol `KatrolEntity` / `roda_katrol` |
| 97 | `petik_herba_tebing` | loop 40 | LEAN −10 (memeluk tebing); RAX −160 (meraih ke atas), LAX −110 (memegang rantai); RLX −20, LLX 10 | `sabit_herba` |
| 98 | `sujud_ziarah` | once 120 | t0-t30 tiga langkah maju 1 blok (gerak server); t30-t50 tangan menyatu RAX/LAX −90, RAY/LAY ∓40; t50-t80 `berlutut` + LEAN 70, HX 20, LIFT −0.4; t80-t110 bangkit | `tas_dupa` di punggung |
| 99 | `tabuh_ikan_kayu` | loop 20 | `duduk_lantai`; RAX −70 → −50 (t4) → −70 | `pemukul_kecil`; `ikan_kayu` di lantai (pajangan); suara `block.wood.hit` pitch 1.6 t4 |
| 100 | `sorak_arena` | loop 20 | RAX −160 ↔ −110 (tinju diangkat), LAX −30; HX −10; LIFT 0 ↔ 0.04 | dipakai juga oleh penonton impostor (prosedural) |
| 101 | `asah_pisau` | loop 24 | duduk, LEAN 30; RAX −50, RAY −20 → +20 (gosok); LAX −50 (bilah) | `pisau_ganda`; percikan t12 |
| 102 | `ancam` | once 40 | t0 LEAN 10; t10 RAX −90 (senjata menunjuk), HX −10; tahan sampai t30 | wajah "senyum" + alis turun (38.O.1 atlas) |
| 103 | `hitung_koin_gelap` | loop 30 | duduk; RAX −60 → −50 (ketuk kecil, t0/t10/t20); LAX −50; HY ±30 cepat tiap 30 (curiga) | `kantong_uang` |
| 104 | `berlutut_rantai` | loop 80 | `berlutut`; RAX 20, LAX 20, RAZ 15, LAZ −15 (tangan terikat di belakang); HX 30; mod TREMBLE lemah | `rantai_tangan` |
| 105 | `semedi_darah` | loop 80 | `napas_meditasi` + LIFT +0.03 (melayang); HY ±10 lambat; aura `FLAG_AURA` warna merah tua | aura klien merah |
| 106 | `pikul_tandu` | loop 30 | RAX −150, RAZ 20, LAX −150, LAZ −20 (galah tandu di dua bahu); langkah serempak (fase sama untuk semua pemikul) | sinkron `TanduEntity` (goyang ±2°) |
| 107 | `tabuh_genderang_perahu` | loop 12 | RAX −140 ↔ −40, LAX berlawanan tiap 6; LEAN 10 | penabuh perahu naga; suara genderang t0/t6 |

Prop baru (indeks `PropId` 41-62, ditambah 38NL; model `kultivator:fx/tempat/tema/<id>` dibuat 38TV): 41 `dayung_yuloh`
(galah 1 × 1 × 40 + bilah 1 × 8 × 12), 42 `jala` (gulungan 6 × 4 × 6 bertekstur jaring), 43 `ikan_tali` (3 ikan 2 × 6 ×
1 di tali), 44 `tali` (gulungan 5 × 2 × 5), 45 `palu_dempul` (kepala kayu 3 × 3 × 4 + gagang), 46 `kuas_besar` (gagang 1 ×
12 × 1 + bulu 2 × 3 × 2), 47 `palet` (papan 6 × 0.5 × 5 + 4 titik warna), 48 `kendi` (badan 4 × 5 × 4 + leher 2 × 2 × 2),
49 `keranjang_teh` (5 × 4 × 5 + daun), 50 `lentera_teratai` (6 kelopak 3 × 0.5 × 2 diputar + lilin pancar), 51
`pemukul_kecil`, 52 `ikan_kayu` (badan ikan 6 × 5 × 5 berlubang), 53 `tongkat_cincin` (1 × 20 × 1 + kepala 4 × 4 × 1 dengan
6 cincin), 54 `tongkat_bambu`, 55 `tas_dupa` (3 × 4 × 2 + tali), 56 `rangka_punggung` (rangka 8 × 16 × 2 + muatan 8 × 8 ×
6), 57 `pisau_ganda`, 58 `rantai_tangan`, 59 `cawan_racun` (cawan 3 × 3 × 3 hijau gelap + asap), 60 `cambuk_kafilah`, 61
`sabit_herba`, 62 `kipas_sutra`. (Teropong, pikulan, teko, cangkir, kantong uang: prop 38.O.4 yang ada.)

#### 38.T.8.2 Perlengkapan baru (atlas `perlengkapan.png` dilebarkan 38MD menjadi 64 × 1536, pita 32-47 baru)

| Id | Bagian | Kubus (px) | Pita | Peran |
|---|---|---|---|---|
| `topi_syahbandar` | kepala | badan 8.4 × 3.5 × 8.4; sayap depan melengkung 9 × 0.6 × 3; jambul giok 1.5³ | 32 biru tua + emas | SYAHBANDAR |
| `rompi_bergaris` | badan | 8.6 × 9 × 4.6 bergaris biru-putih | 32 | PELAUT |
| `kalung_gigi_hiu` | badan | 7 gigi 0.8 × 1.5 × 0.6 melingkar | 33 | PELAUT |
| `mantel_nakhoda` | badan + lengan | mantel 9 × 13 × 5, kerah 9 × 2 × 5.4 | 33 | NAKHODA_LAUT |
| `celemek_ter` | badan | 8.2 × 11 × 0.6 hitam berkilap | 34 | TUKANG_KAPAL |
| `serban_seberang` | kepala | lilitan 9 × 4 × 9 + ujung 2 × 3 × 1 | 34 putih/hijau | PEDAGANG_SEBERANG |
| `mahkota_laut` | kepala | dasar 6 × 1.5 × 6 + 5 kelopak 1 × 3 × 0.5 (ombak) | 35 perak-biru | PENDETA_LAUT |
| `serban_emas` | kepala | 9.4 × 4.5 × 9.4 + bros 2 × 2 × 0.5 | 35 | TUAN_BENTENG |
| `kerudung_pasir` | kepala | penutup 8.8 × 8.8 × 8.8 (sisi wajah terbuka mata saja) + ekor kain 4 × 6 × 1 | 36 | PENUNTUN_UNTA (+ item pemain) |
| `topi_bulu_lebar` | kepala | 11 × 3 × 11 + puncak 7 × 3 × 7 | 36 | KAFILAH_BASHI |
| `helm_runcing` | kepala | 8.8 × 5 × 8.8 + kerucut 3 tingkat 6/4/2 | 37 | PENGAWAL_GURUN |
| `baju_sisik` | badan | 8.8 × 10 × 4.8 bertekstur sisik | 37 | PENGAWAL_GURUN |
| `selendang_penari` | badan | sabuk 8.6 × 1 × 4.6 + 2 pita kain (quad klien) | 38 | PENARI_GURUN |
| `sanggul_giok` | kepala | sanggul 4 × 3 × 4 atas + 2 tusuk 6 × 0.5 × 0.5 menyilang | 38 | NYONYA_SUTRA |
| `caping_biru` | kepala | caping 3 tingkat (seperti `caping`) + kain biru di tepi | 39 | PENDAYUNG |
| `jubah_teh` | badan | jubah 8.6 × 13 × 4.6 hijau teh | 39 | AHLI_TEH, SASTRAWAN (biru) |
| `kasaya` | badan | kain menyilang bahu 9 × 14 × 5 + lipatan | 40 merah-emas | KEPALA_BIARA |
| `ikat_kepala_kuning` | kepala | pita 8.6 × 1.5 × 8.6 + ekor 1 × 4 × 0.5 | 40 | PEZIARAH |
| `tali_melingkar` | badan | gulungan tali menyilang dada 10 × 10 × 1 + 3 lilitan | 41 | PEMETIK_HERBA |
| `topeng_besi` | kepala | pelat 8.4 × 6 × 0.6 depan + lubang mata (alpha) | 42 | PENEGAK_IBLIS |
| `rantai_bahu` | badan | 6 mata rantai 1.5 × 1 × 0.5 di bahu kiri ke pinggang | 42 | PENEGAK_IBLIS, JAGAL_ARENA |
| `mahkota_tanduk` | kepala | lingkar 8.6 × 2 × 8.6 + 2 tanduk 3 segmen (2 × 3 × 2, 1.5 × 3 × 1.5, 1 × 2 × 1, rot 22.5) | 43 hitam-merah | PENGUASA_IBLIS |
| `cadar_ular` | kepala | cadar 8.4 × 4 × 0.4 bawah mata + mahkota ular 6 × 2 × 6 | 44 | MAKELAR_GELAP |
| `penutup_mata` | kepala | pita 8.6 × 2 × 8.6 di mata | 44 | PENJAGA_KITAB_TERLARANG |
| `tudung_pembunuh` | kepala | tudung 9 × 9 × 9 menjorok 1 ke depan | 45 | PEMBUNUH_BAYARAN |
| `zirah_gladiator` | badan + lengan | bahu kiri saja 5 × 4 × 5 + sabuk lebar 9 × 3 × 5 | 45 | GLADIATOR_IBLIS |
| `jubah_pemburu_lurus` | badan | jubah 8.6 × 13 × 4.6 putih-biru + panel lambang (decal sekte Lurus) | 46 | PEMBURU_LURUS |
| `jubah_naga_hitam` | badan | 8.8 × 14 × 4.8, kerah tinggi 9 × 3 × 5, lengan lebar | 47 | PENGUASA_IBLIS |

### 38.T.9 Model 3D dan animasi model (modul 38TV; perilaku server 38TK)

Aturan 38.O.0 berlaku (kubus ModelPart, tekstur prosedural `tools/png.py`, palet 38.O.0 + palet tema: biru laut
`#2E5E8C`, pasir `#D8B878`, lumpur `#8A6A48`, putih kapur `#EDE8DC`, genteng hitam `#2A2A30`, emas puncak `#E0B040`,
hitam iblis `#1C1418`, merah darah `#7A1020`; tanpa placeholder; pratinjau 8× dan render isometrik dibuka dengan Read).
Ukuran dalam piksel model (16 = 1 blok). Semua entitas di bawah: `noSave`, dimunculkan dari jangkar/titik, LOD: > 64 blok
tanpa animasi kecil, > 128 blok tidak digambar (kecuali kapal di laut: 192).

#### 38.T.9.1 Kapal dan perahu

| Entitas (`kultivator:<id>`) | Bagian model (px) | Tekstur | Animasi |
|---|---|---|---|
| `kapal_dagang` (jung dagang, 22 × 7 blok) | lunas 16 × 8 × 352; lambung 3 lapis menyempit ke bawah: 64 × 16 × 320, 96 × 16 × 336, bibir geladak 112 × 8 × 352 (sisi = 2 × 8 × 352 dengan papan bertekstur); haluan persegi 112 × 40 × 8 dengan **mata kapal** dicat; buritan tinggi 112 × 48 × 64 + pagar tiang 2 × 10 × 2 ×12; kemudi 8 × 64 × 40 (engsel di buritan); 3 tiang: utama 8 × 320 × 8, depan 6 × 240 × 6, belakang 6 × 200 × 6; **layar bilah**: layar = 9 panel 160 × 24 × 1 (utama), 120 × 18 × 1 (depan), 100 × 15 × 1 (belakang) dengan bilah bambu 164 × 2 × 2 di antara panel; jangkar 16 × 24 × 4; papan titian 24 × 2 × 96; lentera buritan 6³ pancar; peti muatan 3-6 (16³) di geladak | 256 × 256 `kapal_dagang.png`: kayu merah-coklat, haluan merah-hijau-putih, layar tikar kuning-coklat bergaris, varian layar biru-putih (`jung_laut`) | diam: LIFT ±0.08 periode 80, oleng ±1.5°; **layar gulung**: panel turun dan bertumpuk ke bawah bergiliran (panel i mulai t = 4i, 40 tick) — sebaliknya saat kembang; berlayar: miring 3°, buih buritan (partikel); jangkar turun/naik 30 tick (rantai = garis klien); titian turun 20 tick; kemudi bergerak ±12° saat berbelok; geladak bisa dipijak (collider pola `KapalDekEntity`) |
| `kapal_bajak_laut` | hull `kapal_dagang` + layar robek (panel ber-alpha), panji hitam (kain klien), haluan bertaji 8 × 8 × 32 (tanpa meriam) | `kapal_bajak_laut.png`: kayu hitam, layar hitam-merah | seperti di atas + titian turun cepat 10 tick saat serbuan |
| `perahu_nelayan` (6 × 2 blok) | lambung 32 × 10 × 96 dengan ujung melengkung (3 kubus rot 22.5 tiap ujung); atap anyaman lengkung 28 × 14 × 32 (3 kubus); bangku 28 × 2 × 4; dayung 2 × 2 × 48 | 64 × 64 kayu + anyaman | terombang-ambing; dayung kayuh saat bergerak; lomba: cepat + percikan |
| `perahu_kanal` (7 × 2 blok; varian `wisata`, `lapak`, `hitam`) | lambung 30 × 10 × 112, ujung naik; atap hitam lengkung 3 segmen 32 × 16 × 24 (rot 0/±11.25); yuloh (dayung buritan) 2 × 2 × 64 berpivot di buritan; bangku 2; lentera haluan 4 × 6 × 4 pancar; varian lapak: 4 peti/keranjang + tenda kecil | 64 × 64 kayu tua + atap anyaman hitam | gerak sepanjang spline `jalur_perahu`; yuloh mengayun ±25° sinkron klip 79; haluan naik-turun kecil; berbelok mulus (yaw dari turunan spline) |
| `perahu_naga` (18 × 2 blok) | lambung sempit 24 × 10 × 288; kepala naga 24 × 32 × 40 (rahang 20 × 6 × 24 membuka, tanduk 2 × 10 × 2 ×2 rot 22.5, janggut 4 × 8 × 1, mata pancar); ekor melingkar 3 segmen 16 × 24 × 32 → 8 × 16 × 16; genderang 12³ di tengah; 10 pasang dayung 1 × 1 × 24 | 128 × 128 sisik merah-emas-hijau | dayung serempak sinus (frekuensi ∝ kecepatan); rahang naga membuka/menutup tiap ketukan; pendayung = **impostor klien** (10 tubuh ringan, bukan entitas) |

#### 38.T.9.2 Makhluk

| Entitas | Pemilik server | Bagian model (px) | Tekstur | Animasi |
|---|---|---|---|---|
| `unta_kafilah` (unta Baktria, tinggi 2.3 blok) | 38TK | badan 20 × 18 × 36 (pivot pinggang); **dua punuk** 12 × 10 × 12 (z −8, +8) dengan jumbai 10 × 3 × 10; leher 8 × 22 × 8 miring 35° + jumbai leher 9 × 10 × 2; kepala 8 × 9 × 16 + bibir 6 × 3 × 4 + telinga 2 × 4 × 1; kaki 4 × (paha 6 × 24 × 6 + betis 5 × 14 × 5, sendi lutut), telapak 7 × 2 × 7; ekor 3 × 12 × 2; varian beban: rangka 22 × 2 × 24 + 2 karung 8 × 14 × 16 + gulungan permadani 6 × 6 × 22 + lonceng leher 3³ | 128 × 128 coklat pasir dengan bulu gelap lebat di punuk, leher, paha (musim dingin lebih lebat) | jalan **pace** (kaki sesisi bergerak bersama, khas unta), kepala mengangguk; punuk bergoyang ±2°; mengunyah (rahang) diam; ekor mengibas; **berlutut**: kaki depan menekuk dulu (lutut turun, 20 tick), lalu kaki belakang (20 tick), badan turun 0.9 blok; bangun sebaliknya; badai: berlutut + kepala rendah |
| `kalajengking_raja` (bos, panjang 4, tinggi 2.2) | 38TK | sefalotoraks 32 × 14 × 28; perut 4 segmen 28 × 12 × 10 → 22 × 10 × 8; ekor 6 segmen 10 × 10 × 12 → 6 × 6 × 8 melengkung ke atas (rot −30° per segmen) + sengat kait 8 × 10 × 14; capit: lengan 6 × 6 × 20 + capit atas/bawah 10 × 8 × 14 / 10 × 4 × 14; 8 kaki 3 ruas 3 × 3 × 14; mata 6 kubus 1³ pancar | 128 × 128 kitin hitam-merah, retakan oranye pancar | diam: ekor mengayun, capit berderak; jalan: gelombang kaki; **capit** (6 tick snap); **sengat** (ekor melengkung ke depan 10 tick easeOutBack, partikel racun); **gali**: tenggelam ke pasir 30 tick (hanya gambar + partikel, tidak ada blok berubah, tubuh tak terlihat dan kebal 4 s) lalu muncul di dekat pemain |
| `kera_gunung` (0.7 blok; varian putih tua 1.8 ×) | 38TK | badan 8 × 10 × 12; kepala 7 × 7 × 6 + muka 5 × 4 × 1 merah muda + telinga; lengan 3 × 10 × 3; kaki 3 × 8 × 3; ekor 2 × 2 × 10; varian tua: janggut 4 × 4 × 1, alis putih | 64 × 64 coklat-abu (putih untuk varian) | duduk menggaruk; lari empat kaki; **rebut** (lengan menyambar 6 tick, barang muncul di tangan); memanjat (lompatan); varian tua: gestur menunjuk, membungkuk |

#### 38.T.9.3 Mekanisme dan dekorasi berentitas

| Entitas | Bagian model (px) | Animasi |
|---|---|---|
| `api_suar` (lampu mercusuar) | rumah lampu kuningan 24³ (rangka 2 × 24 × 2 ×4 + atap kerucut 3 tingkat), lensa (kaca bertekstur cincin), sumbu pancar | lensa berputar 3°/tick; **sinar** = 2 quad aditif 4 × 64 blok (klien), menyala 12500-23500 dan saat badai; Minimal: hanya cahaya titik |
| `derek_dermaga` (derek injak) | dua kaki A 4 × 128 × 4; lengan 4 × 4 × 128 miring 30°; roda injak Ø 64 (12 jari 2 × 64 × 2 rot 15° + pelek 16 segmen); kait 4 × 6 × 2 + peti 16³ | roda berputar, peti naik 3 blok, lengan berayun 90° (60 tick), turun; siklus 200 tick saat kapal tambat |
| `kapal_galangan` | tahap 0 lunas 16 × 8 × 352; 1 + 12 gading lengkung 4 × 48 × 4 (3 kubus per gading); 2 + papan separuh; 3 lambung penuh + pangkal tiang; 4 tiang + layar tergulung | tahap berubah dengan efek "papan baru" (skala 0 → 1, 10 tick) saat hari pesanan bertambah |
| `pintu_air` | daun papan 112 × 64 × 6, alur samping 6 × 80 × 6 ×2, rantai katrol | naik-turun 40 tick (05:00 buka, 21:00 tutup), percikan |
| `timba_sumur` | tiang 6 × 48 × 6; balok 4 × 4 × 96 berpivot; pemberat batu 12³; ember 8³ di tali | balok miring ±25° sinkron klip 89 |
| `alat_tenun` | rangka 40 × 36 × 24; benang lungsin (quad bertekstur); sisir 36 × 3 × 3 berayun 15°; teropong 8 × 2 × 2 meluncur; 2 pedal; gulungan kain (silinder 6 kubus) | sisir dan teropong sinkron klip 90; gulungan membesar sesuai kemajuan |
| `roda_katrol` (Ø 9 blok / kecil Ø 3) | 8 jari 4 × 144 × 4 rot 22.5°, pelek 16 segmen, poros 12³ | berputar saat kereta/katrol bergerak |
| `kereta_gantung` | kabin 40 × 36 × 40 kayu berkisi + atap bertingkat 48 × 6 × 48 / 32 × 6 × 32; lengan gantung 4 × 40 × 4; troli 16 × 8 × 24 dengan 2 roda Ø 10; pintu geser 16 × 28 × 1 | troli roda berputar; kabin berayun pendulum ±3° teredam saat mulai/berhenti dan melewati tiang; pintu geser 10 tick; **tali** = kurva gantung klien (rantai 3 px, sag 4 % bentang), 1 batch |
| `katrol` | keranjang anyaman 24 × 20 × 24, 4 tali (garis klien) ke roda; drum engkol 16 × 16 × 24 + engkol 2 × 2 × 10 | keranjang naik/turun 12 s, berayun kecil; engkol berputar sinkron klip 96 |
| `pintu_arena` | jeruji 48 × 64 × 2 (12 batang 2 × 64 × 2 + 4 palang), rantai | naik 32 tick, debu; jatuh cepat 8 tick + guncang kamera klien kecil |
| `tandu` | kabin 24 × 32 × 24 beratap lengkung + tirai (quad), 2 galah 2 × 2 × 96 | goyang ±2° sinkron klip 106; tirai berayun |
| `gerbang_tengkorak` | daun ganda besi 48 × 96 × 4 bertekstur relief; **rahang bawah** 160 × 48 × 64 dengan 10 gigi 6 × 10 × 6 | buka: rahang turun 25° (24 tick, debu, suara `block.iron_door.open` pitch 0.6) lalu daun berayun 90° |
| `batu_runtuh` | bongkah 3 kubus diputar (20 × 18 × 20 inti) | menggelinding (rotasi ∝ jarak), terpental di pagar (entitas collider), pecah menjadi partikel saat berhenti |
| `herba_tebing` | tanaman silang 12 × 16 × 12 (quad) + kuncup pancar | bergoyang angin; menghilang (skala → 0) saat dipetik bagi pemain itu |
| `temuan_pasir` (3 varian) | koin 6 × 1 × 6, pecahan mural 10 × 8 × 1, lempeng peta 8 × 1 × 10, setengah tertanam | kilau berkala |
| `pilar_rune`, `jerat`, `segel_pintu`, `palang`, `tali_turun`, `sel_tawanan` | pilar 8 × 32 × 8 bergaris rune pancar; jerat cincin 12 × 1 × 12 bergerigi; segel pelat 32 × 48 × 2 bertulisan pancar; palang rantai 2 garis; tali 1 × 1 × n; sel memakai model `sangkar` 38MD (diimpor, tidak disunting) | rune berdenyut; jerat menutup 4 tick; segel retak dan memudar 20 tick |

#### 38.T.9.4 Efek klien tema (38TV; semua patuh `EffectQuality` dan `EpicBudget`)

| Efek | Spesifikasi | Min / Nor / Maks |
|---|---|---|
| Badai pasir | overlay kabut warna `#C8A060` (jarak 16 blok), partikel pasir quad horizontal, dinding pasir cakrawala (1 quad besar 256 × 64 blok bergerak 0.3 b/tick), suara angin loop | overlay saja / 400 / 900 partikel per detik |
| Lentera air | lentera teratai quad bertumpuk (kelopak 2 quad + api pancar), hanyut sepanjang spline kanal (titik `jalur_perahu` dikirim sekali per kota), berputar 0.5°/tick, memudar 600 tick | 0 / 40 / 120 |
| Cahaya Budha | cincin pelangi 5 lapis quad aditif di awan berlawanan arah matahari + siluet pemain (bayangan kepala) di tengah | 1 cincin / 3 / 5 lapis |
| Lautan awan | 2 lapis quad awan bertekstur di bawah baris atas Kota Gunung pada pagi dan saat hujan | 1 / 2 / 2 lapis |
| Kabut gunung | kabut putih jarak 24-48 di baris atas saat pagi/hujan | ya / ya / ya |
| Riak kolam darah | 3 cincin aditif merah membesar di permukaan kolam istana | 0 / 1 / 3 |
| Ombak teluk badai | permukaan quad bergelombang di sekitar perahu sewaan | 0 / 1 / 1 |
| Sinar mercusuar | 2 quad aditif berputar | titik cahaya / 2 / 2 |
| Kain dan panji | memakai `KainKota` 38MD (impor) | ≤ 24 per kota |
| Mural bergerak (quest oasis) | billboard 2 frame berganti 20 tick | ya |

### 38.T.10 Dagang antar-kota, toko khas dan harga (kurs tunggal 38.M.0)

Semua harga dalam **Nilai Batu (NB)**: Rendah 1, Menengah 9, Tinggi 81, Agung 729 (`NilaiBatu.VALUE`); dibayar lewat
`NilaiBatu.pay/give` (`give` tidak pernah mengeluarkan Agung); `LabelHarga` 38.U menampilkan rincian batu (mis. 150 NB =
"1 Tinggi · 7 Menengah · 6 Rendah"). PK hanya muncul pada biaya sekte (2 PK per murid pengawal per hari, setara 4 NB
menurut 1 PK = 2 NB, **tidak pernah** ditukar langsung). Aturan 38.M.5 berlaku: beli-kembali ≤ 0.5, komponen
`kultivator:asal_toko` (3 hari), tidak ada pencetakan uang.

#### 38.T.10.1 Barang dagang (`kultivator:dagang/<id>`, item baru 38TK, model item 38TV)

Kolom permintaan = faktor harga jual di kota itu (≤ 1.35; produsen 0.8-1.0); "—" = tidak dibeli.

| Barang | Asal (harga beli dasar) | Pelabuhan | Oasis | Air | Gunung | Ibu kota / kota 34 | Kota Iblis (pasar gelap) |
|---|---|---|---|---|---|---|---|
| `ikan_asin_roh` | pelabuhan 4 | 0.8 | 1.30 | 0.95 | 1.25 | 1.15 | 1.0 |
| `mutiara_laut` | pelabuhan 40 | 0.8 | 1.30 | 1.25 | 1.0 | 1.20 | 1.1 |
| `rempah_seberang` | pelabuhan 12 | 0.85 | 0.9 | 1.25 | 1.30 | 1.20 | 1.0 |
| `porselen_hijau` | pelabuhan 24 | 0.85 | 1.35 | 1.0 | 1.10 | 1.20 | — |
| `kurma_emas` | oasis 3 | 1.25 | 0.8 | 1.30 | 1.20 | 1.10 | 1.0 |
| `permadani_gurun` | oasis 30 | 1.15 | 0.85 | 1.20 | 1.0 | 1.30 | 1.1 |
| `giok_barat` | oasis 60 | 1.10 | 0.85 | 1.30 | 1.15 | 1.25 | 1.2 |
| `kaca_warna` | oasis 16 | 1.0 | 0.85 | 1.25 | 1.30 | 1.15 | — |
| `sutra_awan` | air 36 | 1.30 | 1.35 | 0.8 | 1.10 | 1.20 | 1.1 |
| `teh_bi_luo` (peti kecil) | air 10 | 1.25 | 1.30 | 0.8 | 0.95 | 1.15 | — |
| `arak_beras` | air 6 | 1.20 | 1.25 | 0.85 | 1.0 | 1.05 | 1.2 |
| `herba_tebing_kering` | gunung 14 | 1.20 | 1.15 | 1.25 | 0.8 | 1.30 | 1.1 |
| `dupa_gunung` | gunung 5 | 1.30 | 1.20 | 1.15 | 0.8 | 1.10 | — |
| `teh_awan_gunung` | gunung 9 | 1.20 | 1.30 | 0.9 | 0.8 | 1.10 | — |
| `lingzhi_gunung` | gunung 45 | 1.15 | 1.20 | 1.20 | 0.85 | 1.30 | 1.25 |
| `racun_ular_hitam` | iblis 18 | — | — | — | — | penadah Pasar Gelap ibu kota 1.2 | 0.8 |
| `minyak_bayangan` (selundupan) | iblis 30 (hanya pasar gelap) | penadah 1.35 | penadah 1.35 | penadah 1.35 | — | — | 0.8 |

- **Beli** di kota asal pada harga dasar (diskon Nama Baik/gelar berlaku, maks 15 %), stok **16 per barang per pemain per
  hari dunia**; kafilah/kapal yang baru datang menurunkan harga barangnya 10 % selama 2 hari.
- **Jual** di kota lain: `dasar × permintaan × jenuh`, `jenuh = max(0.6, 1 − 0.04 × n)` dengan n = jumlah unit barang itu
  yang dijual **semua pemain** ke kota itu dalam 24 jam dunia terakhir (pulih 1 unit per jam dunia). Permintaan bergeser
  ±0.05 tiap 7 hari dunia (hash) dan karena peristiwa: badai pasir → kurma/ikan asin di oasis +0.15 (2 hari); Pelita padam
  → mutiara di pelabuhan +0.1; Musim Ziarah → dupa di gunung tetap 0.8 tetapi di tempat lain +0.1; Pesta Darah → racun
  di Kota Iblis −0.1.
- **Batas laba** per pemain per minggu dunia: `150 × (band + 1)` NB (dihitung jual − beli barang dagang); melewatinya,
  harga jual barang dagang untuk pemain itu turun ke `0.5 × dasar` sampai minggu berikutnya. Margin ideal per unit
  ≤ +35 %.
- **Selundupan** (`minyak_bayangan`): tidak pernah dijual toko lurus; hanya penadah (PENGEMIS/WARGA di kumuh ibu kota,
  Lao Shu di Kota Air, gang di kampung pelaut). Bea gerbang kota tema (Gerbang Giok, Kantor Urusan Laut, Pintu Air,
  Gerbang Kaki) memeriksa: 40 % peluang **disita permanen** (bukan Barang Sitaan 38.R yang bisa diambil
  kembali) + Nama Baik −5 di kota itu; ibu kota: poin kejahatan 6 (38.B.5.2). Infami +2 per transaksi.
- **Bea** (sink): Gerbang Giok 3 % nilai barang dagang yang dibawa masuk (Surat Jalan bebas), Kantor Urusan Laut 2 % atas
  penjualan di Pasar Seberang; kota lain tanpa bea.
- **Peti Dagang** (`kultivator:dagang/peti`): wadah tersegel 8 unit satu jenis (segel 1 NB, dibuka di kota mana pun);
  dipakai Kontrak Muatan dan Kafilah Sekte; bukan wadah berisi untuk toko (ditolak dijual ke toko biasa, 38.0 butir 16).
- Permintaan disimpan sebagai data `data/kultivator/tempat/permintaan/kota_tema_<tema>.json` (`{barang: faktor}`) yang
  dibaca generik oleh `KafilahSekte` 38EK (amandemen 38.T.14.3), sehingga kafilah sekte pemain memakai tabel yang sama.

**Jalur dagang khas** (laba ideal per unit sebelum jenuh dan batas, untuk penguji `uji tema dagang`):

| Jalur | Barang | Laba ideal / unit | Catatan |
|---|---|---|---|
| Air → Oasis ("Jalur Sutra") | Sutra Awan | 36 → 48.6 = +12.6 | jalur terbaik, jauh dan berseberangan bioma |
| Pelabuhan → Oasis | Porselen Hijau | 24 → 32.4 = +8.4 | |
| Oasis → Air | Giok Barat | 60 → 78 = +18 | barang mahal, modal besar |
| Gunung → ibu kota | Lingzhi Gunung | 45 → 58.5 = +13.5 | |
| Gunung → Pelabuhan | Dupa Gunung | 5 → 6.5 = +1.5 | murah, untuk pita rendah |
| Pelabuhan → Gunung | Rempah Seberang | 12 → 15.6 = +3.6 | |

#### 38.T.10.2 Toko dan jasa khas (layar 38.T.13.3; pita = syarat beli)

| Kota | Barang / jasa | Harga (NB) | Batas / catatan |
|---|---|---|---|
| Pelabuhan | Ikan Roh / Sup Ikan Pagi | 6 / 3 | sup: kenyang + regen Qi +5 % 3 menit |
| Pelabuhan | **Kompas Pasang** (menunjuk Pusaran Laut terdekat) | 150 | pita ≥ 2, 1 per pemain |
| Pelabuhan | Mutiara Penunjuk Laut (item modul D, lewat `Dimensi.itemById`) | 300 | pita ≥ 3, stok 1 per 3 hari |
| Pelabuhan | Kapal Lintas Laut ke Laut Tanpa Batas | `40 × (1 + band/4)` | pita ≥ 2, 1 per hari |
| Pelabuhan | Lunas perahu awan / kapal sekte | 900 / 4000 | pita ≥ 3 / ≥ 4; 2 / 4 hari |
| Pelabuhan | Sewa perahu nelayan / lomba perahu | 2 per jam dunia / 5 | |
| Pelabuhan | Bahan bakar kapal terbang (batu roh) | nilai `NilaiBatu.VALUE` | tanpa diskon |
| Oasis | Kerudung Pasir / Air Suci / Kurma Emas | 15 / 2 / 3 | |
| Oasis | Restu Mata Air | 3 | 1 per hari |
| Oasis | Sewa unta / ikut kafilah | 12 per hari / 8 | |
| Oasis | Pesanan mural | 50 | 1 mural per pemain per kota |
| Air | Sutra Awan / Kipas Sutra / Jubah Sutra (pesanan) | 36 / 20 / 80 | jubah 2 hari |
| Air | Teh Bi Luo / Teh Embun Gunung / Teh Seribu Tahun | 8 / 5 / 60 | Seribu Tahun pita ≥ 3, 1 per hari |
| Air | Lentera teratai / Perahu Pos / sewa perahu / Tur Kanal | 1 / 1 / 2 per jam / 3 | lentera maks 3 per festival |
| Gunung | Herba gunung berumur | `Harga.value × 1.0` | umur ≤ 300 (M3) |
| Gunung | Rumput Awan Seribu Tahun | 120 | stok 1 per 7 hari |
| Gunung | Tali Panjat / dupa ziarah / Rumah Tamu Biara | 15 / 2 / 3 | |
| Gunung | Kereta gantung / jasa kuli | 1 / 2 | turun gratis |
| Iblis | Upeti Gerbang | `10 × (band + 1)` | gratis Gentar ≥ 60 |
| Iblis | Pil Darah (pulih 30 % HP seketika, Lemah I 20 s) | 25 | 3 per hari |
| Iblis | Racun (pelapis senjata: Racun I 4 s pada 3 serangan) | 12 / 30 / 80 (lemah/sedang/kuat) | pita ≥ 2/3/4 |
| Iblis | Gulungan Bakar Darah / Langkah Hantu / Rantai Jiwa / Topeng Iblis Hati | 40 / 60 / 90 / 120 | pita ≥ 2/3/4/4; Infami +2 per beli |
| Iblis | Topeng Tanpa Wajah | 70 | kosmetik + efek Jam Berburu |
| Iblis | Peta sarang | 35 | 1 per hari |
| Iblis | Tebus tawanan | `25 × (1 + kekuatanKota / 2)` | maks 3 per hari |
| Iblis | Arena (biaya) / taruhan maks | `8 × (band + 1)` / `20 × (band + 1)` | 5 laga / 6 taruhan per malam |

Markup pasar gelap 1.3 berlaku pada harga dasar barang `Harga` umum (bukan pada tabel di atas, yang sudah final).

#### 38.T.10.3 Kontrak muatan (darat, laut, langit)

- Diterbitkan di **Balai Dewan Saudagar** (pelabuhan), **Karavanserai Agung** (oasis), **Rumah Sutra** (air), **Pos Kuli**
  (gunung): "antar N Peti Dagang tersegel (disediakan) ke kota tema/ibu kota Y yang **diketahui** dalam D hari dunia".
  Deposit 20 % nilai muatan (kembali saat tiba). Bayaran `ceil(jarak / 100 × (2 + band))` NB + 25 % bila tiba di separuh
  waktu; dengan kapal terbang (diserahkan di Menara Tambat Langit atau Dermaga Langit ibu kota) × 1.3. Maks 2 kontrak
  aktif; 3 per minggu.
- Gagal (waktu habis, peti dibuka) → deposit hilang, Nama Baik −2 di kota penerbit. Risiko di jalan: begal 38.C.6.3
  (tekanan harian M2), perompak langit modul T (sistem yang ada). Tidak ada risiko tambahan buatan 38.T.
- Kontrak dihitung sebagai misi gilda `muatan` (poin lencana, 38.T.11).

### 38.T.11 Tautan ke sistem lain

| Sistem | Tautan | Pemilik kode |
|---|---|---|
| **Stasiun teleport** (38.B.4.3) | setiap kota tema punya jangkar `kotabesar/cakram` (dilayani 38KS tanpa kode baru); jenis tujuan ditambah `kota_tema/pelabuhan|oasis|air|gunung|iblis`; titik tiba `TempatApi.arrival` = `kota/alun` (empat tema) atau **luar** Gerbang Tengkorak (iblis, 24 blok di depan gerbang); Kota Iblis hanya bisa dituju setelah pernah masuk | 38KS (daftar jenis), 38TK (jangkar) |
| **Perjalanan Awan, rute kapal** | Dermaga Langit ibu kota menyebut kota tema ≥ 600 blok; Menara Tambat Langit pelabuhan = `registerDestination("kota_tema_pelabuhan_<i>", ...)` | 38TK (`DuniaApi.routes()`), 38KS (daftar Perjalanan Awan) |
| **Laut Tanpa Batas** | Kapal Lintas Laut → `SeaGate.enter(player, true)`; pulang lewat Gerbang Pulang yang ada; quest pelabuhan bab 5 memakai `SeaGate.tagOf` untuk tujuan pulau | 38TK (memanggil API publik D) |
| **Misi gilda** (38.M.3) | jenis misi baru lewat `EkonomiApi.daftarJenisMisi(JenisMisi)`: `muatan` (D-B), `kawal_unta` (D-B), `reruntuhan` (C-A), `antar_sutra` (E), `herba_tebing` (D), `pencuri_kanal` (D), `bajak_laut` (B), `buronan_iblis` (B-S), `tebus_tawanan` (C); Papan Gilda salinan (E-C) di empat kota tema damai (`kotabesar/layanan` `papan_gilda`); Kota Iblis memakai Sarang Kontrak | 38EK (API pendaftaran), 38TK (jenis) |
| **Sekte NPC** (`DiplomasiService`) | Biara Awan = "cabang" sekte Lurus terdekat (nama dan lambang di papan biara); Kota Iblis berpanji sekte Iblis terdekat (`GerbangPlan` aliran Iblis) bila ada; Penyerbuan Lurus dan Papan Buronan atas nama sekte Lurus terdekat; `adjust` hanya lewat API (tebus +2, buronan +3, penyerbuan ±5, melawan pemburu −3) | 38TK (pembaca) |
| **Kafilah sekte / kafilah jalan** (38.M.4.2, 38.C.6.3) | tujuan kafilah sekte termasuk kota tema yang diketahui; permintaan dari data 38.T.10.1; rupa kafilah `TemaApi.rupaKafilah` (unta di rute yang menyentuh oasis, kapal di rute pelabuhan↔pelabuhan) | 38EK (pembaca), 38TK (data + API) |
| **Sibuk** (`musuh/Sibuk`) | `Sibuk.register`: `tema.pelayaran` (adegan Kapal Lintas Laut), `tema.perahu` (penumpang perahu/tur), `tema.lift` (kereta gantung/katrol), `tema.arena` (laga arena darah), `tema.tari` (mini-gim tari/irama); semua peristiwa tema (bajak laut, Jam Berburu, Pemburu Lurus, Tikus Air, Penyerbuan Lurus) memanggil `Sibuk.why` dan menunda 200 tick; peristiwa musuh ikut tekanan harian M2 | 38TK |
| **Tempat aman** | `Rencana.zonaAman`: empat kota tema seluruhnya + `pos_pemburu`; Kota Iblis hanya Zona Perjanjian | 38W (TempatZones membaca), 38TK (kotak) |
| **Mode Kehancuran** | semua zona kota tema (termasuk Kota Iblis) dilindungi `TempatZones` → `ProtectedZones`; satelit reruntuhan tidak | 38W |
| **Perbaikan dan ganti rugi** (38.R) | `tempat_dirawat` + faktor tempat: pelabuhan 1.8, oasis 1.8, air 2.0, gunung 2.0, iblis 2.0 gaya `iblis`; kekuatan (38.R.5): pelabuhan 3, oasis 4, air 2, gunung 4, iblis = ranah Penguasa; penagih: PETUGAS_BEA, PENGAWAL_GURUN, KEPALA_DESA (kepala kampung air), BIKSU_GUNUNG, PENEGAK_IBLIS; dermaga di atas air dipulihkan biasa (restore hanya ke udara/air yang direkam); **air kanal** boleh dipulihkan sebagai sumber bila posisinya di kotak titik `kanal` (amandemen aturan 38.R.3 butir 6) | 38PR |
| **Nama Baik** (K) | empat kota tema adalah "kota" K lewat `kota/situs` (`Towns` menerima `kota/kota_tema`, SAFE_RADIUS 160); Kota Iblis tidak (Gentar dan Infami) | 38KS (`Towns`), 38TK |
| **Murid** | `Disciples.addFollowVeto`: arena, sel tawanan, kereta gantung, katrol, perahu tur (murid menunggu di stasiun/dermaga/pintu); Kapal Lintas Laut boleh 2 murid | 38TK |
| **Duel Pedang** (35) | laga arena darah dan duel quest memakai `DuelChallenges.challenge` dengan aturan arena (verifikasi `DuelRuleSet`) | 38TK |
| **Meditasi** | `SpiritSites.addSiteProvider` (38TK, satu provider): "Mata Gurun" ×1.25, "Taman Klasik" ×1.2, "Aula Emas" ×1.4 (Restu Puncak), "Kolam Semedi Darah" ×1.3 (hanya bila Infami ≥ 20); semua dalam CAP | 38TK |
| **Peristiwa langit** | dibaca saja: Gerhana Darah → Pesta Darah; bulan purnama/sabit/mati → festival; petir → badai laut | 38TK |
| **Hub dan peta** | penanda `DuniaMarkers` 42 `kota_tema/pelabuhan` (`#5FA8C8`), 43 `oasis` (`#E0B060`), 44 `air` (`#7FC8B0`), 45 `gunung` (`#A0A8B8`), 46 `iblis` (`#8A1020`), 47 `kota_tema/reruntuhan_gurun` (`#C09050`), 48 `kota_tema/pos_pemburu` (`#D8E4F0`), live 49 `tema/kapal_dagang`; kartu hub DUNIA **"Kota Bertema"** (daftar kota tema diketahui, jarak, acara berikutnya, meter Infami dan Gentar, **Buku Dagang**: harga terakhir yang diketahui pemain per kota, diperbarui saat mengunjungi pasar atau mendengar kabar) lewat `HubCards.register` | 38W (penanda), 38TV (kartu) |
| **Kitab Panduan** | bab **20 "Lima Kota Bertema"** (indeks 19, `CHAPTER_COUNT` +1 setelah bab 18-19 38W; `minRealm` Fana): `c20.pelabuhan` (masuk), `c20.oasis`, `c20.air`, `c20.gunung`, `c20.dagang` (jual barang dagang di kota lain dengan laba), `c20.laut` (Kapal Lintas Laut), `c20.ziarah` (Seribu Anak Tangga), `c20.lentera` (Malam Lentera Air), `c20.iblis` (opsional, `minRealm` Inti Emas: masuk Kota Iblis), `c20.tebus` (opsional: tebus satu tawanan) | 38W (`GuideChapters`), 38TK (teks `tempat_tema_panduan`) |
| **Rumor** (38.S.5) | templat `data/kultivator/tempat/kabar/tema_*.json`: kafilah tiba, badai pasir, pelita padam/menyala, bajak laut, lomba perahu naga, longsor, Kota Iblis dikepung, buronan iblis terlihat di jalan, tawanan pulang, harga sutra naik di oasis | 38TK (data), 38ST (mesin) |
| **Advancement** | `tempat/tema_pelabuhan` … `tema_iblis` (masuk), `tema_penjaga_pelita`, `tema_pembawa_air`, `tema_penenun_malam`, `tema_penjaga_tali`, `tema_peziarah`, `tema_saudagar` (laba dagang 1000 NB total), `tema_lima_kota`, `tema_juara_tangga_darah`, `tema_pemutus_rantai`, `tema_pembawa_fajar`, `tema_tamu_darah` (tersembunyi), `tema_pembebas` (tebus 10) | 38TK |

### 38.T.12 Suara baru (Jepang; 38ST menambah ke `tools/gen_voices.py` `WORLD`, grup `tema.*` di `VoiceEvents`)

| Kunci | Suara | Jepang | English | Indonesia | Emosi |
|---|---|---|---|---|---|
| `tema.pelabuhan.welcome` | f2 | 港へようこそ。荷を下ろすなら、まず税関へ。 | Welcome to the harbour. If you're unloading, customs first. | Selamat datang di pelabuhan. Kalau mau bongkar muatan, ke bea cukai dulu. | normal |
| `tema.pelabuhan.fish_call` | f1 | 獲れたてだよ！朝の霊魚、今だけだよ！ | Fresh from the sea! Morning spirit fish, only for now! | Baru ditangkap! Ikan roh pagi, cuma sekarang! | joy |
| `tema.pelabuhan.ship_arrive` | m2 | 南の船が入ったぞ！荷揚げを急げ！ | A southern ship is in! Hurry with the unloading! | Kapal selatan masuk! Cepat bongkar muatan! | joy |
| `tema.pelabuhan.ship_depart` | m2 | 錨を上げろ！帆を張れ！ | Weigh anchor! Hoist the sails! | Angkat jangkar! Kembangkan layar! | joy |
| `tema.pelabuhan.lighthouse` | elder | この灯が消えたら、海は人を帰さぬ。 | If this light goes out, the sea won't send anyone home. | Kalau pelita ini padam, laut tak akan memulangkan siapa pun. | calm |
| `tema.pelabuhan.pirates` | m1 | 海賊だ！桟橋を守れ！ | Pirates! Defend the piers! | Bajak laut! Pertahankan dermaga! | angry |
| `tema.pelabuhan.black_shark` | m3 | この港の灯は、俺がもらう。 | This harbour's light is mine to take. | Pelita pelabuhan ini akan kuambil. | angry |
| `tema.pelabuhan.sea_crossing` | m2 | 霧の向こうは果てなき海だ。しっかり掴まってな。 | Beyond the fog lies the Boundless Sea. Hold on tight. | Di balik kabut ada Laut Tanpa Batas. Pegangan yang kuat. | normal |
| `tema.pelabuhan.goddess_day` | elder | 海の女神に祈りを。今年も無事の航海を。 | Pray to the Sea Goddess, for another year of safe voyages. | Berdoalah pada Dewi Laut, demi pelayaran selamat setahun lagi. | calm |
| `tema.pelabuhan.shipwright` | elder | 良い船は、竜骨から嘘をつかん。 | A good ship never lies, starting from its keel. | Kapal yang baik tak pernah berbohong, mulai dari lunasnya. | calm |
| `tema.pelabuhan.lamp_lit` | elder | 灯がともった…おかえり、みんな。 | The light is lit... welcome home, everyone. | Pelita menyala... selamat pulang, semuanya. | joy |
| `tema.oasis.welcome` | m1 | 玉門を越えた先は砂の海だ。水は持ったか？ | Beyond the Jade Gate lies a sea of sand. Got your water? | Di balik Gerbang Giok ada lautan pasir. Sudah bawa air? | normal |
| `tema.oasis.bazaar` | m3 | さあさあ、西の香辛料に絨毯だ！値段は相談次第！ | Come, come! Western spices and carpets! The price is open to talk! | Ayo, ayo! Rempah Barat dan permadani! Harga bisa dirunding! | joy |
| `tema.oasis.haggle_no` | m3 | それじゃ商売にならんよ、旦那。 | That won't make me a living, sir. | Kalau segitu, aku tak bisa makan, Tuan. | angry |
| `tema.oasis.haggle_yes` | m3 | ええい、持ってけ！今日だけだぞ！ | Fine, take it! Only today! | Ah, ambillah! Hanya hari ini! | joy |
| `tema.oasis.caravan` | m1 | 隊商が着いたぞ！駱駝に水をやれ！ | The caravan has arrived! Water the camels! | Kafilah sudah tiba! Beri minum unta-unta! | joy |
| `tema.oasis.sandstorm` | m1 | 砂嵐だ！駱駝を伏せさせろ、中へ入れ！ | Sandstorm! Make the camels kneel, get inside! | Badai pasir! Suruh unta berlutut, masuk ke dalam! | angry |
| `tema.oasis.water_priestess` | f3 | 水は命。一滴たりとも無駄にしてはなりません。 | Water is life. Not one drop must be wasted. | Air adalah kehidupan. Tak satu tetes pun boleh terbuang. | calm |
| `tema.oasis.lord` | boss | この町が砂に呑まれる日は来させぬ。 | I will not let the day come when sand swallows this town. | Takkan kubiarkan datang hari pasir menelan kota ini. | calm |
| `tema.oasis.scorpion` | demon | 水は我が蠍のもの。砂に還れ！ | The water belongs to my scorpion. Return to the sand! | Air ini milik kalajengkingku. Kembalilah ke pasir! | angry |
| `tema.oasis.dance` | f1 | 今宵は満月、旋舞をご覧あれ！ | Tonight is the full moon. Behold the whirling dance! | Malam ini purnama, saksikanlah tari putar! | joy |
| `tema.air.welcome` | f1 | 舟に乗っていくかい？町を一回りしてあげるよ。 | Want a boat ride? I'll take you round the town. | Mau naik perahu? Kuantar keliling kota. | joy |
| `tema.air.boat_song` | f1 | 水は流れて、橋はめぐる… | The water flows, the bridges pass by... | Air mengalir, jembatan silih berganti... | calm |
| `tema.air.silk` | f3 | 蘇家の絹は、五代にわたり嘘をついたことがありません。 | The silk of House Su has not lied in five generations. | Sutra keluarga Su tak pernah berbohong selama lima generasi. | calm |
| `tema.air.tea` | m1 | 一煎目は香りを、二煎目は心を味わうのです。 | The first steep is for the fragrance, the second for the heart. | Seduhan pertama untuk harumnya, seduhan kedua untuk hatinya. | calm |
| `tema.air.thief` | m2 | 泥棒だ！舟で逃げたぞ！ | Thief! He's escaping by boat! | Pencuri! Dia kabur naik perahu! | angry |
| `tema.air.lantern_night` | f1 | 灯籠を流しましょう。迷った魂が帰れるように。 | Let's float the lanterns, so the lost souls can find their way home. | Mari melepas lentera, agar arwah yang tersesat bisa pulang. | calm |
| `tema.air.ghost` | f2 | わたしの布は…まだ織りかけなの。 | My cloth... is still unfinished. | Kainku... masih belum selesai ditenun. | calm |
| `tema.air.ghost_rest` | f2 | ありがとう…これで、やっと眠れる。 | Thank you... now, at last, I can sleep. | Terima kasih... akhirnya aku bisa tidur. | calm |
| `tema.air.dragon_boat` | m1 | 太鼓を合わせろ！竜舟、進め！ | Match the drum! Dragon boat, forward! | Ikuti genderang! Perahu naga, maju! | joy |
| `tema.air.poet` | elder | 雨を聴く亭で、詩を一つ詠まぬか。 | Will you not compose a verse in the Pavilion for Listening to Rain? | Maukah kau menggubah satu bait di Paviliun Menyimak Hujan? | calm |
| `tema.gunung.welcome` | m1 | 千段の石段の始まりだ。足元に気をつけて。 | This is the start of the thousand stone steps. Watch your feet. | Inilah awal seribu anak tangga. Hati-hati langkahmu. | normal |
| `tema.gunung.porter` | m2 | 一歩ずつ、一歩ずつ。山は逃げやせん。 | One step at a time. The mountain isn't going anywhere. | Selangkah demi selangkah. Gunung tak akan lari. | calm |
| `tema.gunung.lift` | m1 | 乗った乗った！雲の上まで一息だ！ | All aboard! Up above the clouds in one breath! | Ayo naik! Ke atas awan dalam sekali napas! | joy |
| `tema.gunung.lift_stuck` | m1 | くそっ、綱が…！動くな、今助ける！ | Damn, the rope...! Don't move, I'm coming! | Sial, talinya...! Jangan bergerak, aku datang! | angry |
| `tema.gunung.monkey` | f1 | あっ、猿に饅頭を取られたね。果物を投げてごらん。 | Oh, the monkey took your bun. Try throwing it some fruit. | Wah, bakpaomu direbut kera. Coba lempar buah. | joy |
| `tema.gunung.rockfall` | m1 | 落石だ！道から離れろ！ | Falling rocks! Get off the path! | Batu runtuh! Menjauh dari jalan! | angry |
| `tema.gunung.abbot` | elder | 山頂に至る道は千あれど、心に至る道は一つ。 | A thousand paths reach the summit, but only one reaches the heart. | Seribu jalan menuju puncak, tetapi hanya satu menuju hati. | calm |
| `tema.gunung.pilgrim` | m2 | 三歩一拝、願いよ届け。 | Three steps, one bow. Let my wish be heard. | Tiga langkah satu sujud, semoga permohonanku sampai. | calm |
| `tema.gunung.buddha_light` | f3 | 見なさい、雲の上の光の輪を。仏光です。 | Look, the ring of light upon the clouds. The Buddha's light. | Lihatlah lingkaran cahaya di atas awan. Cahaya Buddha. | joy |
| `tema.gunung.cloud_bell` | elder | 雲の鐘が鳴る。山はあなたを覚えました。 | The cloud bell rings. The mountain remembers you. | Lonceng Awan berbunyi. Gunung kini mengingatmu. | calm |
| `tema.iblis.gate_toll` | m1 | 通るなら貢ぎ物を置いていけ。命よりは安いぞ。 | Leave a tribute if you'd pass. It's cheaper than your life. | Kalau mau lewat, tinggalkan upeti. Lebih murah dari nyawamu. | normal |
| `tema.iblis.gate_weak` | m1 | こんな小物、餌にもならん。帰れ。 | Small fry like you isn't even good bait. Go home. | Ikan teri sepertimu bahkan tak pantas jadi umpan. Pulang sana. | normal |
| `tema.iblis.pact` | m3 | 市場の中で血を流すな。それがこの町唯一の掟だ。 | Spill no blood inside the market. That is this town's only law. | Jangan tumpahkan darah di dalam pasar. Itulah satu-satunya hukum kota ini. | calm |
| `tema.iblis.hunt_hour` | demon | 夜道の通行料だ。金か、血か？ | A toll for the night road. Gold, or blood? | Upeti jalan malam. Uang, atau darah? | angry |
| `tema.iblis.arena_open` | m1 | 血の階段へようこそ！生きて降りられるかな？ | Welcome to the Blood Ladder! Think you'll walk down alive? | Selamat datang di Tangga Darah! Yakin bisa turun hidup-hidup? | joy |
| `tema.iblis.arena_win` | m1 | 見たか！新しい名が血に刻まれたぞ！ | Did you see?! A new name is carved in blood! | Kalian lihat?! Nama baru terukir dengan darah! | joy |
| `tema.iblis.broker` | f3 | 欲しいものは何？ここでは、値段のないものはないのよ。 | What do you desire? Here, nothing is without a price. | Apa yang kau inginkan? Di sini, tak ada yang tak berharga. | calm |
| `tema.iblis.forbidden` | elder | この書は、読む者の何かを喰らう。それでも開くか？ | This book devours something of whoever reads it. Will you still open it? | Kitab ini memakan sesuatu dari pembacanya. Masih mau membukanya? | calm |
| `tema.iblis.captive` | f1 | おねがい…村に帰りたいの。 | Please... I want to go home to my village. | Tolong... aku ingin pulang ke desaku. | calm |
| `tema.iblis.lord` | boss | 正道の者が、わざわざ地獄の門を叩くとはね。 | How rare, a righteous one knocking at hell's gate of their own accord. | Jarang sekali, orang jalan lurus mengetuk gerbang neraka atas kemauan sendiri. | calm |
| `tema.iblis.hunter` | f2 | 正道の名において、その首、貰い受ける！ | In the name of the righteous path, your head is forfeit! | Atas nama jalan lurus, kepalamu kuambil! | angry |
| `tema.iblis.raid` | m1 | 正道の連中が門に来た！迎え撃て！ | The righteous are at the gate! Meet them head on! | Orang-orang jalan lurus di gerbang! Hadapi mereka! | angry |
| `tema.iblis.dawn` | m3 | この町にも、朝は来るんだな。 | So even this town gets a morning. | Ternyata kota ini pun bisa didatangi pagi. | calm |
| `tema.dagang.sold` | m2 | 良い取引でした。また来てくださいよ。 | A fine deal. Do come again. | Transaksi yang bagus. Datang lagi, ya. | joy |
| `tema.dagang.saturated` | m2 | その品はもう溢れててね…安くしか買えんよ。 | We're already flooded with that... I can only buy it cheap. | Barang itu sudah membanjir... aku cuma bisa beli murah. | normal |

Total 56 baris. Gelembung kalimat tanpa suara (±250 kunci, `tempat_tema_kalimat`): seruan pasar ikan, tawar bazar,
lagu perahu (12 bait milik sendiri), keluhan kuli, bisikan pasar gelap, ejekan penantang Jam Berburu, doa peziarah.

### 38.T.13 Config, perintah dan layar

#### 38.T.13.1 Config (`config/kultivator-dunia.json`, `DuniaConfig.section`, dijepit saat dimuat)

Di teks bagian ini kunci ditulis `kotaTema.<tema>.<kunci>`; di berkas, `DuniaConfig.section` hanya satu tingkat
(verifikasi), jadi bagiannya `kotaTema` + `temaPelabuhan`, `temaOasis`, `temaAir`, `temaGunung`, `temaIblis` (tidak
bertabrakan dengan `kota`, `langit`, `sekteLangit`, `tempat`, `kotaBesar`, `kampung`, `kompleksSekte`, `gua`, `jalan`).

| Bagian | Kunci (bawaan, jepit) |
|---|---|
| `kotaTema` | `aktif` true, `worldgen` true, `tubuhMaks` 20 (12-36), `mekanismeMaks` 12 (4-16), `jarakDariIbuKota` 1200 (800-3000), `jarakAntarTema` 900 (600-3000), `dagang` true, `batasLabaMingguan` 150 (50-400, dikali band + 1), `jenuhPerUnit` 0.04 (0.02-0.08), `acara` true, `bahaya` true, `alurCerita` true, `kontrakMuatan` true |
| `temaPelabuhan` | `worldgen` true, `peluangSel` 0.7, `ukuranSel` 3072, `kapalDagang` true, `lintasLaut` true, `serbuanBajakLaut` true, `galangan` true |
| `temaOasis` | `worldgen` true, `peluangSel` 0.7, `ukuranSel` 3584, `badaiPasir` true, `badaiHariMin` 4, `badaiHariMaks` 8, `kafilahUnta` true, `reruntuhan` true |
| `temaAir` | `worldgen` true, `peluangSel` 0.5, `ukuranSel` 3072, `perahuPos` true, `lenteraAir` true, `perahuNaga` true, `tikusAir` true |
| `temaGunung` | `worldgen` true, `peluangSel` 0.6, `ukuranSel` 3072, `keretaGantung` true, `longsor` true, `keraGunung` true, `cahayaBudha` true |
| `temaIblis` | `worldgen` true, `peluangSel` 0.45, `ukuranSel` 4096, `jamBerburu` true, `penyerbuanLurus` true, `pemburuLurus` true, `infami` true, `arenaEv` 0.90 (0.80-0.92), `taruhanEv` 0.90 (0.80-0.92) |

Aturan 38.0 butir 17: item, entitas, payload, struktur dan placement tema **selalu** didaftarkan; saklar hanya
mematikan perilaku dan pack worldgen (`worldgen` false → rencana tema menjawab "tidak layak", tidak pernah setengah).

#### 38.T.13.2 Perintah

Uji di bawah `/kultivasi uji tema` (op, `DuniaCommands.test("tema", ...)`; chip `tema` ditambah 38W ke `CHIPS`):

| Sub-perintah | Arti |
|---|---|
| `rencana [tema]` | daftar kandidat tiap tema (cincin pertama, sel), status layak/tidak dan **alasannya** (air kurang, selisih, bioma), jarak dari pusat |
| `putusan <tema>` | nilai putusan di posisi pemain seolah kandidat di sini (sampel, arah laut/menanjak, `dasarY`, `dy` per lot) |
| `cari <tema> [indeks]` | locate lalu teleport (`DuniaCommands.findAndTeleport`) ke kota tema; `cari reruntuhan` / `cari pos_pemburu` |
| `distrik <tema> <id>` | teleport ke pusat distrik di kota tema terdekat |
| `lot` | lot di bawah pemain: templat, rotasi, `dy`, kode sisi keempat arah, jumlah titik per jenis |
| `cek` | validasi sisi server di kota sekitar: kode sisi bersambung, kanal tidak buntu, jangkar ada, mekanisme hidup terdaftar |
| `acara <tema> <acara> [mulai|akhir]` | `kapal_datang`, `kapal_pergi`, `dewi_laut`, `bajak_laut`, `kafilah_agung`, `badai_pasir`, `tari_putar`, `lentera_air`, `perahu_naga`, `tikus_air`, `ziarah`, `cahaya_budha`, `longsor`, `lift_macet`, `pesta_darah`, `lelang_gelap`, `penyerbuan`, `jam_berburu` (cuaca dan waktu dunia **tidak** diubah) |
| `kapal [tambatan]` / `perahu [jalur]` / `lift [naik|turun|macet|pulih]` / `unta [sewa|kafilah]` | memunculkan/menjalankan mekanisme terdekat (hanya bila pemain di kota itu) |
| `arena <peringkat>` / `taruhan` | mulai laga uji / taruhan uji |
| `infami <0-100>` / `gentar <0-100>` | setel meter pemain (uji) |
| `buronan [daftar|buat]` / `pemburu` | daftar buronan iblis aktif / buat satu di dekat pemain / pratinjau cegatan Pemburu Lurus (lewat `Sibuk`) |
| `dagang [harga|jual <barang> <n>|batas|jenuh|simulasi <hari>]` | harga semua kota, simulasi jual, sisa batas laba, keadaan jenuh, simulasi pedagang aktif N hari (laba harus ≤ batas) |
| `kontrak [buat|selesai|gagal]` | kontrak muatan uji |
| `alur <tema> <bab>` | lompat ke bab rantai quest kota tema (adegan tampil) |
| `model` | barisan semua entitas tema di depan pemain (AI mati, 30 s; 38TV `pamer`) |
| `fx <badai|lentera|cahaya|awan|suar|riak|ombak>` | pratinjau efek klien 20 s |
| `kepadatan [radius]` | kepadatan tema per km² dari rencana (tanpa membuat chunk), ikut dihitung `uji tempat kepadatan` |

Perintah pemain (tanpa op): `/kultivasi dagang` (Buku Dagang, sama dengan kartu hub).

#### 38.T.13.3 Layar baru (38TV, kit "Pasar Giok" 38.U; lembar dibangun 38TK, `TempatLayar.daftar`)

| Layar | Isi | Keadaan khusus |
|---|---|---|
| `tema_tawar` | tawar-menawar bazar (wireframe di bawah) | pedagang menolak, suasana rendah |
| `tema_dagang` (Buku Dagang) | tabel barang × kota yang diketahui (harga terakhir dilihat, umur data, jenuh), laba minggu ini vs batas, kontrak aktif | belum ada kota tema diketahui |
| `tema_kapal` | muatan kapal tambat (Pratinjau3D barang), asal kapal, jadwal berangkat; tab Kapal Lintas Laut | badai, kapal belum datang |
| `tema_galangan` | pesan lunas, tahap pembangunan (Pratinjau3D `kapal_galangan` tahap kini), ganti layar | pita kurang |
| `tema_kafilah` | jadwal kafilah, ikut sebagai penumpang/pengawal, sewa unta | badai, kafilah sudah berangkat |
| `tema_mural` | 12 kisah mural (gambar + teks), pesan mural | — |
| `tema_sutra` / `tema_teh` / `tema_bait` | toko sutra + pesanan jubah (Pratinjau3D jubah di boneka), menu teh + upacara, pasangan bait | efek teh aktif |
| `tema_herba` / `tema_ziarah` | toko & penilaian herba, peringkat pemetik / kemajuan 9 bordes dan syarat (tanpa terbang) | ziarah batal |
| `tema_lift` | jadwal kereta gantung, ongkos, posisi kabin di skema tali | macet, malam |
| `tema_pasar_gelap` / `tema_kitab_terlarang` / `tema_kontrak` | toko gelap (harga dengan markup dan Gentar/Infami), lantai menara terkunci per Gentar, papan kontrak | Penyusup (ditolak) |
| `tema_taruhan` | laga berikutnya, dua petarung (potret, tier, peluang, odds), taruhan | batas taruhan |
| `tema_tawanan` | daftar tawanan (nama, kampung asal, potret), [Tebus], [Tebusan Darah] | batas 3 per hari |
| `tema_dokumen` / `tema_racik` / `tema_irama` | mini-gim quest: cocokkan surat muatan, racik lensa 3 langkah, irama (tari putar, tenun, genderang perahu naga) | — |

```
tema_tawar (320 × 240)
+----------------------------------------------------------------------+
|⟦Tawar ⟧|  ┌──────┐  Pedagang Rahim · Kios Rempah     Suasana ███░ 72 |
|⟦Kios  ⟧|  │potret│  "Saffron Barat, harga teman: 52 NB!"             |
|        |  └──────┘                                                   |
|        |  Barang: Rempah Barat ×4   ╭──────╮  Harga dasar ≈ ? NB      |
|        |                            │  3D  │  Putaran 1/3            |
|        |  Tawaranmu:  (−)  44 NB  (+)  ╰──────╯                       |
|        |  [Tawar]   [Terima 52 NB]   [Pergi]        Uangmu 310 NB     |
+----------------------------------------------------------------------+

tema_dagang (Buku Dagang, 320 × 240)
+----------------------------------------------------------------------+
|⟦Harga ⟧| Barang        Pelab.   Oasis    Air     Gunung   Ibu kota  |
|⟦Laba  ⟧| Sutra Awan    46.8     48.6▲    36 ■    39.6     43.2      |
|⟦Kontrak⟧ Giok Barat    66       60 ■     78▲     69       75        |
|        | Dupa Gunung   6.5▲     6        5.8     5 ■      5.5       |
|        |  ■ = asal   ▲ = terbaik   abu = data > 3 hari               |
|        | Laba minggu ini: 212 / 450 NB  ▁▃▅▆ 4 hari                   |
+----------------------------------------------------------------------+
```

### 38.T.14 Modul dan kepemilikan berkas

#### 38.T.14.1 Keputusan

Lima tema **tidak** dimasukkan ke modul 38 yang ada (pekerjaan 38KS dan 38DS sudah penuh dan berkas mereka terpisah
per tempat), tetapi menjadi **dua modul baru**:

- **38TK — Kota Tema: dunia dan permainan** (server, data, worldgen, cerita data, ekonomi khas).
- **38TV — Kota Tema: model, efek klien dan layar** (semua yang digambar).

Modul inti 38 hanya menerima **baris tambahan kecil di berkas yang sudah mereka miliki** (38.T.14.3), dikerjakan dalam
langkah 2 mereka. 38TK dan 38TV berjalan di **langkah 2b**, setelah sepuluh modul inti selesai langkah 2, sehingga
**tidak pernah lebih dari sepuluh modul bekerja bersamaan** (batas 38 tetap terjaga; total modul 38 menjadi dua belas,
disebut). Tidak ada berkas yang punya dua pemilik.

#### 38.T.14.2 Berkas baru (milik penuh)

| Modul | Java | Generator, data, aset, lang |
|---|---|---|
| **38TK** | `dunia/tempat/tema/**`: `KotaTema` (init, satu baris di `Tempat.init()` yang sudah disiapkan 38W sebagai komentar), `Tema`, `TemaApi` (stub dibuat 38W di langkah 1, milik 38TK sejak 2b), `TemaConfig`, `TemaCommands`, `TemaNet` (payload S2C `kultivator:tema_fx`: keadaan badai, festival, spline lentera/perahu), `RencanaKotaTema`, `PutusanTema`, `KotaTemaNames`, `KotaTemaData` (SavedData `kultivator:kota_tema`: pesanan lunas/jubah/mural, titipan kuli, peringkat arena, Infami, Gentar, tawanan, buronan, data dagang dan jenuh, kontrak, Kas Kota Iblis, keadaan quest dunia), `TemaAnchors`, `TemaEntities`, `TemaItems`, `TemaKalender`, `TemaSitus` (provider `SpiritSites`), `TemaSibuk`; `tema/pelabuhan/**` (`KapalDagangEntity`, `KapalBajakLautEntity`, `PerahuNelayanEntity`, `ApiSuarEntity`, `DerekDermagaEntity`, `KapalGalanganEntity`, `JadwalKapal`, `LintasLaut`, `Galangan`, `HariDewiLaut`, `SerbuanBajakLaut`); `tema/oasis/**` (`UntaKafilahEntity`, `KalajengkingRajaEntity`, `TimbaSumurEntity`, `TemuanPasirEntity`, `PintuPasirEntity`, `PilarRuneEntity`, `BadaiPasir`, `Tawar`, `KuilAir`, `Mural`, `KafilahUnta`, `TariPutar`, `BatuUkurAirEntity`); `tema/air/**` (`PerahuKanalEntity`, `PerahuNagaEntity`, `AlatTenunEntity`, `JalurKanal`, `RumahSutra`, `PaviliunTeh`, `LenteraAir`, `LombaPerahuNaga`, `TikusAir`, `PasanganBait`); `tema/gunung/**` (`KeretaGantungEntity`, `KatrolEntity`, `RodaKatrolEntity`, `BatuRuntuhEntity`, `KeraGunungEntity`, `HerbaTebingEntity`, `PalangEntity`, `TaliTurunEntity`, `JeratEntity`, `Ziarah`, `JasaKuli`, `AulaHerba`, `CahayaBudha`, `Longsor`); `tema/iblis/**` (`GerbangTengkorakEntity`, `PintuArenaEntity`, `SelTawananEntity`, `SegelPintuEntity`, `ArenaDarah`, `Taruhan`, `PasarGelapAgung`, `KandangTawanan`, `MenaraTerlarang`, `GulunganTerlarangItem`, `SarangKontrak`, `JamBerburu`, `Gentar`, `Infami`, `PemburuLurus`, `PenyerbuanLurus`, `PestaDarah`, `BuronanIblis`); `tema/bersama/**` (`TanduEntity`, `PintuAirEntity`, `Dagang`, `KontrakMuatan`, `PetiDagangItem`, `DagangItems`, `TemaLayar` (pembangun lembar), `TemaCerita` (syarat/akibat/tujuan quest tema)) | `tools/tempat_tema_lib.py`, `tools/gen_tempat_tema_{pelabuhan,oasis,air,gunung,iblis,satelit,data}.py`; `data/kultivator/structure/kota_tema/**` (NBT), `worldgen/{structure,structure_set,template_pool,processor_list}/kota_tema/**`, tag `has_structure/kota_tema/**` dan `tags/worldgen/structure/kota_tema.json`, `resourcepacks/dunia_kotatema/**`; `data/kultivator/tempat/{dialog,quest,kabar}/tema_*.json`, `data/kultivator/tempat/{permintaan,jadwal}/kota_tema_*.json`, `data/kultivator/tempat/dagang/**`, `loot_table/tema/**`, `advancement/tempat/tema_*.json`; `lang-src/tempat_tema.*`, `tempat_tema_cerita.*`, `tempat_tema_lore.*`, `tempat_tema_kalimat.*`, `tempat_tema_panduan.*` |
| **38TV** | `client/dunia/tempat/tema/**`: `TemaClient` (init + penerima `TemaNet`, satu baris di `TempatClient` yang disiapkan 38W sebagai komentar), `model/**` (semua `*Model`/`*Renderer` entitas 38.T.9), `fx/**` (`BadaiPasirFx`, `LenteraAirFx`, `CahayaBudhaFx`, `LautAwanFx`, `KabutGunungFx`, `SinarSuarFx`, `RiakDarahFx`, `OmbakFx`, `TaliGantungRenderer`, `MuralBillboard`, `PendayungImpostor`), `TemaHub` (kartu "Kota Bertema", `HubCards.register`); `client/tempat/ui/tema/**` (semua layar 38.T.13.3, memakai kit `client/tempat/ui/kit/**` tanpa menyuntingnya) | `tools/gen_tempat_tema_{models,textures,ui}.py`; `assets/kultivator/{models/item,items,textures/item}/fx/tempat/tema/**` (prop 41-62), `{models/item,items,textures/item}/dagang/**` dan `.../tema/**` (item 38TK), `textures/entity/tempat/tema/**`, `textures/dunia/tempat/tema/**`, `textures/gui/tempat/tema/**`; `lang-src/tempat_tema_model.*`, `tempat_tema_ui.*` |

#### 38.T.14.3 Baris tambahan pada modul inti (berkas yang sudah mereka miliki; semua aditif)

| Modul | Tambahan | Berkas (sudah milik modul itu) |
|---|---|---|
| **38W** | `Rencana.simpul(...)` dan `Rencana.zonaAman(...)` (metode default, 38.T.2.1); `RencanaJalan` memakai `simpul` semua rencana; `TempatZones.safe` memakai `zonaAman`; `TempatApi.arrival` memakai jangkar `kota_tema/tiba` bila ada (tiba di luar Gerbang Tengkorak); stub `dunia/tempat/tema/TemaApi.java` + komentar baris init 38TK/38TV di langkah 1; tag `tempat_berpenghuni`, `tempat_dirawat` (+ `kota_tema/*`); `cek_tempat.py`: kode sisi, jenis titik baru, rentang salt 380401-380452, anggaran tema; penanda `DuniaMarkers` 42-49 + ikon di `gen_dunia_peta_textures.py`; bab 20 `GuideChapters`; chip `tema` di `DuniaCommands.CHIPS`; kepadatan tema di `uji tempat kepadatan` | `dunia/tempat/**` (38W), `tools/{cek_tempat,tempat_lib}.py`, `DuniaMarkers.java`, `GuideChapters.java`, `DuniaCommands.java`, tag `tempat_*` |
| **38NL** | peran `NpcRole` 38.T.3.3-38.T.7.3 di **akhir** enum setelah peran 38.N.2 (`SYAHBANDAR`, `PETUGAS_BEA`, `PELAUT`, `NAKHODA_LAUT`, `PENJAGA_SUAR`, `TUKANG_KAPAL`, `PENJUAL_IKAN`, `PEDAGANG_SEBERANG`, `PENDETA_LAUT`, `TUAN_BENTENG`, `KAFILAH_BASHI`, `PENUNTUN_UNTA`, `PEDAGANG_BAZAR`, `PENDETA_AIR`, `PELUKIS_GUA`, `PENARI_GURUN`, `PENGAWAL_GURUN`, `NYONYA_SUTRA`, `PENENUN_SUTRA`, `PEMELIHARA_ULAT`, `AHLI_TEH`, `PEMETIK_TEH`, `PENDAYUNG`, `SASTRAWAN`, `KEPALA_BIARA`, `BIKSU_GUNUNG`, `PENJAGA_LIFT`, `KULI_GUNUNG`, `PEMETIK_HERBA`, `PEZIARAH`, `PENGUASA_IBLIS`, `PENEGAK_IBLIS`, `JAGAL_ARENA`, `GLADIATOR_IBLIS`, `MAKELAR_GELAP`, `PENJAGA_KITAB_TERLARANG`, `PEMBUNUH_BAYARAN`, `WARGA_IBLIS`, `PEMBURU_LURUS`: 39 peran); `townsfolk()` memuat peran sipil damai (bukan peran iblis kecuali `WARGA_IBLIS` = tidak); `KlipKatalog` 79-107; `PropId` 41-62; `Jadwal` membaca data opsional `data/kultivator/tempat/jadwal/<jenis_situs>.json` (timpa per peran per jam, geser 12 jam untuk kota malam) | `dunia/kota/NpcRole.java`, `dunia/tempat/hidup/**`, `client/dunia/tempat/hidup/**` |
| **38MD** | perlengkapan 38.T.8.2 (pita 32-47; atlas 64 × 1536, pita 0-31 disalin byte demi byte) | `tools/gen_tempat_gear.py`, `client/dunia/kota/KotaGear.java`, `KotaGearLayer.java`, `perlengkapan.png` |
| **38ST** | 56 baris suara 38.T.12 di `gen_voices.py` + grup `tema` di `VoiceEvents`; pendaftaran syarat/akibat/tujuan quest dari modul lain: `Syarat.daftar(String id, ...)`, `Akibat.daftar(...)`, `QuestTracker.daftarTujuan(...)` (38TK mendaftarkan `infami`, `gentar`, `pita_dagang`, `naik_lift`, `ziarah_selesai`, `tebus`, `lentera_dilepas`, `kapal_lintas`, dst.) | `tools/gen_voices.py`, `voice/VoiceEvents.java`, `dunia/tempat/cerita/**` |
| **38EK** | `EkonomiApi.daftarJenisMisi(JenisMisi)`; `KafilahSekte` membaca semua `data/kultivator/tempat/permintaan/*.json` dan `TemaApi.rupaKafilah`; `KafilahJalan` meneruskan rupa ke 38TK (tubuh unta/kapal dimunculkan 38TK lewat `TemaApi`); `ProfilLelang`/`BalaiLelang.mulai` publik (sudah direncanakan) dengan `tabelLot` dari data | `dunia/tempat/ekonomi/**`, `dunia/kota/BalaiLelang.java` |
| **38KS** | `Towns` menerima `kota/kota_tema` (SAFE_RADIUS 160); jenis tujuan teleport dan Perjalanan Awan + `kota_tema/*` | `dunia/kota/Towns.java`, `dunia/tempat/kotabesar/Teleport.java` |
| **38PR** | faktor tempat dan kekuatan 38.T.11; gaya tagihan `iblis` (penagih, Harga Darah, akibat tolak lewat `TemaApi`); air kanal di kotak `kanal` boleh dipulihkan | `dunia/tempat/perbaikan/**` |
| 38UI, 38DS, 38GU | tidak ada | — |

#### 38.T.14.4 Kontrak tema

- **C38.T1 `TemaApi`** (38TK; stub 38W langkah 1 dengan nilai bawaan aman): `@Nullable String temaDi(ServerLevel,
  BlockPos)`, `String rupaKafilah(long situsAsal, long situsTujuan)` (bawaan `"GEROBAK"`), `double permintaan(long
  situsId, Item)` (bawaan 1.0), `int infami(ServerPlayer)` (0), `void tambahInfami(ServerPlayer, int, String alasan)`,
  `int gentar(ServerPlayer, long situsId)` (0), `boolean zonaPerjanjian(ServerLevel, BlockPos)` (false), `void
  tolakTagihanIblis(ServerPlayer, long situsId)` (dipanggil 38PR), `@Nullable Entity tubuhKafilah(ServerLevel, String
  rupa, Vec3 pos, float yaw)` (dipanggil 38EK).
- **C38.T2 `TemaEntities`** (38TK): semua `EntityType` 38.T.9 didaftarkan di awal langkah 2b (stub tipe dulu, pola 38.E.0
  langkah 1) agar 38TV bisa mendaftarkan renderer sejak awal; 38TV tidak mengubah atribut atau perilaku.
- **C38.T3 lembar layar**: setiap layar `tema_*` punya kunci lembar yang ditulis di javadoc pembangun lembar `TemaLayar`
  (38TK); 38TV hanya membaca.
- **C38.T4 `TemaNet`** (38TK): payload `kultivator:tema_fx` `(int jenis, long situsId, CompoundTag data)` dengan jenis
  `BADAI` (mulai, akhir, pusat, jari-jari), `FESTIVAL`, `SPLINE` (titik kanal sekali per kota), `CAHAYA`, `KAPAL_JADWAL`;
  ≤ 8 KB per paket.
- Peristiwa lintas modul memakai `TempatEvents` 38W: `ACARA(level, situsId, "tema.<acara>", mulai)`, `QUEST(player, id,
  tahap)`.

#### 38.T.14.5 Urutan kerja

1. **Langkah 1 (38W):** stub `TemaApi`, komentar baris init, metode default `Rencana`, tag, aturan `cek_tempat.py`.
2. **Langkah 2 (sepuluh modul inti):** termasuk baris tambahan 38.T.14.3.
3. **Langkah 2b (38TK + 38TV paralel):** 38TK mendaratkan `TemaEntities` + `TemaNet` + `TemaLayar` (tanda tangan) dulu,
   lalu keduanya bekerja paralel; masing-masing menjaga `docs/progres/kota-TK.md` / `kota-TV.md` (checklist + langkah
   berikutnya, aturan progres pengguna).
4. **Langkah 3 (finish 38):** generator Python tema (NBT, JSON, tekstur, model, UI, suara VOICEVOX), `cek_tempat.py`,
   harness `scratchpad/final/wg/run_all.sh` dengan 46 + 2 struktur tema, `mergeLang`, lalu satu langkah Gradle
   (`compileJava`, `compileClientJava`, `jar`, `--no-daemon`). Tidak ada tes, `runClient` atau `gradlew build`.

### 38.T.15 Uji terima dan daftar uji

#### 38.T.15.1 Bukti konten nyata (tambahan 38.E.4)

| Fitur | Bukti yang harus terlihat | Cara cepat |
|---|---|---|
| Rencana | tiap tema punya kandidat; empat tema damai punya kota pertama layak di 1400-4200 (atau alasan jelas), Kota Iblis di 3500-6000 | `uji tema rencana` |
| Pelabuhan | sepertiga kota di atas air di tiang, tembok laut, mercusuar di ujung pemecah ombak, gaya bata merah ekor walet | `uji tema cari pelabuhan`, terbang keliling |
| Kapal dagang | kapal datang berlayar, layar digulung, jangkar turun, kuli bongkar muatan, derek berputar; geladak bisa dipijak; berangkat dan memudar | `uji tema acara pelabuhan kapal_datang` |
| Laut | Kapal Lintas Laut membawa pemain ke dermaga Laut Tanpa Batas dan Gerbang Pulang kembali ke pelabuhan | layar `tema_kapal` |
| Oasis | danau bulan sabit, bazar berkain, Tebing Seribu Lukisan, karavanserai, Gerbang Giok | `uji tema cari oasis` |
| Kafilah unta | unta Baktria berjalan pace beriring bertali, berlutut saat badai | `uji tema acara oasis kafilah_agung`, `... badai_pasir` |
| Badai pasir | dinding pasir datang, kabut, NPC berlindung, gerbang menutup, cuaca dunia **tidak** berubah, Temuan Pasir sesudahnya | `uji tema acara oasis badai_pasir mulai` |
| Kota Air | kanal bersambung tanpa buntu, Jembatan Kembar (lengkung bundar + persegi), Pintu Air, taman klasik | `uji tema cek`, `uji tema cari air` |
| Perahu | Perahu Pos membawa pemain antar-halte dengan pendayung beranimasi yuloh; perahu naga berlomba | `uji tema perahu`, `uji tema acara air perahu_naga` |
| Lentera air | lentera hanyut sepanjang kanal ke danau, harapan tampil hanya untuk pemiliknya | `uji tema acara air lentera_air` |
| Kota Gunung | tiga baris teras naik, tangga dengan gapura per bordes, rumah panggung di tepi, Aula Emas di puncak | `uji tema cari gunung` |
| Kereta gantung / katrol | kabin naik di tali (kurva gantung), berayun, roda stasiun berputar; katrol diputar penjaga | `uji tema lift naik` |
| Ziarah dan Cahaya Budha | 9 bordes berasap, Restu Puncak; cincin cahaya di awan dengan bayangan pemain | `uji tema fx cahaya`, ziarah manual |
| Kota Iblis | Gerbang Tengkorak (rahang membuka), arena berlubang bertribun penuh impostor, menara sembilan lantai, kandang | `uji tema cari iblis` |
| Arena darah | laga melawan gladiator bernama; pemain dihentikan di 5 % HP, tidak mati; taruhan EV 0.90 | `uji tema arena 3`, `uji tema taruhan` |
| Tawanan | tebus → tawanan mengikuti dan diantar pulang; tidak ada opsi memiliki tawanan | layar `tema_tawanan` |
| Pemburu lurus | buronan iblis bernama di papan; Infami 45 → regu pemburu mencegat di luar tempat aman dengan dialog | `uji tema infami 45`, `uji tema pemburu` |
| Dagang | beli sutra di Kota Air, jual di Oasis dengan laba; jenuh menurunkan harga; batas laba berlaku | `uji tema dagang simulasi 7` |
| Model dan klip | semua entitas tema dan klip 79-107 tampil tanpa patah, prop 41-62 di tangan | `uji tema model`, `uji hidup klip semua` |
| Cerita | lima rantai quest dapat dilompati per bab dengan adegan; cabang menghasilkan akhir berbeda | `uji tema alur <tema> <bab>` |
| Suara | 56 baris tema terdengar (Jepang) dengan subtitel ID/EN | interaksi |

#### 38.T.15.2 Daftar uji (Indonesia, ditambahkan ke `docs/uji/FASE-38.md` sebagai bagian 38.T)

1. Dunia baru: `uji tema rencana` menampilkan lima tema; empat tema damai layak di cincin pertama (atau alasan); `uji tema
   cari <tema>` membawa ke setiap kota; semua distrik lengkap, tidak ada lot hilang, tidak ada struktur lain di zona.
2. Pelabuhan: dermaga di atas laut berdiri di tiang sampai dasar; air laut tetap di sekitar tiang (tidak ada kantong udara);
   rotasi kota menghadap laut.
3. Kapal dagang datang pukul 08:00-10:00 (tick 2000-4000) hari siklusnya, tambat, bongkar, lalu berangkat; restart server di tengah tambat:
   kapal muncul lagi di tambatannya (dari jangkar + data siklus).
4. Kapal Lintas Laut (pita Fondasi): adegan berlayar, tiba di dermaga Laut Tanpa Batas; Gerbang Pulang kembali ke dermaga
   pelabuhan; murid (≤ 2) ikut.
5. Serbuan Bajak Laut: kapal hitam datang, band menyerbu dermaga, tidak ada blok rusak oleh bajak laut; menang → hadiah Kas
   Kota.
6. Oasis: tawar di bazar tiga putaran; harga akhir ≥ 0.90 dasar; tawaran terlalu rendah membuat pedagang ketus.
7. Badai pasir: di luar tembok tanpa Restu Mata Air → Lambat I; di dalam tembok aman; `isRaining/isThundering` dunia tidak
   berubah; sesudahnya 2-4 Temuan Pasir.
8. Kafilah Agung: unta beriring masuk Gerbang Giok, berlutut di karavanserai; ikut kafilah sebagai penumpang; sewa unta,
   tinggalkan 2 menit → unta pulang.
9. Reruntuhan gurun: Lempeng Peta menunjuk; Raja Kalajengking menggali (tanpa blok berubah) dan muncul; hadiah sekali.
10. Kota Air: semua kanal bersambung (`uji tema cek`); Perahu Pos melewati 4 halte; Tikus Air dikejar dengan perahu
    sewaan; barang kembali.
11. Malam Lentera Air (bulan mati): warga melepas lentera; pemain menulis harapan; pemain kedua tidak melihat teks
    harapan pemain pertama.
12. Lomba Perahu Naga sebagai penabuh: ketepatan irama mempercepat perahu; juara mendapat hadiah.
13. Kota Gunung: kereta gantung naik dua bentang lewat tiang tengah; badai petir menghentikannya; Lift Macet quest: tali
    turun, pemain turun selamat.
14. Ziarah: terbang sekali di tengah → batal; ulangi tanpa terbang → Restu Puncak; meditasi di teras Aula Emas mendapat
    faktor ekstra (dalam CAP).
15. Kera gunung merebut bakpao di tangan, tidak pernah barang lain; lempar buah → barang jatuh.
16. Longsor saat hujan: batu menggelinding dan terpental di pagar; tidak ada blok berubah.
17. Kota Iblis: pita Pemurnian Qi ditolak di gerbang; pita Fondasi masuk dengan kartu merah; upeti dibayar; terbang
    masuk tanpa upeti dan terlihat → penegak bermusuhan.
18. Zona Perjanjian: menyerang NPC di pasar → semua penegak memburu; di luar ZP pada malam hari → Jam Berburu (bayar atau
    lawan) maksimal sekali semalam.
19. Arena: lima laga, kalah satu kali → pemain dihentikan di 5 % HP dan hidup; taruhan 1000 kali simulasi → EV 0.88-0.92.
20. Tawanan: tebus satu, kawal ke kampung asal → Nama Baik +3 kampung, Infami −5; tidak ada pilihan memperbudak.
21. Menara Kitab Terlarang: lantai terkunci per Gentar; gulungan Bakar Darah bekerja dan menambah Infami.
22. Infami ≥ 40: regu Pemburu Lurus mencegat di jalan (tidak di dalam tempat aman); "Menyerah dan menebus" menurunkan
    Infami 20; buronan iblis bernama dari papan bisa ditemukan di jalan dan memberi standing sekte Lurus +3.
23. Penyerbuan Lurus: pilih pihak; hasil berbeda (kandang kosong / upeti naik); gerbang = entitas, tidak ada blok runtuh.
24. Dagang: `uji tema dagang simulasi 7` pada pita 3: laba per minggu ≤ `150 × 4 = 600` NB; jenuh terlihat setelah 10 unit;
    selundupan disita 40 % di bea.
25. Kontrak muatan dengan kapal terbang ke ibu kota: bayaran × 1.3; peti dibuka di jalan → gagal, deposit hilang.
26. Lima rantai quest: setiap bab bisa dilompati (`uji tema alur`), setiap cabang utama menghasilkan akhir yang berbeda
    (pelabuhan bab 6 a/b/c, oasis bab 5 a/b/c, air bab 4, gunung bab 4-5, iblis tiga rute).
27. Perbaikan: rusak dermaga dan kanal (`uji tempat rusak 30 pemain`) → tagihan dari penagih kota; air kanal dipulihkan;
    di Kota Iblis tagihan = Harga Darah, tolak → penegak bermusuhan 1 hari.
28. Peta dan hub: penanda 42-49 muncul setelah kota ditemukan; kartu "Kota Bertema" menampilkan Infami, Gentar dan Buku
    Dagang; Kitab Panduan bab 20 mencentang langkah.
29. Performa: di tengah setiap kota tema, `uji tempat ukur` bagian `tema` ≤ 0.6 ms/tick; Efek Minimal: tanpa partikel
    badai, lentera air, cincin cahaya; entitas mekanisme ≤ 12 per kota.
30. Keamanan: grep finish: tidak ada `setBlock`, `LightningBolt`, `setWeatherParameters`, `Explosion`, `FallingBlockEntity`
    baru di `dunia/tempat/tema/**`; semua mekanisme adalah entitas `noSave`.
