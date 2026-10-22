## 37. Sekte Langit (Sekte di Atas Awan)

Permintaan pengguna (2026-10-22, bahasa Indonesia, mengikat): "Bisakah dibuat sekte yang berada di atas langit?" lalu
"Silakan, tapi buat idenya DETAIL."

Bagian ini adalah kontrak implementasi lengkap untuk empat modul (37B, 37M, 37V, 37G; 37.9). Semua nama kelas, field,
metode, id komponen, kunci lang, perintah dan angka di bawah adalah nama yang **harus** dipakai. Yang ditandai
"(verifikasi)" sudah dicek polanya di kode tetapi nama persisnya wajib dicocokkan lagi dengan
`butterbot-fabric/reference/mcsrc` atau kode mod sebelum dipakai. Tidak ada angka di sini yang berasal dari novel atau
mitos; semua angka adalah desain game (baris Sumber hanya menjelaskan ilham). Bagian ini berdiri sendiri di
`docs/rancangan/37-sekte-langit.md`; `docs/DESIGN.md` tidak diubah oleh bagian ini (hanya satu baris rujukan boleh
ditambahkan oleh pemimpin proyek nanti, bukan oleh modul).

---------------------------------------------------------------------------------------------------------------------

### 37.0 Ruang lingkup dan aturan mengikat

1. **Keamanan blok (DESIGN 20.1, 21.0, 36.0).** Blok dunia berubah **hanya** lewat `kehancuran/WorldBreaker` dan
   `sect/build/engine/BuildPlacer`. Pulau langit, pulau satelit, jembatan giok, tangga awan, dermaga, gerbang, pagoda:
   semuanya komponen templat yang dibangun `BuildPlacer` dari bahan yang **disetor** ke Gudang Bahan Sekte, persis
   seperti T1-T9 darat. Tidak ada `setBlock` lain, tidak ada `Explosion`, tidak ada `LightningBolt` buatan kita, tidak ada
   blok gravitasi yang dibangunkan, tidak ada air yang mengalir keluar pulau.
2. **Sekte langit NPC hanya worldgen** di chunk baru (`tools/nbt.py` + `tools/structgen.py` + generator baru
   `tools/gen_sekte_langit.py`), lewat paket bawaan (pola `GerbangSekte`/`DuniaPacks`). Kode runtime tidak pernah
   menaruh blok strukturnya.
3. **Semua yang bergerak adalah entitas atau gambar klien**: air terjun di tepi pulau (pita quad + partikel kabut),
   laut awan, bangau, kelopak, kubah formasi, sungai bintang, feri awan (entitas tanpa fisika blok, pola
   `KapalRohEntity`), pijakan awan di kaki tangga (entitas kolider diam, pola `KapalDekEntity`), perlindungan jatuh (efek
   `SLOW_FALLING` + penangkapan teleport, tanpa blok).
4. **Batas tinggi dunia 26.3**: Overworld `min_y = -64`, `height = 384` → `getMaxY() = 319`
   (`net/minecraft/data/worldgen/DimensionTypes.java:47-56`); `Validator.world` sudah menolak footprint di atas
   `getMaxY() - 2`. Awan vanilla ada di atribut lingkungan `EnvironmentAttributes.CLOUD_HEIGHT = 192.33F`
   (`DimensionTypes.java:38`, `world/attribute/EnvironmentAttributes.java:58`), bukan lagi field `DimensionType`.
   Sekte langit **hanya di Overworld** (aturan `Validator.world` "dimension" tetap). Alam Abadi, alam rahasia dan dimensi
   lain tidak pernah dibangun (aturan `BuildPlacer.place` yang ada).
5. **Tidak ada force-load chunk.** Satu-satunya tiket adalah `DuniaTickets.touch(ServerLevel, ChunkPos, radius ≤ 1)`
   (timeout 100 tick, `MAX_LIVE = 4`) untuk tiba di tujuan teleport/feri; pembangunan tetap hanya di chunk yang sudah
   dimuat (aturan 21.15).
6. **Server berwenang**; klien hanya menggambar. Efek diskalakan `EffectQuality`, `EpicBudget`, `FxBudget` dan
   pengaturan "Partikel" vanilla; aman untuk Sodium dan Iris (tanpa post chain, tanpa shader sendiri, hanya tipe render
   vanilla seperti `KosmikTypes`/`LangitSink`, tidak menggambar saat `ShaderCompat.shadowPass()`).
7. **Tak ada yang mati karena jatuh dari pulau sendiri.** Pemain, murid dan hewan peliharaan anggota sekte dilindungi
   `JaringAwan` (37.5.6). Penyerang (perampok, bajak awan) **tidak** dilindungi.
8. **Kompatibilitas simpanan**: dunia lama tanpa sekte langit tidak berubah sama sekali. Semua data baru ada di
   kunci/berkas baru (37.8.6). Mematikan `langit.aktif` menghentikan semua sistem langit tanpa menghapus data; blok yang
   sudah dibangun tetap blok milik sekte.
9. Lang hanya di fragmen 37.10 (`lang-src/blueprint_awan`, `sekte_langit_gerak`, `sekte_langit_visual`, `sekte_langit`,
   `sekte_langit_panduan`, masing-masing `.en_us.json` + `.id_id.json`) dengan kunci identik di kedua bahasa; tiap
   kunci tepat di satu fragmen (tugas `mergeLang` di `build.gradle` gagal bila ganda). Kode server tidak menyentuh kelas klien. Akhir baris tiap
   file dipertahankan (LF; `KultivatorClient.java` dan `musuh/SectRaids.java` CRLF).
10. Bangun hanya `compileJava`, `compileClientJava`, `jar` (`--no-daemon`), sekali setelah semua modul selesai; agen
    modul tidak menjalankan Gradle, tes, atau `runClient`.
11. Empat modul dengan file baru yang tidak bertumpuk dan **satu pemilik** untuk tiap file lama yang disunting (37.9).
    DESIGN 36 (Sekte yang Hidup) sedang dibangun paralel: modul 37 **tidak** menyunting file milik modul 36 selama
    `docs/progres/sekte-<X>.md` modul itu belum "DONE"; bila perlu, pakai hook atau tunda dan catat di file progres.

---------------------------------------------------------------------------------------------------------------------

### 37.1 Fakta dari kode (audit 2026-10-22)

**Pembangunan Sekte (DESIGN 21, paket `sect/build`)**
* `api/SectTemplates`: `COUNT = 9`, `TIERS = 9`, `IDS = {"unranked", ..., "heavenly_court"}`, `valid(int)` = 1..9,
  `id`, `byId`, `nameKey` (`kultivator.blueprint.template.<id>`), `tierKey`, `rotation(rot)`, `gate(rot)`, `rotFor`.
  Dipakai di 26 tempat (`client/sectbuild/TemplateScreen`, `TemplateExtensions`, `Templates`, `BuildEngine`,
  `PlanAudit`, `BuildCommands`, `BuildInfo`, `Founding`, `Upgrade`, `SectBuild`): keluarga baru wajib mengaudit semuanya.
* `blueprint/Templates.get(int)`: cache `new TemplateSpec[COUNT + 1]`, `switch` ke `T1Unranked.build()` ...
  `T9HeavenlyCourt.build()`; `ready(int)`.
* `blueprint/TemplateSpec` (+ `Builder`): `footprint(x0, x1, z0, z1)`, `envelope(above, below)`, `relief`, `earthwork`,
  `disc(d, r)`, `territory`, `towerRooms`, `qiTier`, `gateAltars`, `planVersion`, `zone(...)` (tetap atau `lo..hi`
  dipasang ke medan, induk + `minStep`), `keep(x, y, z)`, `add(Component)`; `column()` =
  `x0..x1 × (-1 - below)..(-1 + above) × z0..z1`; pos jaga (0,±5)/(±5,0) selalu udara; `build()` menjalankan
  `TemplateExtensions.apply` sekali. Koordinat lokal: prasasti (0,0,0), +z ke gerbang, +x timur, lantai plaza y = -1.
* `blueprint/TemplateExtensions.register(owner, template 1-9 | 0, Consumer<TemplateSpec.Builder>)`; **0 berarti "setiap
  templat"** dan dipakai `sekte/kebun/KebunKomponen` (`"sekte:kebun"`) dan `sekte/seragam/PanjiKomponen` (`"seragam"`).
  Templat langit **tidak boleh** menerima ekstensi 0 itu (koordinat plot darat), lihat 37.4.9.
* `blueprint/Component.of(id, tier).kind(..).name(..).group(Group.AXIS|MAIN|SIDE|BOUNDARY|GARDEN|ORNAMENT)`;
  `Part { bounds(Ctx); emit(Ctx, Sink); decorate(Ctx, Sink, tier); kind(); }`; `Lv.at(n)` / `Lv.zone(id[, off])`
  (berdiri di n, lantai n - 1); `Ctx.level(Lv)`, `Ctx.random(salt)`, `Ctx.compass(dir)`; `Phase` = `CLEAR, SEAL, CUT,
  SHELL, FILL, PLINTH, FRAME, ROOF, WALL, INTERIOR, ORNAMENT, GARDEN, WATER, TIDY`.
* Pustaka bagian yang dipakai ulang: `HangingIsland(x, z, r ≥ 3, Lv)` + `.tether(tx, tz, Lv)`, `.jadeChains`,
  `.turtle`, `.pad`, `.sporeBlossom` (puncak rumput/lumut, tetesan tuff/andesit/batu/kalsit sedalam
  `1 + round(t²·(1.5 + rand)·r)`, akar gantung 1:4, `Palette.RIDGE_LIGHT` di bawah turf, pinus `Tree.Kind.PINE` miring di
  tepi; tanpa air), `Waterfall(id, x, z, Lv top, Lv bottom, Direction)` (satu-satunya fluida yang di-tick, sumber
  `layer(10_000)`, jangkar `"waterfall_" + id`), `Pond`, `Bridge(Type, x0, z0, x1, z1, Lv)` dengan `Type {MOON_ARCH,
  FLAT, STRAIGHT_MARBLE, ZIGZAG, STEPPING, COVERED, SKY}`, `Paifang(x, z, Lv)`, `Pagoda(x, z, Lv)`, `Pavilion(x, z, Lv)`,
  `Hall(x, z)`, `Gates.innerGate/gateHall/gateTower/cornerTower`, `LanternPost(Style{STONE, RED_POST, HANGING, PALACE,
  JADE, PAGODA}, Lv)`, `QiSeat(x, z, Lv, tier)`, `FormationDisc(d, r, Lv)`, `TowerShaft(x, z, Lv entrance, rooms,
  firstRoomY)`, `Marks.anchor(id, x, z, Lv)`, `Marks.demolish(...)`, `Marks.petals(...)`, `Marks.block(x, y, z, state,
  phase)`, `Stamp.*` (singa, ding, genta, `flagPole`), `Tree(Kind{PINE, PLUM, WILLOW, GINKGO, MAPLE, BAMBOO}, x, z, Lv)`,
  `Rock.scholar/cluster/hill`, `templates/t4t6/Flight`, `templates/t4t6/Islet`, `templates/t7t9/Piers`.
* `Palette`: `PLINTH = POLISHED_ANDESITE`, `MARBLE_DECK = SMOOTH_QUARTZ`, `MARBLE_RAIL = PALE_OAK_FENCE`,
  `MARBLE_POST = QUARTZ_PILLAR`, `RIDGE_LIGHT = PEARLESCENT_FROGLIGHT`, `HIDDEN_LIGHT = OCHRE_FROGLIGHT`,
  `GILT_CAP = CHISELED_SULFUR`, `PLAQUE_FIELD = GILDED_BLACKSTONE`, `MOSS = MOSS_BLOCK`, `jadeBlock()`, `jadeSlab()`,
  `jadeStairs()`, `jadeChain()`, `ironChain()`, `concrete(DyeColor)`, `glazed(DyeColor)`, `stair(..)`, `slab(..)`.
* `engine/BuildPlacer.place(level, site, pos, target, phase, guard, index)`: menolak `!plan.inFootprint(pos)`,
  **`pos.equals(site.stele)` dan `pos.equals(site.stele.below())`**, dimensi alam/abadi, chunk tidak dimuat;
  `PLACE_FLAGS = UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE | UPDATE_SKIP_ON_PLACE`. Fluida disegel lebih dulu (`FluidGuard`).
* `engine/Validator.world(level, footprint, otherSites)`: dimensi Overworld, `height_low`/`height_high`, tumpang tindih
  `SITE_MARGIN = 16` (horizontal), spawn, penduduk desa (±32 y), struktur penting di footprint.
  `Validator.terrain(...)`: relief P95-P5, earthwork, air `WATER_LIMIT 0.30`/`DEEP_LIMIT 0.10`, **void**
  (`-1 - ground > 16` → amber > 5%, merah > 15%). Di langit setiap kolom adalah void, jadi keluarga langit butuh
  `Validator.sky` sendiri (37.4.8).
* `engine/Snapshot`: per kolom `ground` (tanah alami, `PROBE = 40` dari heightmap), `top` (blok non-udara tertinggi,
  heightmap `WORLD_SURFACE`), `water`, `flags`; `CHUNKS_PER_TICK = 2`, hanya `getChunkNow`.
* `engine/Site`: satu situs per pemilik di `HallData.build_site`; showcase dan "orphan" relokasi di SavedData
  `Showcase` (`kultivator:sect_showcases`, `MAX_SHOWCASES = 2`); `ledgerOwner()` = `refundTo ?? owner`.
  `BuildEngine.steleOk(site, level)`: aula aktif, `hall.pos() == site.stele`, blok `HallItems.SECT_HALL_STELE` dimuat.
  `BuildEngine.present(...)`: pemilik/rekan sekte dalam jarak **horizontal** `sectBuildRange` (160) dari `site.stele`.
* `engine/CellIndex` (`kultivator:sect_cells`): bitset per **seksi chunk 16³** dengan **satu** `UUID owner` per seksi;
  `setPlaced`, `setRemoved`, `owner`, `toLegacy`, `clearOwner`, `placedCount`.
* `flow/Relocation implements Halls.SteleMoveListener` (`MOVE_TICKS 1200`, `CONFIRM_TICKS 600`,
  `MOVE_GUARANTEE_DAYS 10`); `EngineApi.relocate(owner, newStele, deconstructOld)`, `beginUpgrade`, `release`,
  `deconstruct`, `testBuild(player, tier, speedMode)`, `showcase(...)`, `anchor(owner, id)`, `insideAnySite`.
* Bahan (`api/Bahan`, ordinal disimpan, tambah hanya di akhir): `BATU, TANAH, KAYU, GENTENG, KAPUR, PERNIS, FLORA, LOGAM,
  PERMATA`, centi (100 = 1 unit). `Costs.FULL 50`, `STAIR 38`, `SLAB 25`, `FILL 25` centi.
* Penjadwal (21.15): laju `(6 + 3(S-1)) × sectBuildSpeed × (1 + 0.25·pekerja) × (bank?2:1) × msptFactor` blok/detik,
  rata-rata keras 8 `setBlock`/tick, ≤ 256 cek/tick, jeda ≥ `sectBuildMsptPause` (40 ms), `instan` = 512 op/tick.

**Aula (DESIGN 20, `sect/hall/Halls`)**
* `Halls.inTerritory(owner, level, Vec3)`: lingkaran horizontal `hall.radius()` dan `|y - stele.y| ≤ TERRITORY_HEIGHT
  = 32.0`; `radiusOf = max(20 + 4L, R_t)`. `relocateHall(player, level, pos)` (aturan 128 blok dari aula lain, jeda
  pindah 1 hari), `SteleMoveListener {beforeMove, afterMove, onRemoved}`, `addArrivalSpotProvider`,
  `OTHER_HALL_RANGE 128`.
* `GrandFormation`: radius `min(32, 16 + 2L)`, `MAX_LAYERS 4`, `State(grand, radius, layers, damageFactor, guarded,
  guardName)`.

**Formasi (DESIGN 24, `formasi/**`)**
* `spec/Specs.spec(id, netId, family)` → `Builder.grade/scope/radius/pattern/effect/param/flag/color/fx`;
  `Family.PERPINDAHAN 0xD8C8FF`; `Grade`: `MISTERIUS` gerbang 3, `LANGIT` gerbang 7. Registrasi di `Formasi.init()`:
  `LegacyMirrors`, `CatalogA.register`, `FormasiB.init`, `FormationRegistry.freeze()`.
* Transportasi: `effect/b/TransportNetwork` (`M3 = "sect_transfer"` netId 450, `L5 = "ancient_transfer"` 451,
  `M3_COST 1.0`, `L5_COST_STEP 2.0` per `L5_STEP_BLOCKS 512`, `linked(level, from, to)`, `inside(level, core)` =
  `Halls.inTerritory`, `travel(player, fromId, toId[, instant])`), `TransportChannel` (`STAND_RANGE 1.5`,
  `M3_CHANNEL 60`, `L5_CHANNEL 100`, `HURT_LOCK_TICKS 200`, `QI_COST 10`, `MAX_DISCIPLES 4`, `DISCIPLE_RANGE 4`,
  `refusal(player, checkCooldown)`; tiba lewat `Targeting.safeSpot` + `teleportTo` + `resetFallDistance()`),
  `Stacking.TRANSPORT_M3_COOLDOWN 200`, `TRANSPORT_L5_COOLDOWN 600`. Simpul di tanah (|dy| > 32) **di luar wilayah**,
  jadi M3 tidak bisa menghubungkan tanah ↔ pulau; L5 bisa (pos luar ≤ `Limits.L5_OUTPOST_RANGE 4096`) tetapi gerbang 7.
* `effect/FormationEffect.pulse(CoreContext)`; `CoreContext.allies(max ≤ 16)`, `hostiles(max ≤ 24)`, `fx(..)`; pola
  efek `effect/a/HealEffect`. **Tidak ada formasi jatuh lambat atau levitasi.**

**Kultivasi**
* `cultivation/SpiritSites`: `CAP = 4.0`, `Site.MOUNTAIN 1.5` bila Overworld, `feet.getY() >= 120` dan langit terlihat
  (otomatis berlaku di pulau); `addSiteProvider(Function<ServerPlayer, ExtraSite>)`, `record ExtraSite(id, multiplier,
  @Nullable Dao, Component name)`.
* `cultivation/Meditation`: `MULTIPLIER_CAP 3.5`, `DENSITY_XP_CAP 1.4`; meditasi ditolak bila menumpang, terbang, di
  air, atau **tidak di tanah** (jadi tidak di atas pedang/kapal/feri, tetapi boleh di pulau).
* `cultivation/SpiritVeins`: per wilayah 64 blok **tanpa Y**; `setOverride` akan ikut mengubah tanah di bawah pulau,
  jadi desain ini **tidak** memakai override urat.
* `cultivation/Tribulation`: awan di `pos + SKY_HEIGHT 22`, petir = partikel (`FxType.TRIBULATION_STRIKE`), tanpa blok;
  `addStrikeFactorProvider(StrikeFactor)`. `purba/HeavenlyWrath.start(player, test)` paket-privat.
* `dunia/kota/NpcRole.UTUSAN` (tab `KotaNet.TAB_TITAH`) dan `KultivatorNpc` sudah ada. `DiscipleEntity` memakai id
  aktivitas sampai `ACTIVITY_FORGE_ASSIST = 12`; DESIGN 36 memakai 13-24; 37 memakai 25-27.

**Terbang dan kapal**
* `technique/FlyingSword` (`FlightLease`, `LEASE_SOURCE "flying_sword"`, `MAX_SPEED 0.18F`, `SLOW_FALLING_TICKS 120`
  saat turun), `technique/CloudStep.fallImmune`, `technique/special/CloudTravel` (`DISTANCE_CAP 128`, `NO_FALL_TICKS
  100`), `technique/spec/run/Boons.noFall(LivingEntity)`; pembatal kerusakan jatuh pusat di `Techniques.init()`
  (`ServerLivingEntityEvents.ALLOW_DAMAGE`).
* `dunia/kapal/KapalRohEntity` (`noPhysics`, `setNoGravity`, bergerak dengan `setPos`, membaca heightmap
  `MOTION_BLOCKING`; `DOCK_HEIGHT 3.2`, `DOCK_TICKS 40`, `CRUISE_ABOVE 40`, `CRUISE_MIN_Y 180`; menjepit Y ke
  `[minY + 2, maxY - height - 2]`; `KapalClass PERAHU/SEKTE/BAHTERA` 7×13 / 11×25 / 17×37), `KapalDekEntity` (kolider
  dek, `noSave`, `canBeCollidedWith` seperti perahu), `KapalBajakEntity` (`PHASE_APPROACH/HOLD/FLEE`, `LIFETIME 6000`),
  `Voyages.pirates(...)`, `KapalConfig.pirates`, `pirateChance 0.15`. Pulau padat ada di heightmap = "tanah" untuk
  berlabuh, jadi kapal bisa mendarat di dek pulau tanpa kode baru.

**Dunia, NPC, UI**
* `sekte/diplomasi` (DESIGN 36 modul D): `GerbangPlan.Entry(index, blockX, blockZ, chunkX, chunkZ, band, aliran, pola,
  kataA, kataB, tier, raiderSect, agresi, kehormatan, lambangSeed, ketuaMarga, ketuaNama)`, `GerbangSektePlacement`,
  pack `kultivator:sekte_gerbang`, jangkar `sektenpc/gerbang|murid|ketua` (`AnggotaSekte`, `MODULE "sektenpc"`),
  `DiplomasiService {sects, name, emblem, tier, standing, stance, adjust, gate}`, `Sikap`, `AksiSekte`, `SekteBus`.
* Worldgen: `tools/structgen.Piece`, `jigsaw_structure(..., start_height, heightmap=None → Y absolut,
  terrain_adaptation)`, `structure_set(...)`; contoh Y absolut `kota/kota_abadi` (absolute 150, `terrain_adaptation
  "none"`); `nbt.Template` maks 48 per sumbu x/z, `DATA_VERSION = 5023`.
* Jangkar komponen DESIGN 36 yang dibaca runtime: `kebun_petak_<n>`, `kebun_rawat_<n>`, `tambang_mulut`,
  `tambang_dalam_1..4` (`KebunKomponen`), `panji_gerbang_0/1`, `panji_formasi_0..3`, `panji_arena_*`
  (`PanjiKomponen`); id komponen `sx_kebun_petak`, `sx_kebun_tambang`, `sx_seragam_panji`, `sx_seragam_panji_formasi`,
  `sx_tangga_batu`, `sx_turnamen_tribun`, `sx_paviliun_altar`, `sx_paviliun_makam` (`SekteIds.componentId`).
* `client/hub/HubCards.register(section, id, glyph, lock, action)`, `HubNav.SEKTE = 3` (7 kartu sekte saat ini),
  `Glyphs.HALL/SEAL/PATH/BUILD/...`; `guide/GuideChapters` (`CHAPTER_COUNT = 16`, `step(chapter0, name, hint, check)`,
  id `c<n>.<name>`); `util/Advancements.grant(player, name)`; `dunia/DuniaCommands.test(literal, Supplier<...>)` →
  `/kultivasi uji <literal>`; `sekte/SekteConfig.section(name, Class, Supplier, UnaryOperator)`;
  `voice/Voice.say/heaven/heavenNear`.
* `musuh/SectRaids` (CRLF, milik modul D DESIGN 36): `spawnPoint` mencari `dryLand` di radius wilayah + 4..8 lewat
  heightmap, cadangan 8-12 blok dari prasasti → untuk aula di langit perampok akan muncul di **tanah di bawah pulau**;
  perlu hook (37.7.5).
* `kehancuran/ProtectedZones.addZoneProvider(ZoneProvider)`.
* `LightningBolt.setVisualOnly(boolean)`: petir visual tidak menyalakan api (`spawnFire`) dan tidak melukai entitas
  (`world/entity/LightningBolt.java:50, 130, 154`).

---------------------------------------------------------------------------------------------------------------------

### 37.2 Lore dan Sumber

Semua sumber di bawah sudah dicek (2026-10-22). Yang **tidak** terverifikasi tidak dikutip sebagai fakta dan hanya
dipakai sebagai nama karangan kita sendiri.

| Ilham | Isi yang dipakai | Sumber (terverifikasi) | Dipakai di |
|---|---|---|---|
| Lima gunung melayang di Guixu | *Liezi*, "Tang Wen": gunung Daiyu, Yuanjiao, Fanghu (Fangzhang), Yingzhou dan Penglai terapung di jurang Guixu di timur Laut Bohai; para dewa terbang di antaranya; Kaisar Langit menyuruh Yuqiang mengirim **lima belas kura-kura raksasa** menyangga gunung bergiliran; raksasa negeri Longbo menangkap enam kura-kura sehingga Daiyu dan Yuanjiao hanyut dan tenggelam | Wikipedia "Guixu", "Fanghu", "Ao (turtle)", "Mount Penglai"; terjemahan *Liezi* di sourcelibrary.org | Nama satelit Penglai/Fangzhang/Yingzhou (A7-A9), kepala kura-kura di bawah pulau utama (`HangingIsland.turtle` yang sudah ada), bos bajak "Raksasa Longbo" (37.7.4) |
| Kunlun bertingkat | *Huainanzi*: Kunlun tiga lapis, Liangfeng (Angin Sejuk) di bawah, Xuanpu (Taman Gantung) di tengah, lalu langit atas tempat Taidi; tiap lapis yang didaki memberi kuasa lebih (tak mati, mengendalikan angin dan hujan, menjadi dewa) | *Religions* 14(11):1366, "Searching for the Location of Lord Lao" (kutipan Huainanzi); Wikipedia "Eight Pillars" (Kunlun sebagai tangga bumi-langit) | Tiga pita ketinggian (Kaki/Liangfeng, Taman Gantung/Xuanpu, Istana), komponen T2 "Taman Gantung", kebal cuaca (37.7.2) |
| Gerbang Langit Selatan | *Journey to the West* bab 4: Sun Wukong naik ke langit dengan awan salto, dihentikan penjaga gerbang karena namanya belum tercatat di daftar abadi; setelah diangkat, ia keluar-masuk Gerbang Langit Selatan dengan bebas | journeytothewestresearch.com ("The Immortal Register of Journey to the West", terj. Wu & Yu) | `GerbangSembilanLangit` (T9) dengan "daftar nama": hanya anggota tercatat yang lewat bebas (37.4.10), Utusan Istana Langit (37.7.4) |
| Pendekar pedang Shushan | *Shushan Jianxia Zhuan* (Legenda Pendekar Pedang Gunung Shu), Huanzhulouzhu, 1932, xianxia di sekitar Gunung Emei, Sichuan, dengan para pendekar yang terbang di atas pedang | Wikipedia "Legend of the Swordsmen of the Mountains of Shu", "Huanzhulouzhu"; wuxiasociety.com | Pedang terbang sebagai jalur naik utama, "Panggung Pedang" (T7), murid inti berpatroli terbang |
| Cahaya Buddha Emei | Fenomena di Puncak Emas Gunung Emei: lingkar pelangi di sekeliling bayangan pengamat di atas kabut; salah satu "empat pemandangan" bersama lautan awan dan matahari terbit | Situs wisata Emei (chinaxiantour.com, intotravelchina.com, gluckman.com) | `HaloPelangi` di laut awan (37.6.3) |
| Sekte awan dalam xianxia | *Zhu Xian* (Jade Dynasty, Xiao Ding, 2003-2007): sekte Qingyun ("Awan Hijau") di Gunung Qingyun sebagai kekuatan ortodoks terdepan; *Martial Peak* (Momo): sekte "Lingxiao" (Paviliun Langit Tinggi) | Wikipedia "Zhu Xian (novel)"; daftar/fandom *Martial Peak* | Hanya ilham nama untuk generator nama sekte langit NPC; **tidak** ada fakta plot yang dipakai |

Tidak terverifikasi, tidak dikutip: "Aula Lingxiao" di *Journey to the West*, "Jembatan Pelangi"/"Puncak Tongtian" di
*Zhu Xian*, sekte "Lingyun". Jembatan giok, rantai, tangga awan, air terjun kabut dan Gerbang Sembilan Langit adalah
arah seni kita sendiri.

Narasi dalam game (Kitab Panduan, bab 17): "Di zaman kuno lima gunung mengapung di atas Guixu, disangga kura-kura
langit. Sekte yang cukup kuat dapat mengangkat gunungnya sendiri ke atas awan: makin tinggi, makin dekat Qi langit,
makin jauh dari debu dunia, tetapi makin terlihat oleh Langit dan para perampok awan."

---------------------------------------------------------------------------------------------------------------------

### 37.3 Pendirian: dua jalan ke langit

#### 37.3.1 Gambaran

Sekte langit adalah **aula biasa yang prasastinya berdiri di atas pulau terapung** yang dibangun `BuildPlacer`. Ada dua
jalan masuk yang bertemu di satu alur:

```
Jalan A "Angkat Gunung ke Langit"                Jalan B "Dirikan di Atas Awan"
(aula yang sudah punya templat darat)            (aula baru, belum punya templat)
            \                                              /
             +--> Rancang Langit (pilih A1-A9, tinggi, geser, putar, pratinjau, Validator.sky)
                  -> Sesaji Angkat Gunung (biaya ritual) + tagihan T1 dari Gudang
                  -> Tapak Langit mode TERTAMBAT (prasasti masih di tanah), hanya tingkat 1 dibangun:
                     "Inti Awan" + "Tahta Prasasti"
                  -> Upacara Naik: ketua mencabut prasasti di tanah, membawanya terbang, menaruhnya di Tahta
                  -> mode BERDIRI: aula pindah ke langit, tingkat 2-9 dibangun seperti biasa
                  -> (Jalan A) tapak darat menjadi "Kaki Gunung" (tetap / bongkar / tinggalkan)
```

Alasan desain: prasasti hanya boleh ditaruh dan dicabut oleh pemain (DESIGN 20.1) dan semua sistem aula (calon, murid,
formasi agung, wilayah, perampok, pos jaga) membaca posisi prasasti. Dengan prasasti benar-benar di langit, semua sistem
itu bekerja di langit tanpa cabang khusus, dan upacara "membawa prasasti naik" menjadi momen cerita yang dilakukan
pemain sendiri.

#### 37.3.2 Keluarga templat Awan (A1-A9)

Templat langit diberi nomor **11-19** (`SectTemplates.SKY_FIRST = 11`, `SKY_LAST = 19`); templat langit `t` bersaudara
dengan templat darat `t - 10` (ukuran footprint, wilayah `R_t`, jumlah ruang menara, kursi Qi dan altar gerbang sama).
Id stabil (disimpan, jangan diganti): `SectTemplates.SKY_IDS`.

| No | Id (`SKY_IDS`) | Nama (id_id / en_us) | Footprint (= darat) | R utama | Satelit × r | above / below | Plaza maks | R_t |
|---|---|---|---|---|---|---|---|---|
| 11 | `awan_pertapa` | Batu Awan Pertapa / Hermit's Cloud Rock | 25×29 | 8 | 3 × 4 | 20 / 17 | 297 | 24 |
| 12 | `awan_kabut` | Pulau Kabut / Isle of Mist | 33×39 | 10 | 3 × 4 | 26 / 19 | 291 | 32 |
| 13 | `awan_bangau` | Pulau Bangau / Crane Isle | 45×51 | 13 | 4 × 5 | 32 / 22 | 285 | 40 |
| 14 | `awan_biru` | Gugus Awan Biru / Azure Cloud Cluster | 57×63 | 17 | 4 × 5 | 36 / 27 | 281 | 46 |
| 15 | `awan_puncak` | Puncak Melayang / Floating Peak | 73×81 | 21 | 5 × 6 | 42 / 31 | 275 | 58 |
| 16 | `awan_suci` | Tanah Suci Mengambang / Drifting Holy Land | 89×97 | 26 | 5 × 7 | 46 / 37 | 271 | 68 |
| 17 | `awan_gua_surga` | Gua Surga di Awan / Cave Heaven in the Clouds | 109×117 | 31 | 6 × 7 | 50 / 42 | 267 | 80 |
| 18 | `awan_kunlun` | Kunlun Melayang / Floating Kunlun | 133×141 | 37 | 6 × 8 | 54 / 49 | 263 | 96 |
| 19 | `awan_sembilan_langit` | Istana Sembilan Langit / Palace of the Nine Heavens | 161×161 | 44 | 6 × 9 | 60 / 56 | 257 | 108 |

* "Plaza maks" = `getMaxY() - 2 - above` untuk Overworld 26.3 (319 - 2 - above); dihitung saat validasi, tidak
  dikodekan mati.
* `below` mencakup kerucut bawah pulau utama (kedalaman `round(1.1·R) + 4`), kristal dan akar gantung; **Tangga Awan**
  turun lebih jauh lewat kolom kaki (37.4.6), bukan lewat `below`.
* Footprint, `disc(d, r)`, `territory`, `towerRooms`, `qiTier`, `gateAltars` disalin dari templat darat saudaranya
  (DESIGN 21 Tabel A/B). `relief` dan `earthwork` tidak berlaku (0).

#### 37.3.3 Gerbang dan biaya

Gerbang ranah memakai `data/Realm` dan bisa dimatikan dengan config `langit.gerbangRanah` (default `true`; DESIGN 21
tidak punya gerbang ranah untuk templat darat, tetapi sekte langit adalah konten akhir permainan).

| Templat | Ranah ketua minimum | Jalan A juga butuh | Jalan B |
|---|---|---|---|
| A1-A3 | `GOLDEN_CORE` | tapak darat tingkat ≥ 6, level aula ≥ 6, Formasi Agung aktif (`GrandFormation.State.grand`), `n ≤ g + 1` | boleh bila `n ≤ langit.awanAwalMaks` (3) |
| A4-A6 | `NASCENT_SOUL` | sama | tidak (harus lewat Jalan A atau Naik Templat) |
| A7-A8 | `DEITY_TRANSFORMATION` | sama | tidak |
| A9 | `DAO_UNION` | sama | tidak |

(`g` = templat darat saat ini 1-9, `n` = nomor saudara darat templat langit 1-9.) Jalan A juga ditolak bila: penyerbuan
aktif (`SectRaids.active`), `WorldBreaker.busy()`, tapak sedang `STATE_RELOCATING`/`STATE_UPGRADING`/
`STATE_DECONSTRUCTING`, atau aula tidak aktif.

**Sesaji Angkat Gunung** (diambil dari inventaris ketua saat konfirmasi, dikembalikan utuh bila dibatalkan sebelum blok
pertama ditaruh; `LangitSesaji.BILLS`):

| Templat | Batu roh (`SpiritStones.stack(grade, n)`) | Barang |
|---|---|---|
| A1-A3 | 16 × `MID` | 8 `phantom_membrane`, 8 `ender_pearl`, 16 `feather` |
| A4-A6 | 8 × `HIGH` | 24 `phantom_membrane`, 16 `ender_pearl`, 4 `echo_shard` |
| A7-A8 | 24 × `HIGH` | 48 `phantom_membrane`, 4 `ghast_tear`, 8 `echo_shard`, 1 `heart_of_the_sea` |
| A9 | 2 × `SUPREME` | 64 `phantom_membrane`, 16 `echo_shard`, 1 `nether_star` |

Config `langit.sesajiSkala` (0..4, default 1.0) mengalikan jumlah (dibulatkan ke atas, minimal 1 bila > 0). Bahan
bangunan (Batu, Tanah, ... ) tetap dibayar per tingkat dari Gudang (37.4.7). Tidak ada biaya "daya apung" berkala:
pulau tidak pernah jatuh karena blok tidak pernah dipindah oleh kode; biaya berkala hanya untuk formasi (Jaring Awan,
Formasi Pindah Awan) lewat sistem Daya formasi yang sudah ada.

#### 37.3.4 Letak: di atas tapak sendiri atau digeser, dan pita ketinggian

* **Titik asal** (`origin`) = posisi Tahta Prasasti di langit (lokal (0,0,0) templat langit). Default: tepat di atas
  prasasti darat (`dx = dz = 0`); boleh digeser horizontal `|dx|, |dz| ≤ LangitPlan.OFFSET_MAX = 64` (dipilih di layar
  atau `/kultivasi aula langit geser <dx> <dz>`), sehingga pemilik tetap dalam `sectBuildRange` (160, horizontal) dari
  kedua tapak selama mode tertambat. Putaran 0-3 bebas (gerbang langit menghadap ke mana saja).
* **Tinggi plaza** `yP` (lantai plaza di `yP - 1`): default `langit.tinggiAsal = 232`, dipilih dengan langkah 4 di
  layar atau `/kultivasi aula langit tinggi <y>`. Rentang sah:
  * `bandMin = max(LangitPlan.PLAZA_MIN = 208, topMax + below + LangitPlan.CLEARANCE = 24)`; `topMax` = nilai `top`
    Snapshot tertinggi di footprint (pohon, gunung, menara pemain, struktur), dalam koordinat dunia.
  * `bandMax = level.getMaxY() - 2 - above`.
  * `bandMin > bandMax` → merah `langit_sempit` (medan terlalu tinggi; geser atau pilih templat lebih kecil).
* **Tiga pita (ilham Kunlun)** yang dipakai semua bagian dan efek:
  * **Kaki** (tanah): kaki Tangga Awan, simpul tanah Formasi Pindah Awan, Kaki Gunung (Jalan A).
  * **Liangfeng** (`yP - below` .. `yP - 4`): perut pulau, akar, kristal gantung, Rongga Akar (pintu tambang roh,
    puncak Tangga Awan), dermaga bawah.
  * **Xuanpu** (`yP - 4` .. `yP + 6`): permukaan pulau dan satelit, kebun, jembatan giok, dermaga atas.
  * **Istana** (`yP + 6` .. `yP + above`): atap paviliun, pagoda, Gerbang Sembilan Langit, Menara.
* Dengan default 232 dan A9: dasar kerucut di y 176, di bawah awan vanilla (192.33) sehingga awan vanilla melintas
  memotong perut pulau; puncak gerbang di y 292.

#### 37.3.5 Mode TERTAMBAT dan Upacara Naik

**Data situs.** `Site` mendapat field baru (disimpan di tag situs, kunci pendek): `boolean sky` (`"sky"`),
`boolean tethered` (`"teth"`), `@Nullable BlockPos groundStele` (`"gs"`, long), `int ascent` (`"asc"`, 0 belum, 1
menunggu prasasti, 2 sudah naik), `long ascentDay` (`"ascd"`). Untuk situs langit `site.stele` = posisi Tahta (dunia)
dan `site.plazaY = yP`.

**Kunci situs.** Jalan B: situs langit langsung menjadi situs utama pemilik (kunci = `owner`, di `HallData.build_site`).
Jalan A: situs darat tetap utama sampai naik; situs langit disimpan di `Showcase` SavedData dalam peta baru
`Showcase.skies` (kunci `LangitKeys.skyKey(owner) = UUID.nameUUIDFromBytes(("kultivator:langit:" +
owner).getBytes(UTF_8))`, `refundTo = owner`), dimuat `BuildEngine` ke peta baru `skies` di samping `sites`,
`showcases`, `orphans`.

**`steleOk` untuk situs langit** (`BuildEngine.steleOk`, cabang baru):
* `tethered == false`: aturan lama (aula aktif, `hall.pos() == site.stele`, blok prasasti ada).
* `tethered == true`: aula `Halls.hallOf(site.ledgerOwner())` aktif, `hall.pos() == site.groundStele`, blok prasasti
  ada dan dimuat di `groundStele`, **dan** `level.isLoaded(site.stele)`.

**Hanya tingkat 1 selama tertambat**: `Scheduler.next(site)` mengembalikan null untuk komponen tingkat > `LangitPlan.
TETHER_MAX_TIER = 1` selama `tethered`; status `SiteView.STATUS_COMPLETE` diganti baris status baru
`kultivator.sekte_langit.status.menunggu_naik` ("Inti Awan selesai. Bawa prasasti ke Tahta untuk naik.").

**Upacara Naik** (`sect/build/flow/LangitAngkat implements Halls.SteleMoveListener`, didaftarkan **sebelum**
`flow/Relocation` sehingga ia yang pertama ditanya):
1. Saat semua komponen tingkat 1 situs langit DONE, ketua mendapat baris chat emas + tombol `[Cara Naik]` (membuka
   halaman Kitab Panduan c17.naik) dan pilar cahaya penambat digambar dari prasasti darat ke Tahta (37.6.5).
2. Ketua mencabut prasasti darat (penambangan biasa). `onRemoved` → situs langit `ascent = 1`, aula dorman (aturan lama);
   pembangunan berhenti (`STATUS_STELE`). Satu baris aksi: "Prasasti siap dibawa ke langit."
3. Ketua terbang (pedang, kapal, feri, CloudTravel) dan menaruh prasasti **di atas alas Tahta** (sel `stele` situs
   langit; sel di bawahnya adalah alas giok yang dibangun T1, lihat 37.4.9 butir 2). `Halls` memanggil
   `beforeMove(player, owner, level, from, to, wake = true)`. `LangitAngkat` mengenali `to == skySite.stele` dan
   mengembalikan `true` (menangani sendiri), lalu membuka dialog chat:
   * Jalan A: `[Naik, Kaki Gunung tetap]` `[Naik, bongkar tapak bawah]` `[Naik, tinggalkan tapak bawah]` `[Batal]`
     (perintah `/kultivasi aula langit naik tetap|bongkar|tinggalkan`, `.. batal`), menunggu `Relocation.MOVE_TICKS`.
   * Jalan B: `[Naik]` `[Batal]`.
   Dialog menyebut peringatan: "Formasi Agung darat tidak ikut naik; pelindung aula kembali saat tingkat 5 langit
   selesai."
4. Konfirmasi memanggil `EngineApi.ascend(UUID owner, FootMode mode)` (baru, 37.9.1): `Halls.relocateHall(player,
   level, throne)` (aturan 128 blok dan aula dorman berlaku; jeda pindah tidak berlaku karena `wake`), lalu
   `BuildEngine` menukar situs (37.3.6), `tethered = false`, `ascent = 2`, `ascentDay = hari ini`, menyimpan.
5. Setelah naik: semua murid berperan DISCIPLE milik pemilik yang termuat di Overworld dalam 256 blok dipindah ke jangkar
   `arrival` situs langit, 2 murid per detik, dengan efek awan (`LangitFx` op `FX_ARRIVE`), `resetFallDistance()`;
   murid yang belum termuat menyusul sendiri karena tugas mereka membaca posisi aula. Advancement `sekte_langit/naik`.
   Adegan suara: `Voice.heavenNear(level, throne, "heaven.ascend", 96)` bila klip ada, jika tidak lonceng vanilla
   `SoundEvents.BELL_RESONATE` (pola cadangan `KapalVoice`).
6. **Jaminan tingkat**: agar level aula tidak jatuh karena tingkat langit masih 1, `HallBridge` memakai
   `guaranteeTier = tingkat darat saat naik` (mekanisme jaminan relokasi yang ada, `Relocation.MOVE_GUARANTEE_DAYS`
   diganti `langit.jaminanHari = 30` untuk naik) sampai tingkat langit mencapainya atau 30 hari dunia lewat.
7. Prasasti ditaruh di tempat lain (bukan Tahta) saat `ascent = 1`: dialog pindah biasa milik `Relocation` (situs langit
   tetap tertambat ke posisi baru bila masih ≤ `OFFSET_MAX` horizontal, jika tidak situs langit ditandai
   `STATE_PAUSED` dengan baris "Tahta terlalu jauh dari prasasti").

#### 37.3.6 Nasib tapak darat (Jalan A): Kaki Gunung

`enum FootMode { TETAP, BONGKAR, TINGGALKAN }` (`sect/build/api/FootMode`, ordinal disimpan):
* **TETAP** (default): situs darat dipindah ke peta `orphans` dengan kunci `LangitKeys.footKey(owner)`
  (`"kultivator:kaki:" + owner`), `refundTo = owner`, flag baru `Site.foot = true` (`"foot"`), `state =
  STATE_ACTIVE`, tanpa komponen terbuka. Sel-selnya tetap sel sekte: `CellIndex.rekey(UUID from, UUID to)` (baru)
  memindah pemilik setiap seksi milik `owner` di bawah garis `skyFloorY` (37.4.8 S4) ke `footKey`. Pengembalian bongkar,
  perlindungan spawn, kredit ledakan dan zona Kehancuran tetap berlaku (orphan dihitung `insideAnySite`). Kaki Gunung
  tidak bisa dinaikkan templatnya; "Periksa & Perbaiki" boleh. Nanti bisa `/kultivasi aula langit kaki bongkar` atau
  `.. kaki lepaskan`.
* **BONGKAR**: sama seperti "Pindah & bongkar" (orphan membongkar dengan pengembalian setiap kali pemilik dekat).
* **TINGGALKAN**: sama seperti "Pindah & tinggalkan" (`CellIndex.clearOwner(footKey)` setelah rekey; blok menjadi blok
  biasa).
* Situs langit: dipindah dari `skies` ke `sites` dengan kunci `owner` (`Site.owner = owner`, `refundTo = null`);
  seksinya di-`rekey(skyKey, owner)`. Aturan seksi S4 menjamin tak ada seksi bersama.
* Formasi Agung darat (lodestone cakram T5 darat) tidak lagi dalam 24 blok prasasti, jadi aula tanpa Formasi Agung
  sampai cakram T5 langit selesai (peringatan di dialog). Inti formasi lain milik pemilik di tanah tetap ada; yang
  ber-scope `TERRITORY` berhenti (di luar wilayah) seperti aturan DESIGN 24.

#### 37.3.7 Naik templat, turun gunung, pindah

* **Naik Templat** di keluarga yang sama (A`n` → A`m`, `m > n`) memakai alur `Upgrade` yang ada dengan `Validator.sky`;
  asal dan `yP` tetap. Bila `yP > bandMax(m)` → merah `langit_naik_tinggi`.
* **Lintas keluarga** hanya lewat naik (darat → langit). **Turun Gunung** (langit → darat) = relokasi biasa: prasasti
  ditaruh di tanah, dialog `Relocation` "Pindah & bongkar" (pulau dibongkar dengan pengembalian) atau "Pindah &
  tinggalkan" (pulau tetap melayang sebagai blok biasa; `JaringAwan` dan efek langit berhenti karena situs langit tidak
  ada lagi). Templat darat dipilih lagi lewat founding.
* Pemain lain tidak bisa "mencuri" Tahta: menaruh prasasti di Tahta milik orang lain membuat prasasti itu inert (aturan
  128 blok aula lain).

---------------------------------------------------------------------------------------------------------------------

### 37.4 Arsitektur

#### 37.4.1 Kelas keluarga Awan (modul 37B, paket `dev.kultivator.sect.build.blueprint.awan`)

| Kelas | Isi |
|---|---|
| `AwanTabel` | `record Baris(int n, String id, int x0, int x1, int z0, int z1, int r, int satelit, int rs, int above, int below, int discD, int discR, int territory, int towerRooms, int qiTier, int gateAltars, int lapis, int kristal, int airTerjun, int meditasi)` dan `static final Baris[] ROWS` (index `n - 1`), angka dari 37.3.2 dan 37.4.4. |
| `AwanLayout` | Fungsi murni dari `Baris` (tanpa dunia, aman di luar thread server): `satelit(n)` → `List<SlotSatelit(Fungsi f, int x, int z, int r, int level, String nama)>`, `jembatan(n)`, `dermaga(n)`, `kaki(n)` → `int[] {x0, z0, x1, z1}` 9×9, `airTerjun(n)` → titik takik tepi, `meditasi(n)`, `jaring(n)` (4 tiang). |
| `AwanTemplates` | `static TemplateSpec build(int sky)` (11-19): `TemplateSpec.builder(sky).sky(true).footprint(..).envelope(above, below).disc(..).territory(..).towerRooms(..).qiTier(..).gateAltars(..).planVersion(1).zone("plaza", ...).kaki(...)` lalu komponen 37.4.5. |
| `AwanPalette` | Blok keluarga: `AWAN_LANTAI = SMOOTH_QUARTZ`, `AWAN_ANAK_TANGGA = SMOOTH_QUARTZ_SLAB`, `AWAN_TEPI = CHISELED_QUARTZ_BLOCK`, `TEBING = {STONE, ANDESITE, TUFF, CALCITE}`, `PERUT = {STONE, ANDESITE, TUFF, DEEPSLATE}`, `AKAR = MANGROVE_ROOTS`, `AKAR_TANAH = ROOTED_DIRT`, `KRISTAL = AMETHYST_BLOCK`, `KRISTAL_UJUNG = AMETHYST_CLUSTER`, `KRISTAL_TUNAS = LARGE_AMETHYST_BUD`, `GIOK = Palette.jadeBlock()`, `GIOK_LANTAI = Palette.jadeSlab()`, `GIOK_RANTAI = Palette.jadeChain()`, `PAGAR_KAYU = PALE_OAK_FENCE`, `PAGAR_BATU = POLISHED_ANDESITE_WALL` + tutup `SMOOTH_QUARTZ_SLAB`, `SULUR = CAVE_VINES` (berbuah, menyala), `LUMUT_GANTUNG = HANGING_ROOTS`, `LICHEN = GLOW_LICHEN`. **Dilarang**: `BUDDING_AMETHYST` (tumbuh sendiri), `POINTED_DRIPSTONE` dan blok gravitasi (pasir, kerikil, landasan, `SCAFFOLDING`), air atau lava di luar kolam tertutup, `FIRE`, blok kayu dalam jarak 2 dari `CAMPFIRE`/`SOUL_CAMPFIRE` tanpa tutup. |
| `PulauAwan implements Part` | Generator pulau (37.4.2). |
| `PulauSatelit implements Part` | `PulauAwan` kecil + bangunan fungsi (37.4.3). |
| `JembatanGiok implements Part` | Jembatan antar pulau (37.4.3). |
| `TanggaAwan implements Part` | Tangga melingkar tanah → perut pulau (37.4.6). |
| `TahtaPrasasti implements Part` | Plaza, alas giok di bawah prasasti, jangkar `tahta` (37.4.5 T1). |
| `PagarTepi implements Part` | Pagar tepi bertingkat, celah otomatis di jembatan/dermaga/gerbang/tangga. |
| `KristalGantung implements Part` | Gugus kristal di perut pulau (37.4.2 langkah 6). |
| `RonggaAkar implements Part` | Rongga dalam perut pulau: pintu tambang, ujung atas Tangga Awan, ruang menara. |
| `DermagaAwan implements Part` | Dermaga kantilever untuk kapal sekte (37.5.4). |
| `GerbangAwan implements Part` | Gerbang tepi T1-T8 (paifang kayu → marmer). |
| `GerbangSembilanLangit implements Part` | Gerbang monumental T9 (37.4.5). |

Semua bagian mematuhi kontrak `Part`: hanya membaca `Ctx`, tidak pernah dunia, deterministik dari `ctx.random(salt)`
dengan `salt` = id komponen + indeks.

#### 37.4.2 Generator pulau `PulauAwan`

Konstruktor dan opsi:
```java
public PulauAwan(int cx, int cz, int r, Lv top)          // r = R pulau, top = tingkat berdiri permukaan plaza pulau
public PulauAwan cincin(double dari, double sampai)        // hanya anulus dari·R < ρ ≤ sampai·R (pertumbuhan per tingkat)
public PulauAwan sektor(int derajatDari, int derajatSampai) // pemecah komponen besar (37.4.7 batas 24.000 op)
public PulauAwan lapis(int n)                              // 1-3 teras tebing bertingkat
public PulauAwan bukit(int tinggi)                         // bukit belakang (-z) +6..+14, 0 = tanpa
public PulauAwan kedalaman(int d)                          // default round(1.1·r) + 4
public PulauAwan kura(boolean)                             // kepala kura-kura di bawah (A7-A9), pola HangingIsland.turtle
public PulauAwan gaya(Gaya g)                              // Gaya {PUNCAK, TAMAN, BATU}: proporsi rumput/lumut/batu
```
Langkah (semua dalam koordinat lokal templat, `ρ` = jarak horizontal dari `(cx, cz)`, `θ` = sudut):
1. **Garis tepi**: `ρmax(θ) = r · (1 + 0.12·n1(θ) + 0.06·n2(3θ))`, `n1`, `n2` = derau nilai 1D periodik (16 dan 8 titik
   kendali) dari `ctx.random`; dijepit `0.82r..1.15r` dan ke dalam footprint dikurangi 1. Takik untuk jembatan, dermaga,
   gerbang dan air terjun (dari `AwanLayout`) diratakan ke `ρmax` lokal ±1 agar tepinya rapi.
2. **Permukaan atas** `h(x, z)` (tingkat berdiri relatif ke `top`): 0 di dalam `ρ ≤ 0.55r` (plaza dan inti), lalu
   `lapis` teras turun ke tepi: offset `0, -2, -4` pada `ρ/ρmax ≥ 0.55, 0.72, 0.86` (A1-A3: 1 lapis, A4-A6: 2, A7-A9: 3).
   `bukit(t)`: tonjolan belakang `t·(1 - (d/0.45r)²)` di `(cx, cz - 0.55r)` untuk pagoda/menara.
3. **Lapisan atas**: blok permukaan `GRASS_BLOCK` (gaya TAMAN 75%, PUNCAK 55%), tambalan `MOSS_BLOCK` dari derau 2D
   (15-25%), `COARSE_DIRT`/`PODZOL` di bawah pohon; di bawahnya 2 lapis `DIRT`. **Muka tebing teras** (beda tinggi
   antar lapis dan tepi luar 2 blok teratas): pita strata `TEBING[(y + seedBand) mod 5]` dengan `CALCITE` tiap pita
   ke-5, sehingga tebing tampak berlapis.
4. **Perut (kerucut terbalik)**: kedalaman di `ρ`: `d(ρ) = D·(1 - (ρ/ρmax)^1.6)·(0.85 + 0.30·n3(x, z)) + 2`, dengan
   ujung kerucut digeser `±0.15r` (acak) agar tidak simetris. **Hanya kulit** yang ditaruh: sel padat bila
   `-d(ρ) ≤ y ≤ -1` **dan** (`y ≤ -d(ρ) + KULIT` atau `ρ ≥ ρmax - KULIT` atau `y ≥ -4`), `KULIT = 3`. Bagian dalam
   dibiarkan udara (tidak di-emit, jadi tidak ditagih dan tidak mengubah apa pun). Material kulit per kedalaman relatif
   `q = -y / d`: `q < 0.15` `DIRT`/`ROOTED_DIRT`, `q < 0.55` `STONE`/`ANDESITE` (derau), `q < 0.85` `TUFF` dengan coret
   `CALCITE`, sisanya `DEEPSLATE`. Rongga dalam terlindung dari spawn oleh pengaman spawn sel sekte
   (`sectBuildSpawnProof`, `SectSpawnMixin`, verifikasi); bila mixin itu hanya melindungi permukaan atas sel, `RonggaAkar`
   menaruh `Palette.HIDDEN_LIGHT` tiap 6 blok di lantai rongga.
5. **Gantungan** (fase `GARDEN`): pada sel dasar kulit yang terbuka ke bawah, 1:6 `HANGING_ROOTS`, 1:10 `GLOW_LICHEN`
   (muka bawah), di cincin `ρ ≥ 0.75ρmax` sulur `CAVE_VINES` panjang 1-4 (ujung `CAVE_VINES` berbuah 1:3, memberi
   cahaya). Maks 2 `SPORE_BLOSSOM` per sekte (aturan 21.5 yang ada; dihitung lintas komponen oleh `PlanAudit`).
6. **Akar Gantung** (`akar`): `4 + r/6` untaian `MANGROVE_ROOTS` selebar 1, panjang 3-8 di bawah dasar kerucut,
   bercabang 1:3; diakhiri `ROOTED_DIRT` + `HANGING_ROOTS`.
7. **Kristal Gantung** (`KristalGantung`, T9; jumlah `Baris.kristal`): gugus di pita Liangfeng, inti `AMETHYST_BLOCK`
   meruncing 3-7 blok ke bawah (penampang 3×3 → 2×2 → 1), kerah `CALCITE`, ujung `AMETHYST_CLUSTER` menghadap bawah,
   `LARGE_AMETHYST_BUD` di sisi, satu `END_ROD` mengarah bawah di ujung gugus terbesar (cahaya). Kristal tidak dihitung
   situs `AMETHYST` (letaknya di bawah pulau, jauh dari tempat duduk).
8. **Kepala kura-kura** (`kura(true)`, A7-A9 pulau utama): menghadap gerbang (+z), di `y = -0.6D`, bentuk sama dengan
   `HangingIsland.turtle` (`MOSSY_COBBLESTONE`, mata `Palette.HIDDEN_LIGHT`), ilham *Liezi*.
9. **Jangkar**: `pulau_pusat` (permukaan), `pulau_dasar` (ujung kerucut), `airterjun_<i>` di takik tepi menghadap keluar
   (arah disimpan sebagai sufiks `_n/_e/_s/_w`, dibaca 37V).

Pertumbuhan per tingkat memakai `cincin`: T1 `0..0.55`, T4 `0.55..0.80`, T6 `0.80..1.0`. Anulus baru menimpa pagar tepi
lama dengan `Marks.demolish` lebih dulu (aturan light-swap: fase struktur mengganti sel sendiri, dengan pengembalian
biaya), dan kulit perut anulus baru menyambung ke kulit lama (anulus selalu menyertakan `KULIT` sel tumpang di batas
dalam agar tidak ada celah).

#### 37.4.3 Pulau satelit dan jembatan giok

`enum Fungsi { KEBUN, ALKIMIA, LELUHUR, ARENA, ASRAMA, TAMBANG }` (urutan = urutan kemunculan). Jumlah satelit per
templat (`Baris.satelit`): A1-A2 tiga (KEBUN, ALKIMIA, LELUHUR), A3-A4 empat (+ARENA), A5-A6 lima (+ASRAMA), A7-A9 enam
(+TAMBANG). Fungsi yang tidak punya satelit ada di pulau utama (seperti templat darat). Paviliun Kitab **selalu** di pulau
utama karena jangkar `library` harus ≤ 16 blok dari prasasti (21.9).

| Fungsi | Tingkat | Isi | Jangkar |
|---|---|---|---|
| KEBUN "Taman Gantung" | T2 (pulau + kebun), T3 (`sx_kebun_petak`) | bedeng herba `Grounds` (gaya t2_garden), paviliun teh kecil, rumpun bambu, kolam kecil tertutup | `kebun_petak_<n>`, `kebun_rawat_<n>` (nama `KebunKomponen.ANCHOR_*`), `post_garden` |
| ALKIMIA "Paviliun Tungku" | T4 | `Pavilion` 2 lantai, kuali + api roh (jumlah per 21.11 T4), rak ramuan, halaman batu | `post_kitchen`? tidak; `alkimia` |
| ARENA "Gelanggang Awan" | T5 (pulau + gelanggang), T7 (`sx_turnamen_tribun`, A6+) | `Grounds.arena` 15×15 lei tai di tengah, tiang bendera arena | `arena`, `panji_arena_<n>` |
| LELUHUR "Altar Leluhur" | T8 | aula leluhur kecil + altar + teras makam (`sx_paviliun_makam`) | `leluhur`, jangkar makam milik `PaviliunKomponen` (verifikasi nama) |
| ASRAMA "Pondok Murid" | T8 (A5+) | 2-4 asrama `Hall` kecil, sumur kering hias | `asrama_<n>` |
| TAMBANG "Pulau Urat Roh" | T9 (A7-A9) | pulau berbatu gaya BATU, mulut tambang ke rongga satelit | `tambang_mulut`, `tambang_dalam_1..4` |

Nama papan satelit A7-A9 (ilham Guixu): LELUHUR = **Penglai**, ALKIMIA = **Fangzhang**, KEBUN = **Yingzhou**, TAMBANG =
**Daiyu**, ARENA = **Yuanjiao** (dua yang dalam *Liezi* hanyut; di sini "ditambat kembali"), ASRAMA = "Pondok Bangau".

**Tata letak** (`AwanLayout.satelit`): sudut satelit merata pada busur 50°..310° diukur dari +z (busur depan ±50° untuk
gerbang dan pemandangan dibiarkan kosong); jarak pusat `d = R + rs + celah`, `celah = 6 + 4·(i mod 2)`; tingkat berdiri
bergiliran `Lv.at(-8), Lv.at(4), Lv.at(-4), Lv.at(8), Lv.at(0), Lv.at(-6)`; kedalaman satelit `round(1.3·rs) + 3`, 1 lapis.
Bila satelit keluar footprint, `d` dikecilkan sampai muat; bila tetap tidak muat, `AwanLayout` melempar saat
`AwanTemplates.build` (tertangkap `uji sekte audit`).

**`JembatanGiok(int xA, int zA, Lv a, int xB, int zB, Lv b)`** dengan `.lebar(3)`, `.gaya(Gaya {RANTAI, LENGKUNG})`:
* Lantai `GIOK_LANTAI` (slab giok) 3 lebar; tepi `PALE_OAK_FENCE` (T2-T5) yang diganti kisi giok
  `Palette.jadeBlock()`-dinding pada T6 (`aw_t6_jembatan`, swap).
* `LENGKUNG` (panjang ≤ 16): busur bulan, puncak naik `0.12·L`; `RANTAI` (panjang > 16): lantai melendut
  `-0.06·L·4t(1-t)` di antara dua tiang gerbang kecil (`QUARTZ_PILLAR` + atap mini), rantai giok vertikal tiap 4 blok
  dari tiang ke pagar (hiasan), `LanternPost` gaya `HANGING` tiap 8 blok.
* Beda tingkat ujung diselesaikan dengan kemiringan maks 1:2 memakai slab atas/bawah; sisanya tangga 3 lebar di ujung.
* Panjang > 40: batu pijak di tengah = `PulauAwan` r 3 dengan paviliun angin kecil, jembatan dipecah dua.
* Jembatan tidak pernah di bawah `skyFloorY` dan selalu dalam footprint.

#### 37.4.4 Angka keluarga per templat (`AwanTabel.ROWS`)

| n | lapis | bukit | kristal | air terjun (emitor) | meditasi | dermaga (kelas) | Kaki 9×9 di | jaring |
|---|---|---|---|---|---|---|---|---|
| 1 | 1 | 0 | 2 | 2 | 2 | 1 (PERAHU) | (-6, 6) | 4 |
| 2 | 1 | 0 | 3 | 2 | 2 | 1 (PERAHU) | (-7, 7) | 4 |
| 3 | 1 | 6 | 4 | 3 | 3 | 1 (PERAHU) | (-9, 9) | 4 |
| 4 | 2 | 6 | 5 | 3 | 3 | 2 (PERAHU, SEKTE) | (-12, 12) | 4 |
| 5 | 2 | 8 | 6 | 4 | 4 | 2 (PERAHU, SEKTE) | (-15, 15) | 4 |
| 6 | 2 | 8 | 8 | 4 | 4 | 2 (PERAHU, SEKTE) | (-18, 18) | 4 |
| 7 | 3 | 10 | 9 | 5 | 5 | 3 (+BAHTERA) | (-22, 22) | 4 |
| 8 | 3 | 12 | 10 | 6 | 6 | 3 (+BAHTERA) | (-26, 26) | 4 |
| 9 | 3 | 14 | 12 | 8 | 6 | 3 (+BAHTERA) | (-31, 31) | 4 |

(Kaki = titik pusat persegi 9×9 di bawah tepi depan-kiri pulau utama, `ρ ≈ 0.7R`.)

#### 37.4.5 Tingkat T1-T9 keluarga Awan (komponen, id, isi)

Id komponen keluarga Awan: `aw_t<tingkat>_<apa>` (stabil selamanya). Komponen yang dibaca sistem DESIGN 36 memakai id
**persis** `sx_*` milik modul 36 dan jangkar yang sama, sehingga kebun, tambang, bendera, tangga, tribun, altar dan makam
DESIGN 36 langsung bekerja di langit tanpa kode baru di modul 36. Tiap tingkat diakhiri `aw_t<n>_ornament` (grup
`ORNAMENT`, `decorate` bagian lama: lonceng angin T4, sepuhan T6, papan bercahaya T9) seperti 21.2 aturan 7.

| Tingkat (tema darat) | Komponen (grup) | Isi |
|---|---|---|
| **T1 Inti Awan** (Peletakan Prasasti) | `aw_t1_inti` (AXIS) | `PulauAwan` `cincin(0, 0.55)`, permukaan plaza, perut inti |
| | `aw_t1_tahta` (AXIS) | `TahtaPrasasti`: plaza `AWAN_LANTAI` 13×13 (A1-A3) / 17×17 / 23×23 (A7-A9), alas `GIOK` di `(0,-1,0)`, cincin `CHISELED_QUARTZ_BLOCK` 3×3, sel prasasti `keep`, jangkar `tahta`, `arrival` (6 blok di depan), pos jaga |
| | `aw_t1_aula` (MAIN) | Aula Pendiri (`Hall` rank kecil, seperti `t1_aula` darat) di `-z` |
| | `aw_t1_asrama` (SIDE) | asrama kecil |
| | `aw_t1_gerbang` (BOUNDARY) | `GerbangAwan` kayu di tepi depan + `PagarTepi` kayu sepanjang tepi inti |
| | `aw_t1_formasi_awan` (SIDE) | pola simpul langit "Formasi Pindah Awan" (37.5.1) dipasang **tidak aktif**, jangkar `formasi_awan` |
| | `sx_seragam_panji` (BOUNDARY) | dua tiang bendera di sisi gerbang, jangkar `panji_gerbang_0/1` (format `PanjiKomponen`) |
| **T2 Api Dapur dan Taman Gantung** | `aw_t2_dapur` (SIDE) | dapur (smoker + campfire, 21.11), `post_kitchen` |
| | `aw_t2_satelit_kebun` (GARDEN) + `aw_t2_jembatan_kebun` (GARDEN) | satelit KEBUN + jembatan giok |
| | `sx_paviliun_altar` (SIDE) | altar kecil di sisi barat-belakang pulau utama (bentuk `PaviliunKomponen`, verifikasi jangkar) |
| | `aw_t2_pohon` (GARDEN) | pinus miring di tepi, plum di plaza |
| **T3 Paviliun Kitab dan Tangga Awan** | `aw_t3_kitab` (SIDE) | Paviliun Kitab di pulau utama, rak buku 14/16/24/32/40/48/56/64/72, mimbar 1-2, `library` ≤ 16 |
| | `aw_t3_tangga_awan` (AXIS) | `TanggaAwan` + pelataran kaki (37.4.6), jangkar `tangga_kaki`, `tangga_puncak` |
| | `sx_tangga_batu` (AXIS) | dua stela Batu Hati di pelataran **kaki** + cakram proyektor 3×3 (bentuk `BatuHatiKomponen`) |
| | `sx_kebun_petak` (GARDEN) | bedeng DESIGN 36 W di satelit KEBUN |
| | `aw_t3_plaza` (AXIS) | paving jalan utama pulau |
| **T4 Tepi dan Air** | `aw_t4_pelebaran` (MAIN, dipecah sektor bila > 24.000 op) | `PulauAwan` `cincin(0.55, 0.80)` + teras tebing |
| | `aw_t4_pagar` (BOUNDARY) | `PagarTepi` batu (swap pagar kayu) |
| | `aw_t4_kolam` (GARDEN, A4+) | kolam `Pond` tertutup di teras + `Waterfall` kecil dari batu bukit ke kolam (air di dalam pulau saja) |
| | `aw_t4_emitor` (GARDEN) | takik tepi + jangkar `airterjun_<i>` (air terjun kabut digambar klien, 37.6.4) |
| | `aw_t4_satelit_alkimia` + `aw_t4_jembatan_alkimia` | satelit ALKIMIA |
| | `sx_kebun_tambang` (SIDE, A1-A6) | `RonggaAkar` + mulut tambang di belakang plaza, jangkar `tambang_mulut`, `tambang_dalam_1..4` |
| | `aw_t4_akar` (GARDEN) | akar gantung, sulur, lumut gantung |
| **T5 Formasi Agung dan Jaring Awan** | `aw_t5_disc` (AXIS) | `FormationDisc(discD, discR, Lv.PLAZA)` (lodestone, 4 crying obsidian, 4 amethyst; 21.11), jangkar `formation` ≤ 24 |
| | `aw_t5_jaring` (BOUNDARY) | 4 Tiang Jaring di tepi mata angin (`QUARTZ_PILLAR` 4 tinggi, tutup `AMETHYST_BLOCK`, `END_ROD` puncak), pola formasi `jaring_awan` (37.5.6), jangkar `jaring_0..3` |
| | `sx_seragam_panji_formasi` (GARDEN) | 4 tiang bendera di cincin cakram, `panji_formasi_0..3` |
| | `aw_t5_altars` (BOUNDARY) | altar gerbang (`gateAltars` dari saudara darat) |
| | `aw_t5_satelit_arena` + jembatan (A3+) | satelit ARENA, jangkar `arena` |
| **T6 Aula Utama** | `aw_t6_penuh` (MAIN, sektor) | `PulauAwan` `cincin(0.80, 1.0)` + kura-kura (A7-A9) |
| | `aw_t6_aula_utama` (MAIN) | Aula Utama (`Hall` rank D, `RoofBuilder.Type.XIESHAN` dua atap, `Interiors.Preset.MAIN_HALL`), `main_hall` |
| | `aw_t6_paifang` (AXIS) | paifang marmer menggantikan gerbang kayu (`Marks.demolish` lalu `Paifang`) |
| | `aw_t6_jembatan` (GARDEN) | swap pagar jembatan ke kisi giok |
| | `aw_t6_dings` (GARDEN) | ding (campfire per 21.11) |
| **T7 Lapangan Latihan, Dermaga, Panggung Pedang** | `aw_t7_latihan` (SIDE) | lapangan latih, sasaran (jumlah 21.11), genta, tambur |
| | `aw_t7_panggung_pedang` (SIDE) | teras kantilever 7×9 di tepi samping, jangkar `panggung_pedang` (37.5.3) |
| | `aw_t7_dermaga` (BOUNDARY) | `DermagaAwan` (37.5.4), jangkar `dermaga_<i>` |
| | `aw_t7_halte_feri` (SIDE) | halte Feri Awan, jangkar `feri_atas` |
| | `sx_turnamen_tribun` (SIDE, A6+) | tribun ≤ 3 blok di luar `Grounds.arena` di satelit ARENA |
| **T8 Pagoda dan Leluhur** | `aw_t8_pagoda` (MAIN) | `Pagoda` di bukit belakang: 3 lantai (A1-A3), 5 (A4-A6), 7 (A7-A9), atap melengkung, `END_ROD` puncak |
| | `aw_t8_satelit_leluhur` + jembatan | satelit LELUHUR |
| | `sx_paviliun_makam` (GARDEN) | teras makam di satelit LELUHUR |
| | `aw_t8_satelit_asrama` + jembatan (A5+) | satelit ASRAMA |
| | `aw_t8_kursi_qi` (GARDEN) | `QiSeat` tingkat per 21.11 (A3: 1, A4-A6: 2, A7-A9: 3) |
| | `aw_t8_meditasi` (GARDEN) | paviliun meditasi kecil di titik pandang tepi, jangkar `meditasi_<i>` (`Baris.meditasi`) |
| **T9 Menara Langit Membara dan Sembilan Langit** | `aw_t9_menara` (MAIN) | `TowerShaft` turun ke rongga perut (pintu ≤ 12 blok, api terdalam ≤ 28 di bawah), `tower` |
| | `aw_t9_gerbang_langit` (AXIS) | `GerbangSembilanLangit` menggantikan paifang (demolish + bangun), jangkar `gerbang_langit` |
| | `aw_t9_kristal` (GARDEN) | `KristalGantung` (`Baris.kristal`) |
| | `aw_t9_satelit_tambang` + jembatan + `sx_kebun_tambang` (A7-A9) | satelit TAMBANG (pada A7-A9 `sx_kebun_tambang` ada di sini, bukan T4) |
| | `aw_t9_finale` (GARDEN) | kelopak (`Marks.petals`), papan emas bercahaya, sepuhan atap |

**`GerbangSembilanLangit`** (T9): di tepi depan pulau utama di atas teras kantilever 9 lebar yang menjorok 6 blok ke
udara; dasar `Gates.gateTower(x, z, Lv.PLAZA, half 4, baseHeight 10)`, di atasnya **sembilan atap bertingkat**
(`RoofBuilder` xieshan dengan ujung melengkung `flying tips`) yang mengecil 1 blok per tingkat, tinggi tiap tingkat 4,
total ≤ `above`; genteng tiap tingkat diwarnai urut sembilan warna langit (emas → jingga → merah → merah muda → ungu
muda → ungu → biru → hijau giok → putih) dengan `Palette.glazed(DyeColor)`/`concrete(DyeColor)`; papan
`Palette.PLAQUE_FIELD` + papan tanda bertulisan "九天门" dan "Gerbang Sembilan Langit" (teks tanda lewat `SignText`
yang sudah didukung `BuildPlacer`); tiang `panji_gerbang_0/1` dipindah ke kaki gerbang (jangkar sama). Pada A1-A3
gerbang ini versi kecil: 3 atap.

**Lentera dan atap**: `LanternPost` gaya `JADE` di jalan tiap 8 blok, `HANGING` di bawah atap, `PAGODA` di sudut
pagoda; semua atap keluarga Awan memakai ujung melengkung (`RoofBuilder` flying tips, sudah ada di T4+ darat) sejak T1
karena "istana di awan". Lentera melayang (kongming) di malam hari hanya gambar klien (37.6).

#### 37.4.6 Kolom kaki dan Tangga Awan

* `TemplateSpec.Builder.kaki(int x0, int z0, int x1, int z1, int design, int lo, int hi)` (baru) menambah zona dipasang
  `"kaki"` (persegi 9×9 dari `AwanLayout.kaki(n)`), dengan `lo = -(yP - minY - 2)`, `hi = -(below + 12)` dihitung
  saat kompilasi rencana karena `yP` tiap situs berbeda (`SitePlan` menerima `yP`); `TerraceFitter` hanya memasang
  zona `kaki` untuk situs langit (zona lain tetap).
* `SitePlan.inFootprint(pos)`: untuk situs langit sah bila di dalam `column()` **atau** di dalam kolom kaki:
  `(x, z)` dalam persegi kaki dan `kakiLevel - 4 ≤ y ≤ -1 - below`.
* **`TanggaAwan(int cx, int cz, Lv kaki, Lv puncak)`**: heliks berpusat di tengah persegi kaki; anak tangga 2 lebar
  (`ρ` 3..4), 24 anak tangga per putaran, naik setengah blok per anak tangga (slab bawah/atas bergantian
  `AWAN_ANAK_TANGGA`), jadi 12 blok per putaran; pagar luar `PALE_OAK_FENCE` tiap anak tangga; lentera gantung di bawah
  anak tangga tiap 24; **pelataran** cincin penuh (`ρ` 2..4) tiap 2 putaran (24 blok) dengan bangku (tangga kayu) dan
  satu `Marks.anchor("tangga_rehat_<i>")`. Ujung bawah: pelataran kaki 7×7 `STONE_BRICKS` + 2 singa `Stamp.lion` +
  papan "Tangga Awan" + jangkar `tangga_kaki` (dan `arrival_kaki` untuk calon, 37.5.2). Ujung atas: masuk ke perut
  pulau lewat lubang kulit 3×3 di sumbu heliks, lanjut di dalam `RonggaAkar` sampai pintu `Gerbang Akar` (pagar gerbang
  `PALE_OAK_FENCE_GATE` ganda) yang keluar di teras depan pulau, jangkar `tangga_puncak`.
* Contoh A9 dengan tanah y 70 dan `yP` 232: naik ≈ 146 blok → ≈ 12 putaran, 292 anak tangga × 2 = 584 slab, 292 pagar,
  6 pelataran ≈ 300 blok, 12 lentera: ≈ 1.200 op. Tidak ada blok penopang; anak tangga slab melayang (sah di MC dan tidak
  jatuh).

#### 37.4.7 Bahan per tingkat (seimbang dengan templat darat)

Aturan imbang: total keluarga Awan = **1,30 ×** total saudara darat (DESIGN 21.11), tambahan 30% sebagian besar Batu dan
Tanah untuk massa pulau, sisanya Flora (gantungan), Logam (rantai, lentera) dan Permata (jembatan giok). Selain itu
langit **tidak punya kredit galian** (darat di lereng sering menutup Batu/Tanah dengan galian), jadi biaya nyata di
langit kira-kira 1,4-1,6 × darat. Tagihan nyata berasal dari dry run (`uji sekte info <n>`); cetak biru disetel ±15%
dari target ini seperti 21.11.

Total per templat (unit): A1 4.200 · A2 8.260 · A3 13.220 · A4 20.610 · A5 27.410 · A6 38.260 · A7 47.370 · A8 67.370 ·
A9 84.830. Barang persis per tingkat (kuali, rak buku, mimbar, tungku, lodestone, target, genta, kursi Qi, ruang menara)
= tabel 21.11 saudara darat.

| A1 | Batu | Tanah | Kayu | Genteng | Kapur | Pernis | Flora | Logam | Permata | Total |
|---|---|---|---|---|---|---|---|---|---|---|
| T1 | 290 | 50 | 130 | 100 | 50 | 20 | 10 | 110 | 50 | 810 |
| T2 | 140 | 30 | 70 | 70 | 40 | 20 | 40 | 30 | 30 | 470 |
| T3 | 200 | 30 | 120 | 130 | 70 | 30 | 10 | 30 | 40 | 660 |
| T4 | 230 | 40 | 90 | 110 | 50 | 40 | 10 | 40 | 60 | 670 |
| T5 | 210 | 20 | – | 90 | 110 | – | – | 30 | – | 460 |
| T6 | 160 | 50 | 20 | 20 | 10 | 10 | 10 | 20 | 30 | 330 |
| T7 | 50 | 30 | – | – | – | – | 20 | 20 | 20 | 140 |
| T8 | 80 | 30 | 10 | – | – | – | 40 | 50 | 50 | 260 |
| T9 | 270 | 30 | – | – | – | 10 | 10 | 60 | 20 | 400 |
| Total | 1.630 | 310 | 440 | 520 | 330 | 130 | 150 | 390 | 300 | 4.200 |

| A5 | Batu | Tanah | Kayu | Genteng | Kapur | Pernis | Flora | Logam | Permata | Total |
|---|---|---|---|---|---|---|---|---|---|---|
| T1 | 1.010 | 300 | 230 | 250 | 130 | 70 | 60 | 120 | 140 | 2.310 |
| T2 | 390 | 170 | 160 | 80 | 50 | 30 | 300 | 70 | 60 | 1.310 |
| T3 | 660 | 190 | 310 | 320 | 110 | 80 | 40 | 90 | 130 | 1.930 |
| T4 | 2.280 | 960 | 180 | 640 | 220 | 560 | 100 | 270 | 140 | 5.350 |
| T5 | 1.830 | 150 | 440 | 740 | 10 | 400 | 30 | 210 | 240 | 4.050 |
| T6 | 1.960 | 1.130 | 390 | 420 | 350 | 120 | 70 | 200 | 260 | 4.900 |
| T7 | 690 | 110 | 430 | 440 | 120 | 110 | 20 | 190 | 270 | 2.380 |
| T8 | 990 | 390 | 560 | 670 | 250 | 150 | 160 | 140 | 250 | 3.560 |
| T9 | 820 | 200 | – | – | – | 20 | 60 | 270 | 250 | 1.620 |
| Total | 10.630 | 3.600 | 2.700 | 3.560 | 1.240 | 1.540 | 840 | 1.560 | 1.740 | 27.410 |

| A9 | Batu | Tanah | Kayu | Genteng | Kapur | Pernis | Flora | Logam | Permata | Total |
|---|---|---|---|---|---|---|---|---|---|---|
| T1 | 2.240 | 940 | 360 | 370 | 160 | 80 | 190 | 300 | 300 | 4.940 |
| T2 | 1.000 | 500 | 230 | 110 | 50 | 30 | 570 | 150 | 120 | 2.760 |
| T3 | 1.400 | 590 | 430 | 520 | 80 | 90 | 120 | 200 | 240 | 3.670 |
| T4 | 4.350 | 1.580 | 420 | 1.920 | 190 | 1.370 | 440 | 700 | 320 | 11.290 |
| T5 | 3.860 | 470 | 940 | 1.570 | 30 | 720 | 90 | 490 | 560 | 8.730 |
| T6 | 7.140 | 6.420 | 1.050 | 1.160 | 930 | 310 | 210 | 680 | 830 | 18.730 |
| T7 | 2.110 | 350 | 820 | 1.000 | 140 | 310 | 70 | 610 | 790 | 6.200 |
| T8 | 6.390 | 4.540 | 710 | 650 | 820 | 140 | 1.280 | 660 | 540 | 15.730 |
| T9 | 4.580 | 1.440 | 1.140 | 1.480 | 310 | 320 | 420 | 1.420 | 1.670 | 12.780 |
| Total | 33.070 | 16.830 | 6.100 | 8.780 | 2.710 | 3.370 | 3.390 | 5.210 | 5.370 | 84.830 |

A2-A4 memakai pembagian A1 atau A5, A6-A8 pembagian A5 atau A9 (yang lebih dekat ukurannya), diskalakan ke totalnya
(aturan sama dengan 21.11).

**Anggaran blok** (op, tidak termasuk udara yang tidak di-emit; batas audit, bukan target):

| Templat | A1 | A2 | A3 | A4 | A5 | A6 | A7 | A8 | A9 |
|---|---|---|---|---|---|---|---|---|---|
| Total op maks | 9.000 | 13.000 | 20.000 | 30.000 | 45.000 | 62.000 | 85.000 | 115.000 | 150.000 |

Op maks per komponen: 24.000 untuk semua templat; `PulauAwan.sektor` memecah anulus besar menjadi komponen `_u/_s/_t/_b`
(utara/selatan/timur/barat) dengan id stabil, misalnya `aw_t6_penuh_u`.

Perkiraan A9: permukaan 3 lapis `3πR²` ≈ 18.200, kulit perut `3·πR·√(R²+D²)` ≈ 28.200, satelit 6 × ≈ 2.000, tangga ≈
1.200, bangunan ≈ 85.000 (darat T9 tanpa galian) → ≈ 145.000.

#### 37.4.8 Aturan `Validator.sky` (membangun di udara)

`Validator.sky(ServerLevel level, SitePlan plan, Snapshot snap, int yP, @Nullable SiteView ground, List<BoundingBox>
otherSites, boolean force)` menggantikan `terrain(...)` untuk situs langit; `world(...)` tetap dipanggil dengan kotak
pulau saja. Kunci pesan `kultivator.blueprint.warn.langit_*` (fragmen `blueprint_awan`).

| # | Aturan | Tingkat |
|---|---|---|
| S1 | Dimensi Overworld; bukan alam/abadi | merah `dimension` (ada) |
| S2 | `bandMin ≤ yP ≤ bandMax` (37.3.4) | merah `langit_rendah` / `langit_tinggi` / `langit_sempit` |
| S3 | **Langit bersih**: untuk setiap kolom footprint, `snap.top(x, z) ≤ skyFloorY - 8` (`skyFloorY` = y dunia blok terbawah pulau/satelit/kristal) | merah `langit_terhalang` (jumlah kolom); hologram menandai kolomnya |
| S4 | **Aturan seksi** (Jalan A): `skyFloorY ≥ ((groundColumnTopY >> 4) + 1) << 4`, `groundColumnTopY` = y dunia atas `column()` tapak darat; dengan begitu tak ada seksi chunk 16³ yang dimiliki dua situs (`CellIndex` satu pemilik per seksi) | merah `langit_seksi` |
| S5 | Kotak pulau (+16 horizontal) tidak menyentuh footprint situs/showcase/orphan **orang lain** (aturan `SITE_MARGIN`, horizontal: tidak boleh melayang di atas sekte orang) | merah `overlap` (ada) |
| S6 | Tahta tidak dalam 128 blok aula lain (`Halls.OTHER_HALL_RANGE`) | merah `langit_aula_lain` |
| S7 | Kolom kaki: chunk kaki tidak sama dengan chunk mana pun dari footprint tapak darat sendiri atau situs lain; relief di 9×9 ≤ 4; air ≤ 30%; tidak ada lava; Snapshot 100% | merah `langit_kaki_*` |
| S8 | Geser horizontal tahta dari prasasti darat ≤ `OFFSET_MAX` 64 | merah `langit_jauh` |
| S9 | Relief, earthwork, void/tebing, air permukaan footprint **tidak** dicek | catatan `langit_udara` |
| S10 | Struktur penting (`Validator.STRUCTURES`) hanya dicek pada kotak pulau dan kolom kaki, bukan seluruh kolom tanah di bawah | merah `structure` (ada) |
| S11 | Snapshot kolom footprint dan kaki 100% sebelum konfirmasi | merah (aturan lama) |
| S12 | Spawn protection server dedicated (pada kotak pulau dan kaki) | merah `spawn` (ada) |
| S13 | Penduduk desa: kotak pulau ±32 y (selalu kosong) **dan** kolom kaki ±16 | merah `village` (ada) |
| S14 | Pemain lain (bukan anggota) sedang berada di kotak pulau | amber `langit_ada_orang` (blok ditunda `EntityGuard` seperti biasa) |
| S15 | `paksa` (uji saja) menurunkan S2 dan S7-relief/air menjadi amber; tidak pernah S3-S6, S10, S12, S13 | – |

#### 37.4.9 Perubahan mesin (milik 37B)

1. `SectTemplates`: `SKY_FIRST = 11`, `SKY_LAST = 19`, `SKY_IDS[9]`, `isSky(int t)`, `validAny(int t)` (1-9 atau
   11-19), `brother(int t)` (11-19 → 1-9, 1-9 → 11-19), `id/byId/nameKey/name` meliputi langit; `valid(int)` **tetap**
   1-9 (arti "templat darat"). Setiap pemakai `valid` diaudit: yang menerima templat dari data tersimpan atau perintah
   memakai `validAny`; `Validator.allowed` memakai `brother(t) ≤ sectBuildMaxTemplate` untuk langit.
2. `BuildPlacer.place`: pengecualian sempit untuk alas tahta, `pos.equals(site.stele.below()) && site.sky &&
   componentId.equals("aw_t1_tahta") && phase == Phase.PLINTH && current.isAir()` → boleh ditaruh (sel prasasti sendiri
   tetap selalu `OUTSIDE`). Situs darat tidak berubah.
3. `SitePlan`: konstruktor menerima `yP` dan kolom kaki; `inFootprint` (37.4.6); `footprint()` mengembalikan kotak
   pulau; `kakiBox()` baru.
4. `Templates.get`: cache `new TemplateSpec[SKY_LAST + 1]`; `case 11..19 -> AwanTemplates.build(t)`.
5. `TemplateSpec`/`Builder`: `sky(boolean)`, `boolean sky()`, `kaki(...)`, `@Nullable Zone kaki()`, `int skyFloor()`
   (y lokal blok terbawah dari dry run, disimpan saat `build()`).
6. `TemplateExtensions`: `0` = **hanya darat 1-9**; konstanta baru `SKY_ALL = -1` = semua langit 11-19;
   `register(owner, 1-9 | 11-19 | 0 | SKY_ALL, adder)`. Ini mengubah arti 0 hanya untuk templat yang belum pernah ada,
   jadi templat darat dan ekstensi DESIGN 36 tidak berubah.
7. `Validator.sky` (37.4.8); `TerraceFitter` memasang hanya `kaki` bila `spec.sky()`.
8. `BuildEngine`: peta `skies`; `steleOk` cabang tertambat; `Scheduler.next` gerbang `TETHER_MAX_TIER`; `ascend(owner,
   FootMode)`; `skySite(owner)`; `otherSites` menyertakan `skies`; `insideAnySite` menyertakan `skies`.
9. `CellIndex.rekey(UUID from, UUID to, int belowY)` (seksi milik `from` dengan `minY < belowY` → `to`; `belowY =
   Integer.MAX_VALUE` untuk semua).
10. `Showcase`: peta `skies` (simpan/muat, kunci `"skies"`), tidak dihitung `MAX_SHOWCASES`.
11. `Founding`: pratinjau langit (layar memilih keluarga, `yP`, geser); `PreviewData` + `skyY`, `kakiBox`, `family`;
    hologram digambar di ketinggian `yP` (pratinjau melihat kotak di udara dan kolom kaki).
12. `Upgrade`: menolak lintas keluarga; `Relocation`: menangani "Turun Gunung" (situs langit menjadi orphan).
13. `PlanAudit`: aturan audit langit (op di luar kolom/kaki, op > 24.000 per komponen, total > anggaran 37.4.7, air
    di luar kolam tertutup, blok terlarang `AwanPalette`, jangkar wajib: `tahta`, `arrival`, `library` ≤ 16,
    `formation` ≤ 24, `tower`, `formasi_awan`, `jaring_0..3`, `tangga_kaki`, `tangga_puncak`, `dermaga_*` (T7),
    `feri_atas` (T7), `gerbang_langit` (T9), `airterjun_*`, `meditasi_*`, jangkar 36 `kebun_*`, `tambang_*`, `panji_*`).
14. `BuildInfo`, `BuildCommands`, `SectBuild`, `HallBridge`: tampilkan keluarga, `/kultivasi aula langit ...` (37.7.9),
    jaminan naik (37.3.5 butir 6).
15. Klien `client/sectbuild/TemplateScreen`: tab "Darat / Awan", geser tinggi (langkah 4), geser `dx/dz`, ringkasan
    sesaji; `client/sectbuild/SiteHologram` menerima `skyY` dan kotak kaki.

#### 37.4.10 Ruang udara terlindung dan anti-grief (`sect/build/flow/RuangUdara`, 37B)

* **Volume** tiap situs langit: footprint + 8 horizontal × `[skyFloorY - 8, yP + above + 8]`, ditambah kolom kaki.
  Dipesan sejak pratinjau dikonfirmasi (mode tertambat), jadi orang lain tidak bisa mengisi ruang yang direncanakan.
* **Bukan anggota** (bukan pemilik, bukan anggota `Sects.sectOf(player)` yang sama, bukan op kreatif) di dalam volume:
  `UseBlockCallback` menolak menaruh blok dan memakai ember/pemantik (`InteractionResult.FAIL`), `PlayerBlockBreakEvents.
  BEFORE` menolak memecah, `UseItemCallback` menolak ember yang sasarannya di volume. Pesan aksi
  `kultivator.sekte_langit.udara.terlindung`. Tidak ada blok yang diubah oleh aturan ini.
* **Kehancuran**: `ProtectedZones.addZoneProvider` menambah volume ini sebagai zona terlindung (tidak pernah dihancurkan
  Mode Kehancuran siapa pun, termasuk pemilik, kecuali pemilik mengizinkan lewat sumpah Kehancuran yang ada untuk kolom
  di luar sel sekte).
* **Petir**: `ServerEntityEvents.ENTITY_LOAD` pada `LightningBolt` yang muncul di volume (bukan dari kode mod) →
  `setVisualOnly(true)` sebelum tick pertama: tidak ada api di atap kayu, tidak ada kerusakan. (Sekte langit "di atas
  badai".)
* **Gerbang tertutup** (T9 Gerbang Sembilan Langit atau T5 Formasi Agung aktif, sakelar `/kultivasi aula langit gerbang
  tutup|buka`, default buka): bukan anggota yang memasuki volume didorong keluar horizontal pelan (dorongan entitas
  0,6 blok/tick menjauhi pusat, seperti `CoreContext.push`) dan diberi `SLOW_FALLING` 100 tick; **tidak pernah**
  diteleport keluar di udara. Undangan `/kultivasi aula langit undang <pemain>` berlaku 1 hari dunia ("nama tercatat di
  daftar", ilham *Journey to the West* bab 4).
* Config `langit.lindungiUdara` (default `true`).

---------------------------------------------------------------------------------------------------------------------

### 37.5 Akses dan gerak

Ringkasan jalur naik/turun (semua ada sejak tingkat yang ditulis):

| Jalur | Tingkat | Siapa | Biaya | Catatan |
|---|---|---|---|---|
| Pedang terbang (`FlyingSword`) | selalu | pemain | Qi (yang ada) | jalur utama ilham Shushan |
| Kapal sekte (`KapalRohEntity`) | selalu (dok resmi T7) | pemain + murid | bahan bakar kapal (yang ada) | dermaga 37.5.4 |
| Formasi Pindah Awan | T1 (simpul langit), simpul tanah dibuat pemain | pemain + ≤ 4 murid | 1 Daya + 10 Qi, jeda 200 tick | 37.5.1 |
| Tangga Awan | T3 | semua (berjalan) | gratis | 37.5.2 |
| Feri Awan | T7 | anggota + murid | gratis | 37.5.5 |
| Terjun Awan (turun saja) | T5 | anggota | gratis | 37.5.6 |

#### 37.5.1 Formasi Pindah Awan (pasangan jangkar tanah ↔ langit)

* Spec baru `awan_transfer` (netId **470**, `Family.PERPINDAHAN`, `Grade.MISTERIUS`, `Scope.CORE`, radius 2,
  `pulseTicks 20`, efek = efek transportasi yang sama dengan `sect_transfer` (`CatalogB.TRANSPORT = "transport"`,
  `TransportNetwork.Effect`) dengan `param("tier", 0.0D)`, warna `0xCFE8FF`, `requires` kosong), didaftarkan di `formasi/FormasiB.init` (37M,
  satu baris) lewat kelas baru `formasi/catalog/CatalogLangit.register(SpecSink, EffectSink)`.
* Pola (`Grid(2, 2)` seperti M3): cincin `SMOOTH_QUARTZ` ('q'), sudut `AMETHYST_BLOCK` ('a'), mata `FORMATION_EYE`
  di tengah. Simpul **langit** dibangun `aw_t1_formasi_awan` (dipasang tidak aktif seperti komponen pola 6b,
  `EngineApi.formasiAnchors`, verifikasi); simpul **tanah** dibangun pemain sendiri dengan tangan (pemain menaruh bloknya,
  sah) di mana saja dalam `TransportNetwork.AWAN_RANGE = 128` blok horizontal dari prasasti (biasanya di Kaki Gunung
  atau di pelataran kaki Tangga Awan).
* Aturan tautan baru di `TransportNetwork.linked(level, from, to)`: bila salah satu ujung `awan_transfer`, tertaut
  bila pemilik sama, dimensi sama, satu ujung `inside` (wilayah aula langit) dan ujung lain dalam `AWAN_RANGE`
  horizontal dari `Halls.hallOf(owner).pos()` (Y bebas). Maks `Limits.AWAN_NODES = 2` simpul `awan_transfer` per pemilik.
  M3 dan L5 yang ada tidak berubah.
* Biaya `TransportNetwork.AWAN_COST = 1.0` Daya per perjalanan, kanal `TransportChannel.M3_CHANNEL` (60 tick, 30 bila
  dijaga), `QI_COST 10`, jeda `Stacking.TRANSPORT_M3_COOLDOWN` (200), `MAX_DISCIPLES 4` dalam `DISCIPLE_RANGE 4`, semua
  penolakan `TransportChannel.refusal` berlaku. Tiba: `Targeting.safeSpot`, `teleportTo`, `resetFallDistance()`
  (yang ada); tujuan belum dimuat → `DuniaTickets.touch(level, chunk, 1)` lalu tunggu ≤ 40 tick.
* Efek: pilar cahaya biru-putih dan pusaran awan di kedua ujung (op `FX_PINDAH`, 37.6).

#### 37.5.2 Tangga Awan dan Tangga Seribu

* **Berjalan**: heliks nyata 37.4.6 (A9 ≈ 292 anak tangga, 2-3 menit). Pelataran rehat tiap 24 blok naik. Pemain yang
  berjalan dari `tangga_kaki` sampai `tangga_puncak` tanpa terbang, tanpa menumpang dan tanpa teleport dalam satu
  perjalanan (pelacak 37M `LangitTanggaJejak`, batas 15 menit) mendapat advancement `sekte_langit/seribu_anak_tangga`.
* **Hari Penerimaan** (aula langit): calon tiba di `arrival_kaki` lewat `Halls.addArrivalSpotProvider` (37G,
  `LangitArrival`: untuk aula langit kembalikan `EngineApi.anchor(owner, "arrival_kaki")` bila komponen
  `aw_t3_tangga_awan` DONE, jika tidak `arrival` di pulau).
* **Sambungan DESIGN 36 Tangga Seribu** (modul 36 G): bila `sx_tangga_batu` DONE di kaki dan modul `tangga` aktif,
  pendakian ilusi diproyeksikan **di sepanjang heliks nyata**: `LangitTangga.path(UUID owner)` (37G) mengembalikan
  `List<Vec3>` 1001 titik (langkah 0-1000 dipetakan merata ke panjang heliks + jalan rongga sampai `tangga_puncak`).
  Pemakaian butuh hook kecil di modul 36 G: `TanggaService.setPathProvider(Function<UUID, @Nullable List<Vec3>>)`
  (verifikasi; bila belum ada, 37G **tidak** menyunting file 36, mencatat permintaan di `docs/progres/langit-G.md`, dan
  cadangannya: ilusi tetap di kaki seperti aturan 36). Calon yang mencapai langkah 1000 benar-benar tiba di gerbang
  langit: "tangga seribu akhirnya menuju tempat nyata".
* Calon yang diterima berjalan naik sebagai tindakan pertama ("Pendakian Pertama", `NaikTanggaGoal` di 37M): navigasi
  per titik `tangga_rehat_<i>`; macet > 60 s → dipindah ke titik rehat berikut (tanpa jatuh).

#### 37.5.3 Pedang terbang dan Panggung Pedang

* `FlyingSword` tidak diubah (tanpa batas tinggi; `FlightLease`). Turun dari pedang di atas pulau tetap memberi
  `SLOW_FALLING` (aturan lama) dan `JaringAwan` menangkap bila perlu.
* **Patroli pedang murid** (37M, `sekte/langit/gerak/PatroliPedang`): tiap 2-5 menit, 1-2 murid berjenjang ≥ Murid Inti dan ranah ≥
  `GOLDEN_CORE` lepas landas dari `panggung_pedang`, berputar mengelilingi pulau pada spline `yP + 12` selama 30-60 s
  lalu mendarat. Server menggerakkan murid (`setNoGravity(true)`, `noPhysics = true` selama terbang, `setPos` per tick
  sepanjang jalur yang dihitung sekali, selalu di luar kotak padat pulau), lalu mengembalikan fisika dan
  `resetFallDistance()`. Aktivitas `ACT_TERBANG_PEDANG = 25` (`DiscipleEntity.setActivity`, tidak disimpan); 37V
  mendaftarkan pose + pedang di bawah kaki lewat `SektePoses.register` (hook publik DESIGN 36, tanpa menyunting file).
  Maks 2 murid terbang per aula; berhenti saat penyerbuan, duel, atau `EffectQuality` Minimal di sisi klien tetap
  menggambar pedang sederhana.

#### 37.5.4 Dermaga kapal sekte

* `DermagaAwan(int x, int z, Direction keluar, KapalClass kelas, Lv lantai)`: dek kantilever selebar kapal + 2 dan
  sepanjang kapal + 2 (PERAHU 9×15, SEKTE 13×27, BAHTERA 19×39), lantai `SMOOTH_STONE_SLAB` di `Lv.at(-2)`, tepi
  `PALE_OAK_FENCE`, tiang tambat (`POLISHED_BLACKSTONE_WALL` + `ironChain`), `LanternPost` `STONE`, tanjakan ke plaza.
  Jangkar `dermaga_<i>` di tengah dek; data jangkar menyimpan kelas dan arah keluar (sufiks `_p/_s/_b` + `_n/_e/_s/_w`).
* Kapal mendarat dengan aturan `dockCheck` yang ada (heightmap: dek = tanah). Perubahan kecil di
  `dunia/kapal/KapalRohEntity` (37M): `sailHall` (OP_HALL 8) untuk aula langit menuju jangkar `dermaga_<i>` bebas yang
  kelasnya cukup (bukan ke prasasti), mendekat dari luar footprint pada ketinggian dek + `DOCK_HEIGHT`, lalu meluruskan
  yaw ke arah jangkar; satu kapal per dermaga (`LangitDermaga.occupied`). Tanpa dermaga yang cocok: kapal melayang di
  `yP + 8` di luar tepi dan pemain diberi tahu.
* Bajak awan yang menyerang aula langit merapat ke dermaga yang sama (37.7.5).

#### 37.5.5 Feri Awan

* Entitas baru `kultivator:feri_awan` (`sekte/langit/gerak/FeriAwanEntity extends Entity`, 3,0 × 1,2, `noPhysics`,
  `setNoGravity(true)`, **tidak disimpan** (`shouldBeSaved() = false`), pelacakan 10, interval 1), 4 kursi.
* Halte: `feri_atas` (T7, di pulau) dan `feri_bawah` (titik tanah 6 blok di depan pelataran kaki, hanya jangkar data,
  tanpa blok). Panggil dengan memakai lonceng (`BELL`) di halte atas atau di pelataran kaki (`UseBlockCallback`, sel
  lonceng milik sekte dalam 3 blok dari jangkar) atau `/kultivasi langit feri`. Feri muncul di halte, menunggu 30 s.
* Rute (dihitung sekali per panggilan, `FeriRute`): dari `feri_bawah` naik vertikal sampai `skyFloorY - 6`, keluar
  horizontal ke tepi + 4, naik ke tinggi halte, masuk 6 blok ke halte; tidak pernah melintasi kotak padat pulau.
  Kecepatan 0,6 blok/tick (A9 ≈ 17 s). Hanya anggota dan murid yang boleh naik. Turun di tengah jalan: dicegah untuk
  murid; pemain boleh (shift) dan mendapat `SLOW_FALLING` 240 tick seperti kapal, lalu `JaringAwan`.
* Tujuan belum dimuat → `DuniaTickets.touch`. Feri hilang 20 s setelah kosong. Maks 1 feri per aula.
* Murid yang berangkat misi (`sect/event/Expeditions`, `X`) **boleh** naik feri sebagai tampilan (37G, opsional,
  `langit.feriMisi`), tanpa menyunting `Expeditions`: G mendengar `SekteBus.MISSION_SENT`.

#### 37.5.6 Perlindungan jatuh: Jaring Awan

Dua lapis, keduanya hanya entitas/efek, tanpa blok:

**(a) Penahan dasar `LangitJatuh`** (37M, selalu aktif untuk situs langit sejak mode tertambat):
* Tiap 5 tick, untuk entitas terlindung (pemain pemilik/rekan sekte, murid DISCIPLE milik pemilik, hewan jinak milik
  anggota) yang horizontal dalam footprint + 24 dan di bawah `yP + above`:
  * Catat **titik aman**: tiap 20 tick bila `onGround()` di dalam kotak pulau → `AMAN.put(uuid, blockPosition())`
    (`Map<UUID, BlockPos>`, dibersihkan saat keluar volume).
  * Bila tidak di tanah, `getDeltaMovement().y < -0.6`, tidak terbang (`FlightLease.holder` kosong, bukan
    `isFallFlying()`), tidak menumpang, dan `getY() < skyFloorY + 2` → `SLOW_FALLING` 60 tick (diperbarui).
  * Bila sudah 24 blok di bawah `skyFloorY` (atau 8 blok di atas tanah, mana yang lebih dulu) dan **tidak** sedang
    Terjun Awan → **Tangkap**: `teleportTo` titik aman terakhir (atau `arrival`), `resetFallDistance()`, `Slowness` I
    20 tick, efek `FX_TANGKAP` di kedua titik, suara `SoundEvents.BREEZE_IDLE_AIR`, baris aksi
    `kultivator.sekte_langit.jatuh.tangkap`. Tujuan dimuat (pulau tempat ia tadi berdiri); bila tidak, tiket
    `DuniaTickets.touch`.
* Bukan anggota, perampok, bajak awan, hewan liar: **tidak** dilindungi. Perampok yang jatuh dihitung "dibuang ke awan"
  (37.7.5).

**(b) Formasi `jaring_awan`** (T5, netId **471**, `Family.PELINDUNG`, `Grade.MISTERIUS`, `Scope.TERRITORY`, efek baru
`formasi/effect/b/JaringAwanEffect implements FormationEffect`, pola = 4 Tiang Jaring di jangkar `jaring_0..3` + mata di
cakram, didaftarkan oleh `CatalogLangit`). Saat berjalan:
* Memperluas perlindungan ke **tamu**: pemain yang diundang (`/kultivasi aula langit undang <pemain>`, 1 hari dunia)
  dan rombongan NPC sekte NPC ber-`Sikap.SEKUTU` (`DiplomasiService.stance`) yang sedang berkunjung.
* **Terjun Awan**: anggota yang meninggalkan tepi pulau sambil menahan shift (deteksi saat titik aman terakhir < 2 detik
  dan `isShiftKeyDown()`) tidak ditangkap, melainkan meluncur turun dengan `SLOW_FALLING` sampai tanah dan kebal
  kerusakan jatuh (`ServerLivingEntityEvents.ALLOW_DAMAGE` milik 37M untuk tag `langit.terjun`); cara turun cepat dan
  indah.
* Upkeep: `Grade.MISTERIUS` (6 Daya/jam), bisa dijaga murid seperti formasi lain; mati (tanpa Daya) → lapis (a) tetap
  ada, hanya fitur (b) hilang.

#### 37.5.7 Kehidupan murid di atas

* **Pos dan jangkar** sama seperti darat (`post_*`, `arrival`, `library`, `formation`, `tower`, `treasury`,
  `main_hall`), sehingga `Duties`, kebun DESIGN 36, tambang, tribun, altar, bendera berjalan tanpa perubahan.
* **Tidak pernah jatuh**: (1) navigasi darat vanilla murid tidak turun lebih dari 3 blok, (2) `PagarTepi` menutup tepi
  kecuali celah jembatan/dermaga/gerbang (celah selalu bersambung ke lantai), (3) `LangitJatuh` menangkap bila terpental
  dalam pertempuran, (4) feri/patroli pedang digerakkan server.
* **Kegiatan baru** (id aktivitas transient `LangitIds.ACT_*`; 25 milik 37M, 26-27 milik 37G `LangitMurid`; pose 37V
  lewat `SektePoses.register`):
  * `ACT_TERBANG_PEDANG = 25` (patroli, 37.5.3).
  * `ACT_JAGA_TEPI = 26`: murid mode `GUARD_HALL` berpatroli di jalur tepi (`AwanLayout.jalurTepi`, titik di dalam
    pagar), berhenti memandang laut awan.
  * `ACT_MEMANDANG = 27`: saat fajar/senja 1-3 murid duduk di `meditasi_<i>` memandang laut awan (gelembung pikiran
    DESIGN 36 P bila aktif: "Awan seperti lautan...").
* **Tempat meditasi**: `meditasi_<i>` + `QiSeat` T8; faktor situs langit 37.7.1 juga berlaku untuk murid yang bermeditasi
  di sana bila sistem kultivasi murid punya hook situs (verifikasi `Disciples`/`DiscipleCultivation`; tanpa hook, hanya
  pemain).
* Murid baru, tamu NPC, utusan dan perampok **selalu** dimunculkan di jangkar di atas lantai pulau (atau di kaki), tidak
  pernah di udara.

---------------------------------------------------------------------------------------------------------------------

### 37.6 Suasana, visual dan suara (modul 37V, paket `dev.kultivator.client.sekte.langit`)

#### 37.6.1 Sinkron dan aturan umum

* Payload `LangitPayloads.Sync(List<SkyHall> halls)` (server → klien, didefinisikan di inti 37B, dikirim 37G
  `LangitSyncServer`) tiap kali pemain masuk/keluar 512 blok horizontal dari asal aula langit, saat tingkat berubah, dan
  tiap 200 tick bila ada perubahan. `record SkyHall(UUID owner, BlockPos origin, int yP, int skyFloorY, BoundingBox
  box, int tier, int color, int domeRadius, int flags, List<LangitPayloads.Anchor> anchors)`; `Anchor(String id,
  BlockPos pos)` hanya untuk jangkar visual (`airterjun_*`, `jaring_*`, `gerbang_langit`, `dermaga_*`, `feri_atas`,
  `tahta`, `meditasi_*`); `flags`: bit 0 tertambat, 1 Formasi Agung aktif, 2 jaring aktif, 3 gerbang tertutup, 4 pesta.
  Maks 4 aula per pemain, payload ≤ 8 KiB.
* Semua penggambar didaftarkan pada event pengumpulan render yang sama dengan `client/dunia/langit/LangitRenderer`
  (`LevelRenderEvents.COLLECT_SUBMITS`), memakai sink `entityTranslucentEmissive` (pola `LangitSink`) atau
  `KosmikTypes.add` untuk aditif, mengambil quad lewat `EpicBudget.takeQuads(n)`, **tidak** menggambar saat
  `ShaderCompat.shadowPass()`, dan memotong jumlah × `EpicBudget.PACK_QUADS` (0,6) bila `ShaderCompat.packOn()`.
* Hanya digambar bila kamera dalam 384 blok horizontal dari asal aula langit yang disinkron; prioritas bila anggaran
  habis: laut awan > air terjun > kubah > sungai bintang > bangau > kelopak > lentera > halo.
* `EpicBudget.softGeometry()` (bingkai lambat) → semua turun ke angka Minimal sampai pulih.
* Tekstur baru 37V di `assets/kultivator/textures/sekte/langit/`: `halo.png` (128×128), `bangau.png` (64×32, 2 bingkai),
  `sungai_bintang.png` (512×128), `lentera.png` (32×32), `pilar.png` (16×64). Tekstur dipakai ulang:
  `textures/misc/kosmik/surga/cloud_sea_256.png`, `cloud_sea_512.png`, `cloud_puff.png`, `shaft.png`, `petal.png`,
  `waterfall.png`, `gate_glow.png`. Gambar baru dibuat skrip `tools/gen_langit_tex.py` (PNG deterministik, tanpa
  dependensi selain `zlib`/`struct`), dijalankan sekali oleh 37V.

#### 37.6.2 Laut awan di bawah (`LautAwanRenderer`)

* Bidang horizontal berpusat di kamera (grid log-polar seperti `SurgaSky` BANK) pada `yAwan = clamp(skyFloorY - 10,
  150, 205)` dan lapis tambahan di `yAwan - 7`, `yAwan - 15`.
* Lapis × cincin × segmen: Minimal 1 × 4 × 8, Normal 2 × 6 × 12, Maksimal 3 × 8 × 16. Tekstur `cloud_sea_256`
  (Minimal/Normal) atau `cloud_sea_512` (Maksimal); UV dari x/z dunia (bukan kamera, agar awan diam relatif dunia),
  digulir `0,6 blok/s` searah angin musiman (dari `seed` aula, berubah tiap hari dunia).
* Alpha `0,85 / 0,62 / 0,50` per lapis, memudar di cincin luar (96..384 blok) dan saat kamera di bawah `yAwan` (dilihat
  dari bawah hanya Normal+ dengan alpha × 0,5). Badai: warna digelapkan 35%. Siang-malam: warna dari
  `level.getSkyColor` × emas saat fajar (37.6.3).
* Awan vanilla (192,33) tidak diubah; laut awan kita menambah kedalaman di sekitarnya.

#### 37.6.3 Cahaya fajar, halo pelangi, sungai bintang

* **`CahayaFajar`**: waktu dunia 23000-1000 dan 11500-13000, kamera di pulau: 0/5/9 berkas cahaya aditif (`shaft.png`)
  dari arah matahari menyapu laut awan, serta pewarnaan emas (vertex color `0xFFD27A`, kekuatan ≤ 0,45) pada lapis laut
  awan teratas.
* **`HaloPelangi`** (Cahaya Buddha, ilham Emei): saat matahari rendah di belakang kamera (`dot(arahPandang,
  -arahMatahari) > 0,9`), kamera di atas laut awan dan ≤ 48 blok dari tepi: satu quad cincin pelangi (`halo.png`, alpha
  0,35) di titik antimatahari pada bidang laut awan, ukuran sudut tetap 12°. Muncul di 30% fajar (diundi dari hari dunia,
  semua pemain melihat hari yang sama), 60 s. Minimal: mati. Advancement klien→server `sekte_langit/cahaya_buddha`
  (payload kecil `LangitPayloads.Saw(int what)`, server memeriksa waktu dan posisi).
* **`SungaiBintang`**: malam (13000-23000), cuaca cerah, kamera di pulau: pita bima sakti (`sungai_bintang.png`) sebagai
  busur 8/16/24 quad pada radius `min(0,8 × jarak kabut, 480)`, alpha × kecerahan bulan; 0/20/60 bintang berkedip
  (sprite `gate_glow.png` kecil).

#### 37.6.4 Air terjun kabut di tepi (`AirTerjunKabut`)

* Di tiap jangkar `airterjun_<i>` (arah dari sufiks): pita quad bertekstur `waterfall.png` lebar 2-3, panjang 12 /
  24 / 40 blok (Minimal/Normal/Maksimal) jatuh dari takik tepi, tekstur digulir ke bawah 4 blok/s; 40% bawah memudar
  menjadi kabut: 2/6/12 gumpalan `cloud_puff.png` berputar pelan. Emitor terlihat maks 4/8/8, hanya dalam 128 blok.
* Kolam kecil di atas pulau (`aw_t4_kolam`) adalah air nyata **tertutup** (Pond + Waterfall yang ada, di dalam pulau);
  air terjun di tepi **tidak** pernah air nyata (aturan 21.8 "No water flows off islands").
* Suara: `SoundEvents.WEATHER_RAIN_ABOVE` volume 0,2 nada 0,7 tiap 40 tick bila ≤ 20 blok.

#### 37.6.5 Kubah formasi, bangau, kelopak, lentera, efek sesaat

* **`KubahFormasi`**: saat flag Formasi Agung aktif: cincin aura di tepi pulau + kilau kubah setengah bola renggang
  (garis lintang 1/3/6 cincin) radius `domeRadius`, warna = warna lambang DESIGN 36 S (`LambangService`, fallback
  `HALL_COLOR 0x8FE3C8`), berdenyut 4 s. Tidak menggandakan kubah formasi yang sudah digambar `FormasiFx`: bila
  `FormasiFx` sedang menggambar inti Formasi Agung aula ini, `KubahFormasi` hanya menggambar cincin tepi.
* **`KawananBangau`**: 0/5/12 bangau dalam 0/1/2 kawanan, sprite 2 bingkai sayap pada lintasan Lissajous mengelilingi
  pulau di `yP + 10..30`, siang saja, dalam 96 blok. Suara `SoundEvents.PARROT_FLY` 0,3 nada 0,6 bila ≤ 24 blok.
* **`KelopakJatuh`**: 0/40/120 kelopak (`petal.png`) dari pohon plum dan `aw_t9_finale`, dalam 48 blok, melayang
  tertiup angin melewati tepi dan memudar di bawah.
* **`LenteraLangit`**: malam pesta (flag bit 4: 3 malam setelah Upacara Naik, malam tahun baru dunia tiap 96 hari, dan
  setelah turnamen DESIGN 36 T) 0/10/30 lentera hangat naik pelan dari plaza.
* **Efek sesaat** (op `LangitPayloads.Fx(int op, BlockPos a, BlockPos b)`): `FX_PILAR = 0` (pilar penambat tertambat,
  terus-menerus selama flag tertambat, `pilar.png`), `FX_ARRIVE = 1` (gumpalan awan murid tiba), `FX_TANGKAP = 2`
  (jaring awan menangkap), `FX_PINDAH = 3` (formasi pindah), `FX_FERI = 4` (feri berangkat/tiba), `FX_NAIK = 5`
  (upacara naik: cincin cahaya dari tahta, 9 cincin warna Sembilan Langit naik 40 blok selama 8 s), `FX_BAJAK = 6`
  (bajak awan menembus laut awan).
* **Kebal cuaca (tampilan)**: mixin klien `client/mixin/WeatherEffectRendererMixin` (37V) pada
  `WeatherEffectRenderer.extractRenderState(ClientLevel, float, Vec3, WeatherRenderState)` (`client/renderer/
  WeatherEffectRenderer.java:68`): setelah ekstraksi, kosongkan daftar kolom hujan/salju di `WeatherRenderState` bila
  kamera di dalam volume aula langit dan di atas `yAwan`; dan `ClientLevel.tickWeatherEffects()`
  (`client/multiplayer/ClientLevel.java:362`, partikel percik dan suara `WEATHER_RAIN_ABOVE`) dibatalkan pada kondisi
  yang sama. Config klien `langit.cuacaKebal` (default `true`).

#### 37.6.6 Suara (semua event vanilla, terverifikasi di `sounds/SoundEvents.java`)

| Suara | Event | Volume / nada | Kapan |
|---|---|---|---|
| Angin langit | `ELYTRA_FLYING` | 0,08-0,18 / 0,5 | tiap 120-200 tick bila kamera ≤ 6 blok dari tepi atau di jembatan |
| Lonceng gerbang | `BELL_RESONATE` | 0,6 / 0,8 | sekali saat fajar (waktu 0) dari `gerbang_langit`/`main_hall`; Upacara Naik |
| Lonceng angin | `AMETHYST_BLOCK_CHIME` | 0,3 / 1,0-1,4 | acak 1:200 tick dalam 12 blok dari atap pagoda |
| Bangau | `PARROT_FLY` | 0,3 / 0,6 | kawanan ≤ 24 blok |
| Air terjun | `WEATHER_RAIN_ABOVE` | 0,2 / 0,7 | tiap 40 tick ≤ 20 blok |
| Pindah formasi | `WIND_CHARGE_BURST` (`.value()`) | 0,7 / 1,2 | tiba |
| Jaring awan | `BREEZE_IDLE_AIR` | 0,8 / 1,0 | menangkap |
| Upacara naik | `BELL_RESONATE` + `AMETHYST_BLOCK_CHIME` | 1,0 | `FX_NAIK` |

Maks 4 suara ambien langit per 5 s per klien; kategori vanilla (`AMBIENT`, `WEATHER`, `NEUTRAL`) dihormati. Suara
suara-tokoh (`Voice.say`) hanya memakai kunci yang ada (`VoiceEvents.exists`), pola cadangan `KapalVoice`; 37 **tidak**
menyunting `sounds.json` atau `gen_voices.py`.

#### 37.6.7 Anggaran per `EffectQuality`

| Efek | Minimal | Normal | Maksimal | Sumber anggaran |
|---|---|---|---|---|
| Laut awan (quad) | 32 | 144 | 384 | `EpicBudget.takeQuads` |
| Air terjun (segmen × emitor) | 4 × 4 | 8 × 8 | 16 × 8 | quad |
| Gumpalan kabut / emitor | 2 | 6 | 12 | quad |
| Kubah (cincin) | 1 | 3 | 6 | quad (≤ 64 per cincin) |
| Sungai bintang + bintang | 8 + 0 | 16 + 20 | 24 + 60 | quad |
| Berkas fajar | 0 | 5 | 9 | quad aditif |
| Halo | 0 | 1 | 1 | quad |
| Bangau | 0 | 5 | 12 | quad |
| Kelopak | 0 | 40 | 120 | quad |
| Lentera | 0 | 10 | 30 | quad |
| **Batas total langit per bingkai** | **400** | **1.200** | **2.600** | di bawah `EpicBudget.QUADS {400, 1500, 3000}` |
| Partikel vanilla (efek sesaat) | 40 | 300 | 800 | `FxBudget.take(n, Priority.DETAIL)` |

Pengaturan "Partikel" vanilla = Minimal memaksa kolom Minimal untuk kelopak, kabut dan lentera.

---------------------------------------------------------------------------------------------------------------------

### 37.7 Gameplay (modul 37G kecuali disebut lain)

#### 37.7.1 Qi di ketinggian (dibatasi seperti faktor situs yang ada)

* `sekte/langit/main/LangitSites` mendaftar `SpiritSites.addSiteProvider(player -> ...)` dengan
  `ExtraSite("sekte_langit", f, null, Component.translatable("kultivator.sekte_langit.situs.sekte_langit"))` bila:
  pemain di tanah, di dalam kotak pulau aula langit yang tidak tertambat (`y ≥ skyFloorY`, dalam footprint), langit
  terlihat di kepala, **dan** pemain anggota sekte pemilik (bukan anggota hanya mendapat `MOUNTAIN` vanilla mod).
* `f = min(langit.faktorQiMaks 1,25, 1,05 + 0,025·(tier - 1) + (Formasi Agung aktif ? 0,05 : 0))`: T1 1,05, T5 1,15
  (+0,05), T9 1,25 (+0,05 dijepit 1,25).
* Bertumpuk perkalian dengan `MOUNTAIN 1,5` yang sudah otomatis (y ≥ 120): pulau T9 = 1,5 × 1,25 = **1,875**. Sekte
  darat di gunung = 1,5; di dataran = 1,0. Semua tetap di bawah `SpiritSites.CAP 4.0` dan `Meditation.MULTIPLIER_CAP
  3.5`; `SpiritVeins` tidak diubah (urat di langit sama dengan urat tanah di bawahnya).
* **Qi Fajar**: di `meditasi_<i>` (≤ 2 blok) saat 23000-1000: `ExtraSite("qi_fajar", 1.05, null, ...)`.
* **Bulan di atas awan**: situs `FULL_MOON` vanilla mod gagal saat hujan; di pulau langit saat bulan purnama, gelap dan
  hujan: `ExtraSite("bulan_langit", 1.5, Dao.ICE, ...)` (menggantikan, tidak menumpuk dengan `FULL_MOON` karena keduanya
  tidak pernah benar bersamaan).

#### 37.7.2 Kebal cuaca

* Petir di volume → `setVisualOnly(true)` (37.4.10): tidak ada api, tidak ada luka.
* Salju tidak menumpuk dan kuali tidak terisi hujan di atas pulau: mixin server `mixin/ServerLevelPrecipitationMixin`
  (37G) membatalkan presipitasi blok (`ServerLevel.tickPrecipitation(BlockPos)`, `ServerLevel.java:585`) bila posisi di dalam volume aula
  langit. Ini mencegah perubahan blok vanilla, bukan perubahan blok oleh kita. Config `langit.kebalSalju`.
* Hujan tidak digambar dan tidak terdengar di atas laut awan (mixin klien 37V, 37.6.5).
* Narasi panduan: "Di atas awan, langit selalu cerah; badai mengamuk di bawah kakimu."

#### 37.7.3 Kesengsaraan langit dilihat dari atas

* Saat `Tribulation` berjalan di mana saja ≤ 256 blok horizontal dan ≥ 40 blok di bawah pemain yang berada di pulau
  langit, 37G mengirim `Fx(FX_SENGSARA_BAWAH = 7, pos, ...)` ke pemain itu; 37V menggambar pusaran gelap berputar di laut
  awan di atas titik kesengsaraan, kilat (quad aditif) memancar ke atas menembus awan tiap sambaran, suara guntur
  vanilla teredam. Deteksi: `Tribulation.addStrikeFactorProvider(StrikeFactor)` dengan `StrikeFactor.factor(ServerPlayer
  p, Rank target, int strikeIndex, boolean last)` yang melihat tiap sambaran dan mengembalikan 1,0 (hanya kesengsaraan
  pemain; kesengsaraan murid tidak terdeteksi kecuali ada pendengar lain).
* **Saksi Kesengsaraan** (`langit.sengsaraSaksi`): anggota di pulau yang menyaksikan (dalam 256 blok, menghadap ke bawah
  ≥ 30° ke arah titik, ≥ 10 s) mendapat +2% XP tahap saat ini, maks 1 per kesengsaraan dan 3 per hari dunia; murid
  yang `ACT_JAGA_TEPI` mendapat jumlah setara untuk murid. Baris aksi
  `kultivator.sekte_langit.sengsara.saksi`.
* Kesengsaraan pemain **di** pulau: awan omen tetap di `pos + 22` (di atas pulau). Opsi `langit.sengsaraLebihKeras`
  (default `false`): kekuatan sambaran × 1,10 ("lebih dekat ke Langit") lewat `StrikeFactor` yang sama.

#### 37.7.4 Peristiwa khusus (`LangitPeristiwa`)

Diperiksa sekali per hari dunia (waktu 6000) per aula langit yang tidak tertambat, hanya bila pemilik atau rekan sekte
online dalam 160 blok; maks 1 peristiwa per 3 hari per aula; semua bisa dimatikan.

| Peristiwa | Syarat | Peluang | Isi |
|---|---|---|---|
| **Bajak Awan** | T ≥ 3, `langit.bajakAwan` | 1 per `langit.bajakHari` (8) hari rata-rata | 1/2/3 `KapalBajakEntity` (T3-T5/T6-T8/T9) muncul dari laut awan (`FX_BAJAK`), menahan diri di dermaga atau tepi, menurunkan regu `RogueCultivators.spawnBand(level, titik, tiers, realm, ROLE_AMBUSH, home, onCleared)` di jangkar `dermaga_<i>`/`darat_<i>` (tiers dari `SectRaids` gelombang 2). Menang: 150 renown, peti barang rampasan (tabel barang `Voyages` bajak, verifikasi) ke Gudang. Kalah/tak ada yang melawan 10 menit: bajak pergi membawa 5% satu kategori Bahan (maks 300 unit). |
| **Raksasa Longbo** | A7+ T ≥ 8, `langit.longbo` | 1 dari 4 Bajak Awan | bos `RogueCultivatorEntity` tier 4 dengan `Attributes.SCALE` 2,5 dan nama "Raksasa Longbo", mendarat di dekat kepala kura-kura, ingin "mencuri kura-kura penyangga" (*Liezi*). Menang: 400 renown, barang `kultivator:cangkang_kura_langit` (trofi, bahan altar), advancement `longbo`. |
| **Utusan Istana Langit** | A7+ T ≥ 9 Gerbang Sembilan Langit, `langit.utusanLangit` | 1 per 12 hari | saat senja berkas emas turun di `gerbang_langit`, utusan (NPC `dunia/kota/KultivatorNpc` peran `NpcRole.UTUSAN` yang sudah punya tab `KotaNet.TAB_TITAH`, verifikasi
  cara memunculkannya; cadangan: tubuh `Disciples.spawnUnowned(..., Role.RIVAL, anchor)` berjubah emas, tidak memusuhi) membacakan titah dengan tombol chat: **[Terima Titah]** (tugas 3 hari: kalahkan 12 kultivator nakal atau setor 8 batu roh `HIGH`; hadiah 500 renown, gelar sekte "Diakui Langit" 30 hari di kartu hub, 1 pil dari tabel hadiah turnamen), **[Tolak dengan hormat]** (tidak apa-apa), **[Tantang Langit]** (Murka Langit: `purba/HeavenlyWrath.start(player, false)` senja berikutnya; metode itu kini
  paket-privat, 37G menjadikannya `public` (satu kata); menang = 800 renown). Tiga penolakan beruntun: kesengsaraan berikut ketua bersambaran × 1,10 (`StrikeFactor`). |
| **Tamu Sekte Langit** | ada sekte langit NPC ber-`Sikap` ≥ `RAMAH` | 1 per 10 hari | feri awan tamu merapat, 2-3 tubuh RIVAL berlambang sekte NPC berkunjung 5 menit, standing +3; dengan `SEKUTU` membawa hadiah herba langit. |
| **Pesta Lentera** | 3 malam setelah naik; tiap 96 hari dunia | pasti | flag pesta (lentera langit 37.6.5), murid `ACT_MEMANDANG`. |

#### 37.7.5 Penyerbuan sekte untuk langit

* Hook baru di `musuh/SectRaids` (37G; file CRLF milik modul 36 D, disunting **hanya setelah** `docs/progres/sekte-D.md`
  menyatakan DONE, yang sudah benar per 2026-10-22): `public interface SpawnPointProvider { @Nullable Vec3 spawn(UUID
  owner, ServerLevel level, int wave, RandomSource random); }`, `SectRaids.addSpawnPointProvider(SpawnPointProvider)`,
  dipanggil di awal `spawnPoint` sebelum pencarian `dryLand`.
* `LangitSerbu` (37G) untuk aula langit: titik = jangkar `dermaga_<i>` atau `darat_<i>` (titik pendaratan di dalam pagar
  tepi, `AwanLayout.titikDarat`) acak; untuk tiap gelombang satu `KapalBajakEntity` dimunculkan sebagai pengangkut visual
  (menahan diri di samping titik selama gelombang, pergi saat gelombang habis). Gelombang ≥ 3 menyertakan **perampok
  pedang**: 1-2 perampok yang meluncur dari laut awan ke tepi (digerakkan server seperti patroli 37.5.3), lalu AI biasa.
* Meter pembobolan, gelombang, waktu, hasil: **tidak berubah**. Perampok yang jatuh dari pulau (y < `skyFloorY - 16`)
  dihapus dan dihitung tewas ("dibuang ke awan", baris log).
* Penyerbuan data (pemilik tak ada): `repelChance` + 0,05 untuk aula langit (sulit dicapai), tetap dijepit 0,95.
* Perang sekte NPC (DESIGN 36 `Perang` lewat `SectRaids.Director`) memakai jalur kedatangan yang sama.

#### 37.7.6 Sekte langit NPC (worldgen, diplomasi DESIGN 36)

* **Rencana**: `sekte/langit/npc/LangitNpcPlan.of(long seed)` memilih dari `GerbangPlan.of(seed).entries()` yang
  `tier ≥ 7`, diurutkan hash, maks `langit.sekteNpcMaks` (default 2); istana langitnya tepat di atas chunk gerbangnya
  (`chunkX, chunkZ` entri), Y mulai dari hash dalam 204-228. Sekte itu tetap sekte DESIGN 36 yang sama (nama, lambang,
  tier, standing, perang); ia hanya punya "Istana Awan" juga.
* **Struktur** (`tools/gen_sekte_langit.py`, 37G, memakai `structgen.Piece` dan `nbt.Template`, ≤ 48 per sumbu x/z per
  potongan): kolam `kultivator:sekte_langit/inti` (pulau 48×52×48, 2 varian gaya, rumus bentuk sama dengan `PulauAwan`
  37.4.2 ditulis ulang di Python, bagian dalam perut `structure_void`), `.../satelit` (3 varian 24×28×24: kebun, alkimia,
  arena), `.../jembatan` (penghubung jigsaw), `.../gerbang` (gerbang 3 atap). Jangkar `structgen.anchor`:
  `sektelangit/gerbang`, `sektelangit/murid` (≤ 6), `sektelangit/ketua`, `sektelangit/dermaga`.
* **JSON**: `data/kultivator/worldgen/structure/sekte_langit/istana_langit.json`: `minecraft:jigsaw`, `start_pool
  "kultivator:sekte_langit/inti"`, `size 3`, `max_distance_from_center 80`, `step "surface_structures"`,
  `start_height {"type":"minecraft:uniform","min_inclusive":{"absolute":204},"max_inclusive":{"absolute":228}}`
  (`heightmap=None` → tanpa `project_start_to_heightmap`), `terrain_adaptation "none"`, biomes
  `#kultivator:has_structure/sekte_langit` (semua bioma darat Overworld), `spawn_overrides` monster kosong. Puncak
  tertinggi ≤ 228 + 60 = 288 < 319.
* **Penempatan**: `sekte/langit/npc/LangitSektePlacement extends RandomSpreadStructurePlacement` (field codec `index`
  0-1; `isPlacementChunk` benar hanya untuk chunk entri ke-`index` dari `LangitNpcPlan`), didaftarkan
  `Registry.register(BuiltInRegistries.STRUCTURE_PLACEMENT, Kultivator.id("sekte_langit"), CODEC)`; structure set
  `sekte_langit/istana_langit_0` dan `_1` (salt 360921, 360922; rentang 3609xx milik sekte). Pack bawaan
  `kultivator:sekte_langit` didaftarkan (pola `GerbangSekte`) hanya bila `langit.aktif && langit.strukturNpc`.
  **Hanya chunk baru**; `uji langit_sekte npc daftar` menyebut bila chunk-nya sudah ada sebelum pack aktif.
* **Penghuni**: `LangitNpcAnchors implements DuniaAnchors.AnchorHandler` (modul `"sektelangit"`, `radius()` 64):
  `loaded` memunculkan tubuh RIVAL ber-lambang sekte (pola `AnggotaSekte`, maks `DiplomasiConfig.anggotaMaks`) di
  jangkar `murid` di atas lantai; ketua hanya saat perang. Tubuh NPC di pulau NPC dijaga jatuh dengan logika `LangitJatuh`
  yang dikunci ke kotak jangkar (`JaringNpc`). Zona `DuniaAnchors.protectedNear` (radius 64) melindungi dari Kehancuran.
* **Kunjungan dan diplomasi** (hook `DiplomasiService`): mendekat ≤ 96 blok pertama kali → advancement `temu_langit` +
  baris chat nama sekte; bila Diplomasi punya kontak publik (verifikasi) dipanggil, jika tidak hanya advancement.
  Sikap memengaruhi: `SEKUTU` → **Hak Labuh** (kapal pemain boleh berlabuh di `sektelangit/dermaga`; murid sekutu ikut
  bertahan saat Bajak Awan, memakai `penjagaSekutu` yang ada), tamu bulanan; `BERMUSUHAN`/`PERANG` → bajak awan dari
  sekte itu (Bajak Awan bernama sekte itu, standing -2 per serangan yang kita tahan); menang perang = mengalahkan
  `KetuaSekte` di istana langitnya (aturan DESIGN 36). Hadiah/pakta/tuntutan memakai `DiplomasiService.adjust` dan aksi
  Diplomasi yang ada; 37 tidak menambah aksi diplomasi baru.

#### 37.7.7 Advancement (`data/kultivator/advancement/sekte_langit/*.json`, pola `docs/ADVANCEMENT_TEMPLATE.json`)

Parent `kultivator:root`, kriteria `minecraft:impossible`, diberikan `Advancements.grant(player, "sekte_langit/<nama>")`;
kunci `advancement.kultivator.sekte_langit.<nama>.title|description` di fragmen `sekte_langit`.

| Nama | Ikon | Kapan |
|---|---|---|
| `rancang` | `minecraft:feather` | pratinjau langit dikonfirmasi pertama kali |
| `tapak_awan` | `minecraft:calcite` | T1 situs langit selesai |
| `naik` | `minecraft:lodestone` | Upacara Naik |
| `seribu_anak_tangga` | `minecraft:quartz_stairs` | berjalan kaki sampai puncak (37.5.2) |
| `pindah_awan` | `minecraft:ender_pearl` | perjalanan Formasi Pindah Awan pertama |
| `feri` | `minecraft:white_wool` | naik Feri Awan |
| `tertangkap` | `minecraft:phantom_membrane` | Jaring Awan menangkap |
| `terjun_awan` | `minecraft:elytra` | Terjun Awan sampai tanah |
| `cahaya_buddha` | `minecraft:glowstone_dust` | melihat Halo Pelangi |
| `bajak_awan` | `minecraft:iron_sword` | menahan Bajak Awan |
| `longbo` | `minecraft:turtle_scute` | mengalahkan Raksasa Longbo |
| `utusan_langit` | `minecraft:gold_ingot` | menyelesaikan titah |
| `temu_langit` | `minecraft:spyglass` | menemukan sekte langit NPC |
| `sekutu_langit` | `minecraft:amethyst_shard` | bersekutu dengan sekte langit NPC |
| `sembilan_langit` | `minecraft:nether_star` | T9 situs langit selesai |

#### 37.7.8 Kitab Panduan

* Bab baru 17 "Sekte Langit" (indeks 16): 37G menyunting `guide/GuideChapters` (`CHAPTER_COUNT = 17`, langkah ditambah
  **di akhir** daftar karena bit `GuideSync` mengikuti urutan). `client/guide/GuideBook` membaca `GuideChapters.chapters()` secara dinamis; bila
  layar ternyata memakai larik ukuran tetap 16 (verifikasi `GuideScreen`), langkah dipindah ke bab 16 (indeks 15) dengan id yang sama `c16.langit_*` dan
  `CHAPTER_COUNT` tidak diubah.
* Langkah (`step(16, nama, petunjuk, cek)`): `syarat` (`/kultivasi aula langit`, `hall_level:6|tier:6`), `rancang`
  (`adv:sekte_langit/rancang`), `tapak` (`adv:sekte_langit/tapak_awan`), `naik` (`adv:sekte_langit/naik`), `formasi`
  (`adv:sekte_langit/pindah_awan`), `tangga` (`adv:sekte_langit/seribu_anak_tangga`), `jaring`
  (`adv:sekte_langit/tertangkap|adv:sekte_langit/terjun_awan`), `feri` (`adv:sekte_langit/feri`), `bajak`
  (`adv:sekte_langit/bajak_awan`), `temu` (`adv:sekte_langit/temu_langit`), `sembilan`
  (`adv:sekte_langit/sembilan_langit`).
* Teks `kultivator.guide.c17.<nama>.title|body`, `kultivator.guide.chapter.17` dan `.desc` di
  `lang-src/sekte_langit_panduan.*`; isi menjelaskan dua jalan, sesaji, membawa prasasti, cara turun (Terjun Awan,
  feri), dan bahwa air terjun tepi hanyalah kabut.

#### 37.7.9 Kartu hub, layar, perintah, config

* **Kartu hub**: `HubCards.register(HubNav.SEKTE, "sekte_langit", Glyphs.BUILD, lock, action)` (37G klien
  `client/sekte/langit/ui/LangitHub`); `lock` → `kultivator.hub.card.sekte_langit.lock` bila pemain tanpa aula;
  `action` membuka `LangitScreen` (status: keluarga dan tingkat, `yP`, tertambat/berdiri, sesaji, jaminan, gerbang
  tertutup, peristiwa berikut, sekte langit NPC yang diketahui; tombol: Rancang (membuka tab Awan layar templat lewat
  `Halls.OP_OPEN` halaman Bangun), Feri, Gerbang, Undang). Kunci `kultivator.hub.card.sekte_langit` dan `.tip`.
* **Perintah pemain** (37B, di `BuildCommands` di bawah `/kultivasi aula langit`): `rancang [1-9]`, `tinggi <y>`, `geser
  <dx> <dz>`, `putar`, `pratinjau`, `konfirmasi`, `batal`, `sesaji`, `naik tetap|bongkar|tinggalkan|batal`, `kaki
  bongkar|lepaskan`, `gerbang tutup|buka`, `undang <pemain>`, `info`. 37G: `/kultivasi langit feri` (lewat
  `DuniaCommands.player("langit", ...)`).
* **Perintah uji** `/kultivasi uji langit_sekte ...` (37G `sekte/langit/LangitTests`, didaftarkan
  `DuniaCommands.test("langit_sekte", LangitTests::node)`; level op 2; memanggil API 37B/37M):

| Sub | Fungsi |
|---|---|
| `pratinjau <1-9> [tinggi <y>] [putar <0-3>]` | hologram pratinjau langit gratis di atas pemain (`EngineApi.previewTest` cabang langit), `Validator.sky` dicetak |
| `pamer <1-9> tingkat <0-9> [tinggi <y>]` | showcase pulau di udara di atas pemain (log undo, maks 2, `EngineApi.showcase` cabang langit) |
| `bangun <0-9> [instan\|cepat]` | bangun situs langit pemain sendiri gratis sampai tingkat (`EngineApi.testBuild`, `instan` = 512 op/tick) |
| `naik [paksa]` | jalankan Upacara Naik bila prasasti sudah di Tahta; `paksa` melewati syarat T1 (tidak pernah menaruh prasasti) |
| `tp tahta\|kaki\|puncak\|dermaga <i>\|feri` | teleport penguji |
| `jatuh [<tinggi>] [terjun]` | uji jatuh: pindahkan penguji 4 blok keluar tepi pada `skyFloorY - tinggi` (default 4) dengan kecepatan y -1; `terjun` menyalakan shift-terjun |
| `jatuh murid` | dorong murid terdekat keluar tepi |
| `feri` / `formasi` | panggil feri sekarang / cetak tautan simpul awan |
| `bajak [1-3]` / `longbo` / `utusan` | mulai peristiwa sekarang |
| `sengsara` / `halo` / `pesta` | efek klien uji: kesengsaraan di bawah, halo 60 s, malam lentera |
| `npc daftar\|cari\|tp <i>` | sekte langit NPC: rencana, terdekat, teleport ke istana |
| `info` | status situs langit (tertambat, naik, `yP`, `skyFloorY`, tingkat, `footKey`, aturan seksi, volume udara) |
| `audit [1-9]` | `PlanAudit` keluarga Awan |
| `reset` | hapus situs langit **uji** lewat log undo |

  Grup chip `langit` ditambahkan ke `command/CheatHelp` (37G; `kultivator.cheat.langit.*`).
* **Config** `SekteConfig.section("langit", LangitConfig.class, LangitConfig::new, LangitConfig::clamp)` (inti 37B;
  `config/kultivator-sekte.json` bagian `langit`):

| Field | Default | Rentang | Arti |
|---|---|---|---|
| `aktif` | true | – | sakelar utama sistem langit |
| `gerbangRanah` | true | – | gerbang ranah 37.3.3 |
| `awanAwalMaks` | 3 | 0-9 | templat langit maks untuk Jalan B |
| `sesajiSkala` | 1.0 | 0-4 | pengali Sesaji |
| `tinggiAsal` | 232 | 208-300 | `yP` awal |
| `offsetMaks` | 64 | 0-96 | geser horizontal maks |
| `jaminanHari` | 30 | 0-120 | jaminan tingkat setelah naik |
| `lindungiUdara` | true | – | ruang udara terlindung |
| `jaringDasar` | true | – | `LangitJatuh` (jangan dimatikan di server publik) |
| `tangkapJarak` | 24 | 8-64 | jarak jatuh sebelum ditangkap |
| `terjunAwan` | true | – | Terjun Awan |
| `feri` / `feriMisi` | true / true | – | Feri Awan / murid misi naik feri |
| `patroliPedang` / `patroliMaks` | true / 2 | – / 0-4 | patroli pedang |
| `faktorQi` / `faktorQiMaks` | true / 1.25 | – / 1.0-1.5 | situs `sekte_langit` |
| `bajakAwan` / `bajakHari` | true / 8 | – / 2-60 | Bajak Awan |
| `longbo` / `utusanLangit` | true / true | – | peristiwa A7+ |
| `sengsaraSaksi` / `sengsaraLebihKeras` | true / false | – | 37.7.3 |
| `kebalSalju` | true | – | mixin presipitasi |
| `strukturNpc` / `sekteNpcMaks` | true / 2 | – / 0-2 | sekte langit NPC |

  Config klien 37V `config/kultivator-langit-client.json` (`LangitClientConfig`): `lautAwan`, `halo`, `bangau`,
  `sungaiBintang`, `airTerjun`, `kelopak`, `lentera`, `cuacaKebal`, `suaraAmbien` (semua `true`).

---------------------------------------------------------------------------------------------------------------------

### 37.8 Performa dan keamanan

#### 37.8.1 Blok

* Anggaran op per templat dan per komponen: 37.4.7 (audit gagal bila dilampaui). Penjadwal tidak berubah: rata-rata ≤ 8
  `setBlock`/tick, jeda ≥ 40 ms MSPT, hanya chunk dimuat, hanya dalam kolom + kolom kaki, log
  `[Kultivator] Bangun: <owner> <template> T<n> -> N blocks`.
* Blok terlarang `AwanPalette` (37.4.1) dicek `PlanAudit`; tidak ada blok gravitasi, tumbuh sendiri, atau fluida bebas.
* Pulau tidak pernah dipindah, dijatuhkan atau dihapus oleh kode; bongkar memakai jalur `deconstruct` biasa (dengan
  pengembalian bahan).

#### 37.8.2 Chunk

* **Tidak pernah force-load.** Tiket hanya `DuniaTickets.touch(level, chunk, ≤ 1)` (timeout 100 tick, maks 4 hidup
  seluruh server) untuk tujuan Formasi Pindah Awan, Feri Awan dan tangkapan `LangitJatuh` bila tujuan belum dimuat.
* Snapshot hanya `getChunkNow`; validasi menunggu 100% (S11).
* Peristiwa hanya saat pemilik/rekan ≥ dalam 160 blok (chunk pulau termuat karena pemain). Sekte langit NPC hanya
  worldgen; penghuninya muncul lewat `DuniaAnchors` saat chunk dimuat pemain.

#### 37.8.3 Cahaya dan spawn

* Cahaya mengikuti aturan cahaya 21.5 (lentera, `RIDGE_LIGHT` di bawah turf, `HIDDEN_LIGHT`); pembaruan cahaya oleh
  mesin cahaya vanilla seperti bangunan darat.
* Rongga perut: anti-spawn sel sekte (`sectBuildSpawnProof`) + lampu tersembunyi bila perlu (37.4.2 langkah 4).
* **Bayangan pulau**: tanah di bawah pulau besar gelap di siang hari. `SectSpawnMixin` (37B) juga menolak spawn alami
  monster di kolom footprint situs langit di bawah `skyFloorY` (`EngineApi.underSkyIsland(level, pos)` baru), agar Kaki
  Gunung dan tanah di bawah tidak menjadi sarang monster. Pratinjau memberi catatan `langit_bayangan`.

#### 37.8.4 Batas entitas

| Entitas | Maks per aula langit |
|---|---|
| Feri Awan | 1 |
| `KapalBajakEntity` peristiwa/penyerbuan | 3 |
| Perampok pedang (meluncur) | 2 per gelombang |
| Murid patroli pedang | `patroliMaks` (2) |
| Utusan / tamu NPC | 1 / 3 |
| Tubuh sekte langit NPC | `DiplomasiConfig.anggotaMaks` (6) per istana |
| Entri titik aman `LangitJatuh.AMAN` | 64 (LRU) |

Semua visual (bangau, kelopak, lentera, air terjun, laut awan) **bukan** entitas.

#### 37.8.5 Penyebaran tick server

* `LangitJatuh`: tiap 5 tick, hanya pemain di level + murid pemilik aula langit (daftar di-cache ulang tiap 40 tick);
  pencatatan titik aman bergilir `entityId % 20`. Tetap berjalan saat MSPT tinggi (keselamatan).
* `RuangUdara`: callback blok hanya memeriksa daftar kotak aula langit (cache `AABB`, diperbarui saat situs berubah).
* Patroli pedang, feri, peristiwa: dijeda bila MSPT ≥ `sectBuildMsptPause`.
* `LangitSyncServer`: hanya saat berubah atau tiap 200 tick; maks 4 aula per pemain.
* Emisi `PulauAwan` dan `Validator.sky` berjalan di luar thread server (pola `emitAsync`, irisan ≤ 4.096 op).

#### 37.8.6 Batas tinggi dunia

* Semua batas dihitung dari `level.getMinY()`/`getMaxY()`, tidak dikodekan (datapack yang mengubah tinggi Overworld
  otomatis diikuti). Situs yang setelah perubahan datapack melewati `getMaxY() - 2` di-`STATE_PAUSED` dengan baris
  `kultivator.sekte_langit.status.terlalu_tinggi`; tidak ada blok yang dibuang.
* Kapal tetap dijepit `maxY - height - 2` (yang ada); rute feri dijepit `maxY - 4`; struktur NPC ≤ 288.

#### 37.8.7 Kompatibilitas simpanan

* Kunci baru: tag `Site` `sky`, `teth`, `gs`, `asc`, `ascd`, `foot`; `Showcase` `skies`; SavedData baru
  `kultivator:sekte_langit` (`LangitData`, Overworld: per pemilik undangan, gerbang tertutup, jeda peristiwa, status titah,
  log bajak, kontak sekte NPC, penghitung advancement); bagian config `langit`. Format `CellIndex` tidak berubah (rekey
  hanya mengganti UUID pemilik seksi).
* Dunia lama: kunci tidak ada → default (bukan langit). `langit.aktif = false`: situs langit tetap dimuat dan disimpan,
  pembangunannya `STATUS_PAUSED`, perlindungan jatuh tetap aktif bila `jaringDasar` (keselamatan), efek lain mati.
* Id templat `awan_*`, id komponen `aw_*`, ordinal `Bahan` dan `FootMode`: stabil selamanya.
* **Peringatan turun versi**: jar lama tidak mengenal templat 11-19 dan bisa membuang situs langit saat memuat; catatan
  rilis meminta cadangan dunia.

---------------------------------------------------------------------------------------------------------------------

### 37.9 Tepat empat modul implementasi

Urutan: **37B langkah 1 (inti API) dulu** (≤ satu sesi kerja; isinya hanya tanda tangan + implementasi `NONE`), lalu
37B sisanya, 37M, 37V dan 37G berjalan paralel dengan berkas baru yang tidak bertumpuk. Tiap modul menyimpan progres di
`docs/progres/langit-<B|M|V|G>.md` (daftar [x]/[ ], langkah berikut) setelah tiap sub-tugas dan melanjutkan dari situ
bila terputus. Tidak ada modul yang menjalankan Gradle; pemeriksaan `javac` per berkas boleh (pola
`docs/progres/sekte-design.md`). Gradle `compileJava compileClientJava jar --no-daemon` sekali di akhir oleh pemimpin.

#### 37.9.1 Inti bersama (dibuat 37B langkah 1, paket `dev.kultivator.sekte.langit` dan `sect/build/api`)

```java
// sekte/langit/LangitContent.java
public final class LangitContent {
    public static void init() {                 // dipanggil satu baris dari Kultivator.onInitialize (37B)
        LangitConfig.register();                 // SekteConfig.section("langit", ...)
        LangitPayloads.register();               // PayloadTypeRegistry S2C/C2S
        LangitApi.set(new dev.kultivator.sect.build.flow.LangitApiImpl());   // 37B
        // dev.kultivator.sekte.langit.gerak.LangitGerak.init();            // 37M mengganti baris komentarnya sendiri
        // dev.kultivator.sekte.langit.main.LangitMain.init();              // 37G mengganti baris komentarnya sendiri
    }
}

// sekte/langit/api/LangitApi.java
public interface LangitApi {
    LangitApi NONE = new LangitApi() {};
    static LangitApi get();                      // NONE sampai di-set
    static void set(LangitApi api);
    default boolean isSkyHall(UUID owner) { return false; }               // aula berdiri (tidak tertambat) di langit
    default @Nullable SkyHallView skyHall(UUID owner) { return null; }     // termasuk yang tertambat
    default @Nullable SkyHallView at(Level level, BlockPos pos) { return null; }   // volume (37.4.10) yang memuat pos
    default List<SkyHallView> all(ServerLevel level) { return List.of(); }
    default boolean underSkyIsland(Level level, BlockPos pos) { return false; }
    default @Nullable BlockPos anchor(UUID owner, String id) { return null; }    // jangkar situs langit
    default boolean member(UUID owner, Entity entity) { return false; }   // pemilik, rekan sekte, murid, hewan jinak anggota
    default boolean invited(UUID owner, UUID player) { return false; }
}

// sekte/langit/api/SkyHallView.java
public record SkyHallView(UUID owner, ResourceKey<Level> dim, BlockPos origin, int yP, int skyFloorY, int template,
        int tier, boolean tethered, int ascent, BoundingBox islandBox, BoundingBox volume, @Nullable BoundingBox kakiBox,
        boolean grandFormation, boolean gateClosed) {
    public boolean inIsland(Vec3 p);   // islandBox
    public boolean inVolume(Vec3 p);   // volume atau kakiBox
}

// sekte/langit/api/LangitIds.java
public final class LangitIds {
    public static final String ADV = "sekte_langit/";
    public static final int FX_PILAR = 0, FX_ARRIVE = 1, FX_TANGKAP = 2, FX_PINDAH = 3, FX_FERI = 4, FX_NAIK = 5,
            FX_BAJAK = 6, FX_SENGSARA_BAWAH = 7;
    public static final int ACT_TERBANG_PEDANG = 25, ACT_JAGA_TEPI = 26, ACT_MEMANDANG = 27;
    public static final int SAW_HALO = 0;
}

// sekte/langit/api/LangitKeys.java: skyKey(UUID), footKey(UUID)  (UUID.nameUUIDFromBytes, 37.3.5, 37.3.6)
// sekte/langit/api/LangitConfig.java: field 37.7.9, static LangitConfig get(), register(), clamp()
// sekte/langit/api/LangitPayloads.java: record Sync(List<SkyHall>), SkyHall, Anchor, Fx(int op, BlockPos a, BlockPos b),
//     Saw(int what) (C2S); register(); static void send(ServerPlayer, CustomPacketPayload)
// sect/build/api/FootMode.java: enum FootMode { TETAP, BONGKAR, TINGGALKAN }
```

Tambahan `EngineApi` (default menolak / kosong, implementasi `BuildEngine`): `@Nullable Component ascend(UUID owner,
FootMode mode)`, `@Nullable SiteView skySite(UUID owner)`, `long previewSky(ServerPlayer player, int sky, int rot, Glaze
glaze, int yP, int dx, int dz, boolean test)`, `long showcaseSky(ServerPlayer player, int sky, int tier, int rot, BlockPos
origin)`, `boolean underSkyIsland(Level level, BlockPos pos)`. `SiteView` mendapat komponen record `boolean sky, boolean
tethered, int skyFloorY` (semua pemanggil konstruktor di `BuildEngine`, milik 37B).

#### 37.9.2 Modul 37B "Bangun" (keluarga Awan, generator, komponen, mesin, pendirian, ruang udara)

* **Berkas baru**: inti 37.9.1; `sect/build/blueprint/awan/*` (37.4.1, 15 kelas); `sect/build/flow/LangitAngkat`
  (Upacara Naik, `Halls.SteleMoveListener`), `sect/build/flow/LangitSesaji` (`BILLS`, ambil/kembalikan),
  `sect/build/flow/LangitPlan` (`PLAZA_MIN 208`, `CLEARANCE 24`, `OFFSET_MAX 64`, `TETHER_MAX_TIER 1`,
  `bandMin/bandMax`), `sect/build/flow/RuangUdara` (37.4.10), `sect/build/flow/LangitApiImpl`;
  `lang-src/blueprint_awan.{en_us,id_id}.json`.
* **Berkas lama yang disunting (pemilik tunggal 37B)**: `Kultivator.java` (1 baris `LangitContent.init()`),
  `sect/build/api/SectTemplates.java`, `EngineApi.java`, `SiteView.java`, `sect/build/blueprint/Templates.java`,
  `TemplateSpec.java`, `TemplateExtensions.java`, `sect/build/engine/BuildPlacer.java`, `Validator.java`,
  `SitePlan.java`, `Site.java`, `BuildEngine.java`, `Scheduler.java`, `CellIndex.java`, `Showcase.java`,
  `TerraceFitter.java`, `PlanAudit.java`, `PreviewData.java`, `Previews.java`, `sect/build/flow/Founding.java`,
  `Upgrade.java`, `Relocation.java`, `BuildCommands.java`, `BuildInfo.java`, `HallBridge.java`,
  `sect/build/SectBuild.java`, `mixin/SectSpawnMixin.java`, `client/sectbuild/TemplateScreen.java` dan
  `client/sectbuild/SiteHologram.java`.
* **Kontrak keluar**: `LangitApi` terisi; jangkar 37.4.5/37.4.9 butir 13; `SkyHallView` benar sejak konfirmasi pratinjau.
* **Langkah**: (1) inti API + `TemplateExtensions` 0 = darat; (2) `SectTemplates`/`Templates`/`TemplateSpec`;
  (3) `PulauAwan` + `AwanTabel` + `AwanLayout`; (4) bagian lain + `AwanTemplates` A1-A9; (5) `PlanAudit` langit + `uji
  sekte audit 11..19` bersih; (6) `SitePlan`/`Validator.sky`/`Snapshot`; (7) `BuildEngine` (skies, tertambat, ascend,
  rekey) + `BuildPlacer`; (8) `Founding`/`TemplateScreen`/hologram; (9) `LangitAngkat` + `LangitSesaji` + perintah;
  (10) `RuangUdara` + spawn bayangan; (11) lang; (12) pemeriksaan akhir baris dan `javac`.

#### 37.9.3 Modul 37M "Gerak" (akses, gerak, perlindungan jatuh)

* **Berkas baru**: `sekte/langit/gerak/LangitGerak` (init), `LangitJatuh`, `TerjunAwan`, `FeriAwanEntity`, `FeriRute`,
  `LangitGerakEntities` (`kultivator:feri_awan`), `LangitDermaga`, `PatroliPedang`, `NaikTanggaGoal`,
  `LangitTanggaJejak`; `formasi/catalog/CatalogLangit` (specs `awan_transfer` 470, `jaring_awan` 471),
  `formasi/effect/b/JaringAwanEffect`; klien `client/sekte/langit/gerak/LangitGerakClient`, `FeriAwanRenderer`
  (bentuk perahu awan dari gumpalan `cloud_puff` + dek giok kecil, pola `KapalDraw`); `lang-src/sekte_langit_gerak.
  {en_us,id_id}.json`.
* **Berkas lama yang disunting (pemilik tunggal 37M)**: `formasi/FormasiB.java` (1 baris `CatalogLangit.register`), `formasi/effect/b/TransportNetwork.java` (`AWAN_RANGE`, `AWAN_COST`, aturan `linked`),
  `formasi/own/Limits.java` (`AWAN_NODES`), `dunia/kapal/KapalRohEntity.java` (`sailHall` ke dermaga langit).
  `sekte/langit/LangitContent.java`: hanya baris komentarnya sendiri.
* **Kontrak**: `LangitJatuh.lastSafe(UUID) → @Nullable BlockPos`; `LangitJatuh.catchNow(LivingEntity)` (dipakai uji
  `jatuh` dan 37G); `FeriAwanEntity.call(ServerLevel, UUID owner, boolean up)`; `PatroliPedang.active(UUID owner)`.
* **Langkah**: (1) `LangitJatuh` + `TerjunAwan`; (2) `CatalogLangit` + `TransportNetwork` + `Limits`; (3)
  `JaringAwanEffect`; (4) feri (entitas, rute, lonceng, renderer); (5) dermaga + `KapalRohEntity`; (6) patroli pedang +
  `NaikTanggaGoal` + jejak tangga; (7) lang; (8) pemeriksaan.

#### 37.9.4 Modul 37V "Visual" (suasana, efek, suara; klien saja)

* **Berkas baru**: `client/sekte/langit/LangitClient` (init klien; berisi baris komentar untuk `LangitGerakClient.init()`
  37M dan `LangitUiClient.init()` 37G), `LangitSkyState` (penerima `Sync`), `AwanSink`, `LautAwanRenderer`,
  `CahayaFajar`, `HaloPelangi`, `SungaiBintang`, `AirTerjunKabut`, `KubahFormasi`, `KawananBangau`, `KelopakJatuh`,
  `LenteraLangit`, `LangitFxClient` (op `Fx`), `LangitSuara`, `LangitBudget` (pembagian 37.6.7), `LangitPoses`
  (pose aktivitas 25-27 lewat `SektePoses.register`), `LangitClientConfig`; mixin `client/mixin/WeatherEffectRendererMixin` dan
  `client/mixin/ClientLevelWeatherMixin`;
  tekstur `assets/kultivator/textures/sekte/langit/*.png` + `tools/gen_langit_tex.py`;
  `lang-src/sekte_langit_visual.{en_us,id_id}.json` (label config klien).
* **Berkas lama yang disunting (pemilik tunggal 37V)**: `KultivatorClient.java` (**CRLF**, 1 baris `LangitClient.init()`),
  `src/client/resources/kultivator.client.mixins.json` (2 entri).
* **Kontrak masuk**: `LangitPayloads.Sync`, `Fx`; keluar: `Saw(SAW_HALO)`.
* **Langkah**: (1) `LangitSkyState` + `AwanSink` + `LangitBudget`; (2) laut awan; (3) air terjun kabut; (4) kubah;
  (5) fajar + halo + sungai bintang; (6) bangau + kelopak + lentera; (7) efek sesaat + pilar; (8) mixin cuaca; (9) suara;
  (10) tekstur; (11) uji visual dengan `uji langit_sekte halo|pesta|sengsara`; (12) pemeriksaan Iris/Sodium (tanpa shadow
  pass, tanpa post chain).

#### 37.9.5 Modul 37G "Gameplay" (Qi, cuaca, peristiwa, penyerbuan, sekte NPC, UI, panduan, uji)

* **Berkas baru**: `sekte/langit/main/LangitMain` (init), `LangitSites`, `LangitSyncServer`, `LangitPeristiwa`,
  `LangitBajak`, `LangitUtusan`, `LangitSerbu`, `LangitTangga`, `LangitArrival`, `LangitSaksi`, `LangitMurid`
  (aktivitas 26-27), `LangitData` (SavedData `kultivator:sekte_langit`), `LangitItems`
  (`kultivator:cangkang_kura_langit`), `sekte/langit/LangitTests`; `sekte/langit/npc/LangitNpcPlan`,
  `LangitSektePlacement`, `LangitNpcAnchors`, `LangitNpcPack`, `JaringNpc`; mixin `mixin/ServerLevelPrecipitationMixin`;
  `tools/gen_sekte_langit.py` dan keluarannya (`data/kultivator/structure/sekte_langit/*.nbt`,
  `worldgen/structure|structure_set|template_pool/sekte_langit/*.json`, `tags/worldgen/biome/has_structure/sekte_langit.json`,
  `resourcepacks/sekte_langit/pack.mcmeta`), `data/kultivator/advancement/sekte_langit/*.json`; klien
  `client/sekte/langit/ui/LangitUiClient`, `LangitHub`, `LangitScreen`; `lang-src/sekte_langit.{en_us,id_id}.json`,
  `lang-src/sekte_langit_panduan.{en_us,id_id}.json`.
* **Berkas lama yang disunting (pemilik tunggal 37G)**: `musuh/SectRaids.java` (**CRLF**, hook 37.7.5; hanya bila
  `docs/progres/sekte-D.md` DONE), `guide/GuideChapters.java`, `command/CheatHelp.java`, `purba/HeavenlyWrath.java` (`start` menjadi `public`),
  `src/main/resources/kultivator.mixins.json` (1 entri). `sekte/langit/LangitContent.java` dan
  `client/sekte/langit/LangitClient.java`: hanya baris komentarnya sendiri.
* **Kontrak**: `LangitTangga.path(UUID) → @Nullable List<Vec3>`; `LangitData.get(server)`; `LangitSyncServer.dirty(UUID)`.
* **Langkah**: (1) `LangitData` + sync; (2) `LangitSites` + saksi kesengsaraan; (3) mixin presipitasi + petir (bersama
  `RuangUdara` 37B: 37G tidak menyentuh petir, 37B yang memiliki aturan itu); (4) `LangitSerbu` + `SectRaids` hook;
  (5) peristiwa (bajak, Longbo, utusan, tamu, pesta); (6) sekte langit NPC: generator Python, JSON, placement, pack,
  jangkar; (7) murid 26-27 + kedatangan + tangga; (8) advancement + panduan + kartu hub + layar; (9) `LangitTests`
  semua sub-perintah 37.7.9; (10) lang; (11) pemeriksaan.

#### 37.9.6 Tabel pemilik berkas lama (satu pemilik)

| Berkas lama | Pemilik | Catatan |
|---|---|---|
| `Kultivator.java` | 37B | 1 baris |
| `KultivatorClient.java` (CRLF) | 37V | 1 baris |
| `sect/build/**` (daftar 37.9.2) + `mixin/SectSpawnMixin.java` | 37B | |
| `client/sectbuild/TemplateScreen.java`, `client/sectbuild/SiteHologram.java` | 37B | |
| `formasi/FormasiB.java`, `formasi/effect/b/TransportNetwork.java`, `formasi/own/Limits.java` | 37M | |
| `dunia/kapal/KapalRohEntity.java` | 37M | |
| `kultivator.client.mixins.json` | 37V | |
| `kultivator.mixins.json` | 37G | |
| `musuh/SectRaids.java` (CRLF) | 37G | setelah 36 D DONE |
| `guide/GuideChapters.java`, `command/CheatHelp.java`, `purba/HeavenlyWrath.java` | 37G | |
| Semua berkas modul DESIGN 36 (`sekte/<id>/**`, `client/sekte/<id>/**`) | – | **tidak disunting**; pakai hook publik (`SektePoses.register`, `SekteBus`, `DiplomasiService`, `TanggaService`) |
| `sounds.json`, `gen_voices.py`, `docs/DESIGN.md` | – | tidak disunting |

---------------------------------------------------------------------------------------------------------------------

### 37.10 Lang dan kunci

Satu fragmen per modul, awalan kunci tidak bertumpuk:

| Fragmen | Pemilik | Awalan kunci |
|---|---|---|
| `blueprint_awan` | 37B | `kultivator.blueprint.template.awan_*` (9 nama + `.desc`), `kultivator.blueprint.comp.awan_*` (nama komponen baru), `kultivator.blueprint.warn.langit_*`, `kultivator.sekte_langit.bangun.*`, `.naik.*`, `.sesaji.*`, `.udara.*`, `.status.*`, `kultivator.sectbuild.button.langit_*` |
| `sekte_langit_gerak` | 37M | `kultivator.sekte_langit.gerak.*`, `.jatuh.*`, `.feri.*`, `kultivator.formasi.spec.awan_transfer.*`, `kultivator.formasi.spec.jaring_awan.*`, `entity.kultivator.feri_awan` |
| `sekte_langit_visual` | 37V | `kultivator.sekte_langit.visual.*` (label config klien) |
| `sekte_langit` | 37G | `kultivator.sekte_langit.situs.*`, `.peristiwa.*`, `.bajak.*`, `.utusan.*`, `.sengsara.*`, `.npc.*`, `.layar.*`, `kultivator.hub.card.sekte_langit*`, `kultivator.cheat.langit.*`, `advancement.kultivator.sekte_langit.*`, `item.kultivator.cangkang_kura_langit`, `kultivator.sekte_langit.uji.*` |
| `sekte_langit_panduan` | 37G | `kultivator.guide.c17.*`, `kultivator.guide.chapter.17*` |

Nama komponen baru (`kultivator.blueprint.comp.<name>`): `awan_inti`, `awan_tahta`, `awan_formasi`, `awan_satelit_kebun`,
`awan_satelit_alkimia`, `awan_satelit_arena`, `awan_satelit_leluhur`, `awan_satelit_asrama`, `awan_satelit_tambang`,
`awan_jembatan`, `awan_tangga`, `awan_pelebaran`, `awan_penuh`, `awan_pagar`, `awan_kolam`, `awan_emitor`, `awan_akar`,
`awan_jaring`, `awan_panggung_pedang`, `awan_dermaga`, `awan_halte_feri`, `awan_meditasi`, `awan_kristal`,
`awan_gerbang_langit`; komponen lain memakai nama yang sudah ada (`aula_pendiri`, `dorm`, `kitchen`, `scripture`,
`main_hall`, `paifang`, `pagoda`, `blazing_tower`, `ornament_<n>`, `finale`, dst.).

Contoh teks (id_id / en_us):
* `kultivator.sekte_langit.naik.tanya`: "Prasasti %s berdiri di Tahta Awan. Angkat sekte ke langit?" / "The stele of %s
  stands on the Cloud Throne. Raise the sect into the sky?"
* `kultivator.sekte_langit.jatuh.tangkap`: "Jaring Awan menangkapmu." / "The Cloud Net catches you."
* `kultivator.sekte_langit.udara.terlindung`: "Ruang udara %s terlindung formasi." / "The skies of %s are warded."
* `kultivator.blueprint.warn.langit_terhalang`: "%s kolom terhalang di ruang pulau (pohon, gunung atau bangunan)." / "%s
  columns are blocked inside the island space (trees, mountains or buildings)."

---------------------------------------------------------------------------------------------------------------------

### 37.11 Daftar uji (Indonesia)

Semua uji di dunia uji Overworld dengan op, kecuali disebut lain. Centang di `docs/progres/langit-<modul>.md`.

**37B Bangun**
- [ ] `uji sekte audit 11` … `uji sekte audit 19`: 0 op di luar kolom/kaki, tidak ada komponen > 24.000 op, total di bawah
      anggaran 37.4.7, tidak ada blok terlarang, semua jangkar wajib ada, tidak ada air di luar kolam tertutup.
- [ ] `uji sekte info 11..19`: tagihan per tingkat dalam ±15% tabel 37.4.7 (A1, A5, A9) dan skala A2-A4, A6-A8.
- [ ] `uji langit_sekte pratinjau 9 tinggi 232` di dataran: hologram di udara, kolom kaki terlihat, `Validator.sky` hijau;
      di bawah pohon tinggi/gunung: merah `langit_terhalang` dengan kolom ditandai; `tinggi 300` untuk A9: merah
      `langit_tinggi`.
- [ ] `uji langit_sekte pamer 5 tingkat 9`: pulau utuh melayang, 5 satelit, jembatan, tangga sampai tanah; `uji sekte
      pamer hapus` mengembalikan dunia persis (log undo).
- [ ] Ekstensi DESIGN 36 dengan template 0 (`KebunKomponen`, `PanjiKomponen`) **tidak** muncul di templat 11-19 dan tetap
      muncul di 1-9 (bandingkan `uji sekte info 5` sebelum/sesudah).
- [ ] Jalan A di aula T6 darat: Rancang A5, Sesaji diambil, T1 tertambat dibangun dari Gudang; tingkat 2 tidak mulai
      sebelum naik; cabut prasasti → status menunggu; taruh di Tahta → dialog; `[Naik, Kaki Gunung tetap]` → aula di
      langit, level aula tidak turun (jaminan), murid datang dengan efek awan, tapak darat menjadi Kaki Gunung yang
      memecah-kembalikan bahan bila blok dipecah.
- [ ] `[Naik, bongkar tapak bawah]` dan `[Naik, tinggalkan tapak bawah]` di salinan dunia: bongkar berjalan saat pemilik
      dekat; tinggalkan membuat blok darat biasa (`isSectCell` false).
- [ ] Aturan seksi: `uji langit_sekte info` menunjukkan `skyFloorY` ≥ batas seksi; tak ada seksi `CellIndex` dengan dua
      pemilik.
- [ ] Jalan B: aula baru, pilih A2, naik, tidak ada Kaki Gunung.
- [ ] Ruang udara: pemain lain (bukan anggota) tidak bisa menaruh/memecah blok, memakai ember atau pemantik di volume;
      anggota bisa. Petir di atas pulau (badai) tidak membakar atap.
- [ ] Turun Gunung: taruh prasasti di tanah → dialog relokasi biasa; pulau dibongkar atau ditinggal.
- [ ] Spawn: malam dan siang di bawah pulau A9 tidak ada monster alami di kolom footprint.

**37M Gerak**
- [ ] `uji langit_sekte jatuh`: penguji melambat lalu ditangkap ke titik aman, tanpa kerusakan; `jatuh 40`; `jatuh
      murid`; perampok yang didorong keluar jatuh dan dihitung tewas.
- [ ] Terjun Awan (T5): shift di tepi → meluncur sampai tanah tanpa luka; tanpa shift → ditangkap.
- [ ] Formasi Pindah Awan: bangun simpul tanah tangan dalam 128 blok, aktifkan dua mata, perjalanan naik/turun 60 tick,
      4 murid ikut, jeda 200 tick, penolakan saat terluka/terbang/menumpang.
- [ ] Simpul tanah 140 blok jauhnya: tidak tertaut (pesan `unlinked`).
- [ ] Feri: lonceng di halte atas dan kaki, rute tidak menembus pulau, 17 s untuk A9, turun di tengah = jatuh lambat lalu
      jaring; feri hilang 20 s setelah kosong.
- [ ] Kapal sekte `OP_HALL` ke aula langit: merapat di dermaga yang cocok, satu kapal per dermaga, kapal BAHTERA tidak
      memakai dermaga PERAHU.
- [ ] Patroli pedang: maks 2 murid, tidak pernah jatuh, berhenti saat penyerbuan.
- [ ] Tangga Awan: berjalan dari kaki sampai puncak → advancement `seribu_anak_tangga`; murid baru mendaki sendiri.

**37V Visual**
- [ ] Laut awan di tiga kualitas, memudar di tepi 384 blok, tidak terlihat di bawah dari jarak > 384, alpha × 0,8 dengan
      paket shader Iris, tidak digambar di shadow pass (uji dengan Iris + paket umum).
- [ ] Sodium aktif: tidak ada galat, quad per bingkai ≤ 400/1.200/2.600 (`F3` hitung atau log debug `LangitBudget`).
- [ ] Air terjun kabut di tiap emitor, berubah jadi kabut, tanpa air nyata; suara gemericik ≤ 20 blok.
- [ ] Fajar emas, `uji langit_sekte halo` menampilkan halo di titik antimatahari; sungai bintang di malam cerah; bangau dan
      kelopak siang; lentera saat `pesta`.
- [ ] Kubah formasi warna lambang; tidak ganda dengan `FormasiFx`.
- [ ] Hujan/salju tidak digambar di atas laut awan di pulau, tetap di tanah; `cuacaKebal false` mengembalikan hujan.
- [ ] FPS lambat (`EpicBudget.softGeometry`) → efek turun ke Minimal; pengaturan Partikel vanilla Minimal dihormati.

**37G Gameplay**
- [ ] Faktor situs di layar meditasi: "Sekte Langit ×1,05..1,25" hanya untuk anggota, bertumpuk dengan Gunung; tidak
      melewati batas 3,5.
- [ ] Salju tidak menumpuk di pulau di bioma dingin; kuali tidak terisi hujan.
- [ ] `uji langit_sekte sengsara`: pusaran di laut awan, saksi mendapat +2% XP maks 3 per hari.
- [ ] `bajak 3`: bajak muncul dari awan, regu turun di dermaga, menang = renown + peti; `longbo` pada A7+.
- [ ] `utusan`: tiga pilihan bekerja; Tantang memicu Murka Langit senja berikut.
- [ ] Penyerbuan sekte di aula langit: perampok tiba di dermaga/tepi, bukan di tanah; penyerbuan data +0,05.
- [ ] Dunia baru: `uji langit_sekte npc daftar` menyebut ≤ 2 sekte langit NPC; `npc tp 0`: istana melayang di 204-228
      di atas gerbang sekte NPC itu, penghuni di lantai dan tidak jatuh; dunia lama (chunk sudah ada): pesan "chunk lama".
- [ ] Diplomasi: temu → advancement; sekutu → Hak Labuh dan bantuan saat bajak; perang → bajak bernama sekte itu.
- [ ] Kitab Panduan bab 17 (atau bab 16 cadangan) lengkap dan tercentang dari advancement; kartu hub "Sekte Langit"
      terkunci tanpa aula; layar status benar.
- [ ] Semua sub-perintah `uji langit_sekte` (37.7.9) ada di bantuan `CheatHelp` grup `langit`.

**Integrasi akhir (pemimpin)**
- [ ] `mergeLang` lulus (tidak ada kunci ganda, kedua bahasa lengkap); akhir baris tiap berkas tetap (LF, kecuali
      `KultivatorClient.java` dan `SectRaids.java` CRLF).
- [ ] `compileJava compileClientJava jar --no-daemon` hijau.
- [ ] Dunia lama tanpa sekte langit: muat, simpan, muat ulang tanpa perubahan; `langit.aktif false`: tidak ada efek,
      pembangunan langit dijeda, jaring dasar tetap.
- [ ] Server dedicated dengan 2 pemain: pemain non-anggota terdorong keluar saat gerbang tertutup, tidak pernah
      diteleport di udara; tidak ada force-load (`/forceload query` kosong), tiket `kultivator:dunia` ≤ 4.
- [ ] MSPT stabil (< 40 ms) saat pembangunan A9 `cepat` + feri + bajak + 4 pemain di pulau.
