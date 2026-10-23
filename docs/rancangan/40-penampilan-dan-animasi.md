## 40. Penampilan dan Animasi (Murid dan NPC)

Permintaan pengguna (2026-10-23, bahasa Indonesia, mengikat): "Murid itu ada laki-laki dan perempuan, kan? Saya mau
animasi murid sangat detail dan bagus, dan kulitnya berbeda-beda sesuai gaya kultivator. Begitu juga NPC lain, sesuai
pekerjaan atau jabatannya."

Bagian ini adalah kontrak implementasi lengkap untuk **satu sistem penampilan dan animasi bersama** ("Sosok") yang
dipakai murid (`DiscipleEntity`) dan **setiap NPC manusia** mod ini (`KultivatorNpc` kota, penduduk 38, murid mandiri 39,
orang Dimensi D, Kultivator Liar, Utusan Langit, penonton turnamen). Empat modul (40I, 40K, 40G, 40R; 40.7) bekerja
paralel pada berkas yang terpisah. Semua nama kelas, field, metode, id klip, id bagian, kunci lang, perintah dan angka di
bawah adalah nama yang **harus** dipakai. Yang ditandai "(verifikasi)" sudah dicek polanya di kode tetapi nama
persisnya wajib dicocokkan lagi dengan `butterbot-fabric/reference/mcsrc` sebelum dipakai. Bagian ini berdiri sendiri di
`docs/rancangan/40-penampilan-dan-animasi.md`; `docs/DESIGN.md` tidak diubah oleh modul.

Preferensi pengguna yang mengikat bagian ini: **selalu dunia baru** (tidak ada kompatibilitas simpanan; field baru boleh
ditambah bebas, data lama tidak dimigrasi), **konten nyata** (tidak ada kotak polos, kulit abu-abu, klip kosong atau
"TODO"), **laptop lemah** (anggaran dan LOD wajib, 40.6), **Sodium dan Iris** harus tetap bekerja (40.6.3).

---------------------------------------------------------------------------------------------------------------------

### 40.0 Ruang lingkup dan aturan mengikat

1. **Satu sistem, banyak pemakai.** Identitas (40.2), penyusun kulit (40.3), busana dan rambut berkubus (40.3.6-40.3.8),
   katalog klip dan pengendali animasi (40.5) adalah milik 40. Modul 38 (kehidupan NPC 38NL, model 38MD, tema 38TK/38TV)
   dan 39 (murid mandiri) **memanggil** sistem ini lewat kontrak C40.* (40.7.3); mereka tidak membuat sistem kulit, wajah,
   klip atau prop sendiri. Penyesuaian yang mengikat untuk 38 dan 39 ditulis di 40.7.4.
2. **Rangka tetap rangka pemain.** Semua tubuh memakai `PlayerModel` vanilla (kepala, badan, dua lengan, dua kaki, enam
   lapisan luar). Tidak ada rangka baru, tidak ada siku/lutut tambahan; detail datang dari tekstur 128 × 128 per sosok,
   kubus busana/rambut yang menempel pada bagian rangka, pegas sekunder, klip berlapis dan wajah beranimasi. Ini menjaga
   semua pose yang ada (cast 27, duel 35, meditasi, tugas 20, Sekte 36, Langit 37) tetap berlaku tanpa diterjemahkan.
3. **Tidak menggandakan pose.** Pose cast (`PoseLibrary`/`CastPoses`), pose duel (`DuelPoseLibrary`/`DuelPoses`), duduk
   meditasi (`MeditationPose`) dan pose fungsi yang terdaftar (`SektePoses` 13-24, `LangitPoses` 25-27) **dipakai ulang**
   sebagai sumber klip (40.5.9). `NpcPoses` (bahasa tubuh K) dan empat pose tugas `DutyPoses` **dipindah** menjadi data
   klip kanonik; kode lamanya menjadi pemanggil tipis (40.5.9). 38 tidak lagi membuat `NpcKlip`, `KlipKatalog`,
   `NpcKlipPlayer`, `NpcPropLayer`, `TatapKepala` atau `NpcFaceLayer` (40.7.4).
4. **Server hanya data.** Server menyimpan dan menyinkronkan angka (jenis kelamin, kelompok usia, benih rupa, gaya, klip,
   prop, sasaran tatap). Semua gambar, tekstur, pegas dan sampel klip dihitung di klien. Server tidak pernah memuat PNG.
5. **Determinisme.** Rupa satu entitas sepenuhnya ditentukan oleh angka tersinkron + tabel resep (40.3.10). Dua pemain
   melihat murid yang sama persis sama; memuat ulang dunia memberi rupa yang sama. RNG penyusun = `SplitMix64`
   (40.3.10) yang ditulis identik di Java dan Python.
6. **Tidak ada aset unduhan, tidak ada kulit pemain sungguhan.** Semua lembar bagian dibuat oleh generator Python
   (`tools/png.py`) dan diperiksa dengan Read pada pratinjau 8× (40.3.11). Kulit bawaan vanilla (Alex, Steve, ...) tidak
   lagi dipakai untuk murid dan NPC manusia yang ditangani Sosok (masih dipakai sebagai cadangan terakhir bila atlas gagal
   dibuat, 40.3.9).
7. **Keamanan GPU dan memori.** Semua tekstur runtime adalah `DynamicTexture` yang didaftarkan dan **dilepas** lewat
   `TextureManager.register/release` (pola `LambangAtlas`), dengan topi jumlah per tingkat detail (40.6.1). Tidak ada
   shader sendiri, tidak ada `RenderType` sendiri: hanya `RenderTypes.entityCutout`, `entityCutoutNoCull` dan
   `entityTranslucent` (verifikasi nama tepat di `client/renderer/rendertype/RenderTypes`), sehingga Iris memakai shader
   entitas paket shader dengan benar.
8. **Klien thread saja** untuk semua kelas `client/sosok/**`; tidak ada thread pekerja (NativeImage dan unggah tekstur di
   thread render). Penyusunan dibatasi waktu per frame (40.6.1).
9. **Dunia baru, data baru.** Field baru di `DiscipleEntity` dan `KultivatorNpc` disimpan dengan kunci baru; NBT lama
   tanpa kunci itu mendapat sosok baru dari benih entitas (tidak ada migrasi khusus, tidak ada crash).
10. **Gender tidak dikira dari wajah kulit vanilla lagi.** `VoiceDisciples.female` dan `KultivatorNpc.female()` membaca
    jenis kelamin eksplisit (40.2.1). Suara, nama, kulit, busana, gaya jalan dan klip varian mengikuti satu sumber.
11. **Hormat budaya.** Kerah silang hanfu ditutup **kanan-ke-kiri (右衽: panel kiri pemakai di atas)** untuk orang hidup;
    **kiri-ke-kanan (左衽)** hanya untuk arwah dan kain kafan (40.3.7). Tidak ada stereotip etnis pada warna kulit: semua
    nada kulit muncul di semua peran (bobot per tempat hanya untuk kota tema 38.T, 40.4.1).

---------------------------------------------------------------------------------------------------------------------

### 40.1 Fakta dari kode dan sumber 26.3 (audit 2026-10-23; kode adalah kebenaran)

**Murid (`client/disciple/**`, `disciple/**`).**
- `DiscipleRenderer extends LivingEntityRenderer<DiscipleEntity, DiscipleRenderState, PlayerModel>`; model utama
  `DutyPoses.Model` (wide/slim dari `ModelLayers.PLAYER` / `PLAYER_SLIM`), skala tetap `PLAYER_SCALE = 0.9375`; lapisan
  `DiscipleRobeLayer`, `NatalTreasureLayer`, `DiscipleHeldItemLayer`, `FormLayer`, `AvatarLayer`. Tekstur =
  `DiscipleLook.skin(entity)` = salah satu **sembilan kulit bawaan vanilla** (`alex, ari, efe, kai, makena, noor, steve,
  sunny, zuri`) dari `DATA_SKIN` (0-8) + `DATA_SLIM`.
- Jenis kelamin sekarang **ditebak dari wajah kulit**: `VoiceDisciples.FEMALE_SKINS` (alex, ari, efe, makena, noor =
  perempuan). Rekrut: `Disciples.recruit` memanggil `setupNew(..., random.nextInt(SKIN_COUNT), random.nextBoolean())`
  (lengan ramping acak, lepas dari wajah). Nama: `DiscipleNames` 36 nama netral (`kultivator.disciples.name.0-35`).
- Data tersinkron murid: realm, layer, stage, dao, warna akar, flags (MEDITATING, BREAKING_THROUGH, FIGHTING, DEVIATING),
  skin, slim, role (DISCIPLE, CANDIDATE, RIVAL, PHANTOM, CLONE), activity (byte), sect rank (0-5: Tercatat, Luar, Dalam,
  Inti, Tetua, Tetua Agung), stars, talent (fisik di `talentPhysique`; `PowerIds.PHYSIQUE_DOUBLE_PUPILS = 7`), power,
  aura. Watak (`sekte/api/Sifat`, 12 sifat pada 6 sumbu) **tidak** tersinkron.
- `DiscipleRobeLayer`: model `CultivatorModels.ROBE(_SLIM)` (PlayerModel mengembang 0.32 + rok 9 × 9 × 5 di badan),
  tekstur abu `robe_base(_pendek|_tempur).png` diwarnai warna Dao/ranah atau seragam sekte (`JubahLook`), tepi pangkat
  `PhysiqueVisuals.RANK_TRIM = {0x9A9A9A, 0xF2F2F2, 0x4F8FFF, 0x3FBF8A, 0xA060E0, 0xFFF2C0}`, lambang dari Jiwa Baru Lahir
  (`LambangCache`/`LambangAtlas` = `DynamicTexture` 64 × 64 per aula, maks 12 entri LRU). Potongan seragam
  `SeragamData.CUT_PANJANG/PENDEK/TEMPUR`.
- Pose murid: `PlayerModelMixin` (TAIL `PlayerModel.setupAnim`) → `MeditationPose.apply` lalu `PoseApplier.apply`
  (snapshot cast/duel dari `PoseRenderData.POSE`); `DutyPoses.Model.setupAnim` → `DutyPoses.apply` (4 tugas air, kebun,
  dapur, ternak + `SektePoses` 13-24, campur 6 tick, hanya diam dan tenang); `LangitDutyPosesMixin` menambah
  `LangitPoses` 25-27 di RETURN `DutyPoses.apply`. Prop tugas digambar `DiscipleHeldItemLayer`.
- `PoseLibrary`: 18 kanal `HX, HY, LEAN, TWIST, RAX, RAY, RAZ, LAX, LAY, LAZ, RLX, RLZ, LLX, LLZ, LIFT, RLY, LLY, TILT`;
  `CastPoses.Snapshot(pose, val[], w[], fromVal[], fromW[], cross, twoHanded)`; `PoseApplier` mencampur lengan/kaki
  mutlak, kepala aditif, lean/twist memutar pivot bahu dan kepala; kaki dibiarkan berjalan bila `walkAnimationSpeed > 0.1`.

**NPC.**
- `KultivatorNpc` (PathfinderMob, `noSave`): tersinkron role (`NpcRole`, 15 nilai), look (int benih), realm, stage, dao,
  voice (0-5: m1-m3, f1-f3), robe, flags (SITTING, CHANNEL, HOSTILE, AURA, GHOST, TALK, BOW, GAZE, WORK, LEADER), plate,
  line. `female()` = `voice() >= 3`. `NpcRenderer`: kulit = `skinByIndex(look % 9, slim)`, slim = perempuan atau
  `(look >>> 13) % 4 == 0`; jubah murid (≤ 32 b), `KotaGearLayer` (21 setelan kubus, atlas `perlengkapan.png` 64 × 512,
  16 pita bahan; bit look 9-10 dan 21 memilih varian), bahasa tubuh `NpcPoses` (snapshot sikap per peran, BOW, TALK,
  GAZE, WORK) bila tidak ada cast/duel, gelembung kalimat ≤ 12 b.
- Lain: `HumanNpcRenderer` (D: Arwah, Laut, Yaksha; `Headwear` 17 jenis; kulit bawaan atau kulit sendiri
  `textures/dunia/dimensi/entity/*.png`), `RogueRenderer` (Kultivator Liar, 8 sekte iblis), `EnvoyRenderer` (Utusan
  Murka Langit), `PenontonRenderer` (penonton turnamen 36, ringan), `ShadowRenderer`/`HeartDemonRenderer` (meniru
  pemain; **di luar** lingkup Sosok).
- `KotaNames.person(look)`: marga 24 + nama diri 32 **tanpa gender**.

**Duel dan kompatibilitas.** `DuelPose` 27 nilai (SIKAP_PEMBUKA ... HORMAT); `DuelPoseLibrary.sample(pose, t, windup,
from, ctx, out)` dan `modelChannels(...)` publik; `PalBridge` memutar animasi Player Animation Library lewat refleksi
**hanya untuk pemain** (`PlayerAnimationAccess.getPlayerAnimationLayer(Avatar, Identifier)`), opsi `duel_pal`.

**Sumber Minecraft 26.3 (diverifikasi di `reference/mcsrc`).**
- `DynamicTexture(Supplier<String> label, NativeImage image)` (mengunggah di konstruktor), `upload()`, `getPixels()`,
  `setPixels(NativeImage)`, `close()`; `TextureManager.register(Identifier, AbstractTexture)` (menutup tekstur lama di
  id yang sama) dan `release(Identifier)`.
- `NativeImage(int w, int h, boolean zero)` (RGBA), `setPixel(x, y, argb)` / `getPixel(x, y)` (ARGB, konversi ABGR di
  dalam), `copyRect(...)`, `fillRect(...)`, `writeToFile(Path)`, `NativeImage.read(InputStream)`.
- `ModelPart`: field publik `x, y, z, xRot, yRot, zRot, xScale, yScale, zScale, visible, skipDraw`; `resetPose()` memuat
  ulang **termasuk skala** (`PartPose` 9 nilai) dan `Model.setupAnim` memanggil `resetPose()` dulu → skala per frame aman.
  `hasChild/getChild`, `translateAndRotate(PoseStack)`.
- `PlayerModel(ModelPart root, boolean slim)`, `PlayerModel.createMesh(CubeDeformation, boolean slim)`; field
  `leftSleeve, rightSleeve, leftPants, rightPants, jacket` (+ `hat` dari `HumanoidModel`); `setupAnim` membaca
  `AvatarRenderState.showHat/showJacket/...` (bawaan true).
- `Attributes.SCALE` (0.0625-16, **syncable**) ada di `LivingEntity.createLivingAttributes`; `LivingEntityRenderer`
  mengalikan `state.scale = entity.getScale()` sebelum `setupRotations`, dan kotak tabrak, mata, papan nama ikut
  `getScale()`. Tinggi dan anak memakai atribut ini, bukan skala gambar saja.
- `ResourceManager.getResource(Identifier)` → `Optional<Resource>` (pola `PalBridge`), untuk membaca resep JSON dan lembar
  bagian PNG sekali saat pertama dipakai.
- `ShaderCompat.shadowPass()` (mod, `client/epic/ShaderCompat`) sudah dipakai untuk melewati gambar di shadow pass Iris.
- Perintah uji: `DuniaCommands.test(String literal, Supplier<LiteralArgumentBuilder<CommandSourceStack>>)` →
  `/kultivasi uji <literal>` (tanpa menyunting `DuniaCommands`).

**Rencana yang sudah ada dan disatukan di sini.** DESIGN 38.N (klip 1-78, `DATA_ANIM`, `DATA_ANIM_T0`, `DATA_PROP`,
`DATA_LOOK_AT`, tatapan, kedip), 38.O.1 (`NpcFaceLayer`, skala anak, perlengkapan pita 16-31), 38.O.4 (prop 1-40),
38.T.8 (klip 79-107, prop 41-62, perlengkapan pita 32-47). DESIGN 39 (`docs/rancangan/39-murid-mandiri.md`, ditulis
bersamaan): sepuluh jalan hidup (`pedang, alkemis, formasi, pertapa, petualang, pemburu, pedagang, cendekia, penjaga,
iblis`), aktivitas tersinkron **40-56** (`ACT_LATIH_JURUS` ... `ACT_MEDITASI_MANDIRI`, 39.4.2) dengan daftar animasi
39.4.8 yang rencananya didaftarkan 39E lewat `SektePoses.register` — **padahal** `SektePoses.register` melempar
`IllegalArgumentException` di luar 13-24 (`SIZE = ACT_LAST − ACT_FIRST + 1 = 12`; masalah yang sama sudah dicatat
`LangitPoses`). 40 menyediakan klip untuk 40-56 (40.5.13), sehingga 39 tidak perlu memperluas `SektePoses`. 39 juga
menambah `Role.JELMAAN` (tubuh tampilan murid di kota) dan menyunting `DiscipleEntity` (39A).

---------------------------------------------------------------------------------------------------------------------

### 40.2 Identitas: jenis kelamin, usia, proporsi tubuh

#### 40.2.1 Data `Sosok` (paket `dev.kultivator.sosok`, modul 40I)

`public record Sosok(int kelamin, int usia, int postur, int benih)` dengan konstanta:

| Field | Nilai | Arti |
|---|---|---|
| `kelamin` | `LAKI = 0`, `PEREMPUAN = 1` | eksplisit, tidak pernah dikira dari kulit |
| `usia` | `ANAK = 0` (≈ 7-12 th), `MUDA = 1` (≈ 13-20), `DEWASA = 2` (≈ 21-55), `TUA = 3` (≈ 56+) | kelompok usia **tampak** |
| `postur` | 0-7 | varian tinggi/bentuk (40.2.3) |
| `benih` | 0 .. 2^26 − 1 | benih rupa (pilihan bagian 40.3) |

Dikemas dalam satu `int` tersinkron (`Sosok.pack()` / `Sosok.unpack(int)`):

```
bit 0      kelamin
bit 1-2    usia
bit 3-5    postur
bit 6-31   benih (26 bit)
```

`int 0` berarti "belum ada sosok" → klien memakai `Sosok.dariBenihEntitas(uuid)` sementara (tidak pernah tampil kulit
vanilla bila Sosok aktif).

Data gaya yang **boleh berubah** (int kedua, `Gaya.pack()`):

```
bit 0-3    sifat1  (0 = tidak ada, 1-12 = Sifat.ordinal()+1)
bit 4-7    sifat2
bit 8-12   jalan   (0-31, id jalan hidup 40.4.3; 0 = belum)
bit 13-15  luka    (0 sehat, 1 pincang, 2 lengan dibalut, 3 lelah habis-qi, 4 terluka parah)
bit 16-19  busanaKhusus (0 otomatis, 1 jubah bepergian, 2 putih berkabung, 3 pakaian pesta, 4 pakaian kerja,
           5 pakaian tidur, 6 baju zirah tugas, 7 penyamaran)
bit 20-23  suasana (0 netral, 1 senang, 2 sedih, 3 marah, 4 takut, 5 mengantuk, 6 malu, 7 bangga)
bit 24-31  cadangan (harus 0)
```

`Gaya` tidak masuk kunci cache kulit kecuali `busanaKhusus` dan `luka == 2` (perban digambar di atlas).

#### 40.2.2 Kebijakan jenis kelamin dan usia

**Murid** (`Disciples.recruit`, `setupBody`, klon):
- Jenis kelamin: `random.nextDouble() < SosokConfig.rasioPerempuan` (bawaan **0.5**). Calon Istimewa (`talentSpecial`)
  memakai rasio yang sama. Klon Ilahi dan Bayangan menyalin sosok sumber persis (benih sama).
- Usia saat rekrut: `MUDA` 82 %, `ANAK` 10 % (yatim yang dibawa ke sekte), `DEWASA` 8 % (pengembara yang bertobat).
  Tidak pernah `TUA` saat rekrut.
- Penuaan tampak bawaan (tick harian 40I, `SosokUsia.harian`): `ANAK → MUDA` setelah **48 hari dunia**; `MUDA → DEWASA`
  setelah **160 hari** hanya bila ranah ≤ Pemurnian Qi; dari Fondasi ke atas **rupa membeku** (ciri kultivator: tetap
  muda). `DEWASA → TUA` hanya untuk Fana dan hanya lewat `SosokApi.setUsia` (39 boleh memakainya untuk umur sungguhan).
  Gagal tembus ranah berat (`Calamity` mematikan) tidak menuakan. Hari dihitung dari `extra.sosok.lahir` (gameTime
  rekrut / 24000).
- Nama mengikuti kelamin (40.2.5); suara mengikuti kelamin (`VoiceDisciples.voice` memilih m1-m3 atau f1-f3 dari benih).

**NPC** (`KultivatorNpc.setup`): `SosokGen.untukNpc(NpcRole peran, int look)` menentukan semuanya dari peran + look,
sehingga **semua spawner yang ada otomatis benar tanpa disunting**:
- Kelamin per kebijakan peran (kolom "K" di 40.4.1): `L` = laki-laki saja, `P` = perempuan saja, `C<p>` = campuran
  dengan peluang perempuan `p` %, dibaca dari bit look `(look >>> 24) & 0x7F` (0-127) < `p × 1.28`.
- Usia per peran (kolom "U"): satu kelompok tetap atau campuran (mis. `WARGA`: anak 15 %, muda 20 %, dewasa 45 %, tua
  20 %, dari bit `(look >>> 2) & 0x7F`).
- `voice` dipaksa cocok: perempuan → `3 + voice % 3`, laki-laki → `voice % 3` (dilakukan di `setup` setelah tabel).
- Postur dan benih dari `HashCommon.murmurHash3(look)` (verifikasi nama di `it.unimi.dsi.fastutil.HashCommon`, sudah
  dipakai Minecraft).

**Orang D, Liar, Utusan, Penonton** (tidak disunting di server): sosok dihitung **di klien** dari field tersinkron yang
sudah ada (`look`/`skin`/`slim`/`variant`/jenis) lewat `SosokKlien.dariLama(int jenisRenderer, int peran, int look,
boolean slim)`; kelamin = slim (bila sebelumnya ramping = perempuan) kecuali kebijakan peran tetap (Meng Po `P`, Raja
Yama `L`, Hakim `L`, Raja Naga `L`).

#### 40.2.3 Proporsi tubuh dalam rangka pemain

Skala keseluruhan memakai **`Attributes.SCALE`** (server, base value) sehingga kotak tabrak, tinggi mata, posisi papan
nama dan bayangan ikut. Renderer tetap mengalikan `PLAYER_SCALE 0.9375` seperti sekarang. Lengan ramping = model
`PLAYER_SLIM` (lengan 3 px). Skala bagian (kepala, kaki) memakai `ModelPart.xScale/yScale/zScale` per frame (aman karena
`resetPose`).

| Kelompok | Lengan | `SCALE` dasar | Varian postur (0-7) ditambah | Kepala relatif | Kaki `yScale` | Catatan rangka |
|---|---|---|---|---|---|---|
| Laki-laki dewasa | lebar | 1.00 | −0.03, −0.02, −0.01, 0, 0, +0.01, +0.02, +0.04 | 1.00 | 1.00 | — |
| Perempuan dewasa | ramping | 0.95 | −0.03, −0.02, −0.01, 0, 0, +0.01, +0.02, +0.03 | 1.00 | 1.02 (kaki sedikit panjang, pinggul tinggi) | badan `xScale 0.94` (bahu sempit), lengan ikut pivot bahu digeser 0.25 px ke dalam |
| Laki-laki muda | lebar (postur 0-1: ramping, pemuda kurus) | 0.95 | −0.02 .. +0.02 | 1.04 | 1.00 | badan `xScale 0.96` |
| Perempuan muda | ramping | 0.91 | −0.02 .. +0.02 | 1.05 | 1.02 | badan `xScale 0.92` |
| Anak (L/P) | ramping | 0.66 | −0.04 .. +0.04 (usia 7-12 terasa) | **1.22** | 0.92 | badan `yScale 0.94`; kepala tumbuh ke atas dari pivot leher, lengan/kaki tetap; kotak tabrak 0.66 |
| Laki-laki tua | lebar | 0.97 | −0.02 .. +0.01 | 1.00 | 0.98 | bungkuk lewat lapisan sikap tua (40.5.6), bukan skala |
| Perempuan tua | ramping | 0.92 | −0.02 .. +0.01 | 1.00 | 0.98 | idem |

- Pergeseran bahu perempuan: `rightArm.x += 0.25`, `leftArm.x −= 0.25` (px) setelah `PoseApplier` (oleh `GerakApplier`,
  40.5.2), sehingga lengan ramping tidak "melayang" di luar badan yang menyempit.
- Pemain **tidak** pernah diubah (Sosok hanya pada entitas yang renderer-nya memasang data Sosok).
- Hitbox anak memengaruhi navigasi: anak lewat celah 1 blok tinggi (tidak masalah). Duel: anak **tidak pernah**
  menjadi petarung duel (`KotaDuel`/`DuelRules` menolak `usia == ANAK`; 40I menambah satu syarat di `DuelRules` murid,
  lihat 40.7.2).

#### 40.2.4 Penyimpanan dan sinkron

- `DiscipleEntity`: `DATA_SOSOK` (INT), `DATA_GAYA` (INT), `DATA_GERAK` (INT), `DATA_GERAK_T0` (INT), `DATA_PROP` (INT),
  `DATA_LOOK_AT` (INT, −1 = tidak). NBT kunci `"sosok"`, `"gaya"` (gerak/prop/tatap tidak disimpan). Tag Prasasti Jiwa dan
  kepergian (`restoreFromTablet`, `restoreFromDeparture`) membawa `"sosok"` dan `"gaya"`. `DATA_SKIN/DATA_SLIM` tetap ada
  (dipakai cadangan dan oleh kode lama yang membacanya) tetapi `slim` diisi dari kelamin.
- `KultivatorNpc`: field yang sama (tidak disimpan; tipe `noSave`). `look` tetap benih utama.
- Arti `DATA_GERAK`: `klip << 8 | varian` (klip 0-4095 memakai 12 bit di atas 8 bit varian; 0 = tidak ada klip server);
  `DATA_GERAK_T0` = `gameTime & 0x7FFFFFFF` saat mulai; `DATA_PROP` = `kanan | kiri << 10 | punggung << 20` (prop
  0-1023 tiap slot, 40.5.7); `DATA_LOOK_AT` = id entitas yang ditatap. Ini **persis** data yang direncanakan 38.N.1
  (namanya di sini kanonik; 38NL tidak menambahkannya lagi, 40.7.4).

#### 40.2.5 Nama mengikuti kelamin

- `DiscipleNames`: daftar lama 0-35 diberi kelamin (tabel tetap di kode, `FEMALE_OLD = {1, 3, 5, 6, 9, 12, 15, 17, 20,
  22, 24, 27, 29, 31, 32, 35}`), ditambah 36 nama baru `kultivator.disciples.name.36-71`, sehingga **36 laki-laki dan 36
  perempuan**. `pick(random, taken, kelamin)` memilih dari kolam kelaminnya. Nama baru (sama di id_id dan en_us):
  - Perempuan (36-55): Liu Ruyan, Shen Yueru, Xie Wanqing, Lan Xinyao, Qing Ziyan, Mu Wanrou, Jiang Lixue, Lin Shiyin,
    Su Mengyao, Chen Yuwei, Wen Xiangyun, Gong Yuhua, Luo Qingyao, Xia Ningzhi, Tang Zixuan, Yao Yunxi, He Lianyi,
    Zhou Bingyu, Hua Yinxue, Qiu Lingxi.
  - Laki-laki (56-71): Gao Yunhe, Tie Hanshan, Lei Zhenyu, Qu Fengming, Yu Wenchang, Xue Tianheng, Kong Yanshu,
    Huo Lieyang, Peng Jingshan, Cao Mosheng, Wang Xuance, Du Qianshan, Ling Haoran, Situ Jian, Rong Beichen, Wei Changge.
  - Hitungan: perempuan = 16 lama (termasuk 31 "Yan Zhiqiu") + 20 baru = 36; laki-laki = 20 lama (termasuk 10 "Mo
    Chenxi" dan 13 "Liu Qinghe") + 16 baru = 36. `COUNT` lama dihapus; `MALE_COUNT = FEMALE_COUNT = 36`.
- `KotaNames.person(look)` → `person(look, perempuan)`: nama diri dari kolam kelamin: `kultivator.kota.nama_diri_l.0-23`
  (Feng, Hao, Jian, Ming, Shan, Tian, Wei, Zhen, Hong, Kai, Long, Ping, Tao, Lei, Bo, Cheng, Gang, Heng, Jun, Kun, Peng,
  Qiang, Wen, Yong) dan `kultivator.kota.nama_diri_p.0-23` (Xue, Lan, Mei, Qing, Xin, Yao, Jing, Ling, Ning, Qiu, Shu,
  Yan, Yu, Zi, Hua, Rui, Yun, Wan, Rou, Ying, Xiang, Juan, Li, Fang). `plate(look, peran)` memakai kelamin dari
  `SosokGen.kelamin(peran, look)`. Kunci lama `nama_diri.*` dibiarkan (tidak dipakai lagi oleh kode baru).

---------------------------------------------------------------------------------------------------------------------

### 40.3 Kulit kultivator prosedural: Penyusun Sosok (modul 40K)

Setiap sosok mendapat **satu atlas 128 × 128 RGBA** yang disusun di klien dari lembar bagian buatan Python. Atlas berisi
kulit pemain 64 × 64 (dua lapisan), bingkai wajah beranimasi, tekstur kubus rambut dan tekstur kubus busana. Tubuh,
wajah, rambut berkubus dan busana berkubus semuanya memakai **tekstur yang sama** → satu `RenderType` per sosok, sehingga
batch Iris/Sodium tetap sederhana dan memori satu sosok tetap 64 KiB di GPU (+ 64 KiB salinan `NativeImage` di RAM yang
disimpan `DynamicTexture`).

#### 40.3.1 Tata letak atlas sosok (koordinat piksel, x ke kanan, y ke bawah)

| Wilayah | Kotak | Isi |
|---|---|---|
| A | (0, 0)-(64, 64) | kulit pemain baku 64 × 64 (kepala+topi, badan+jaket, lengan+lengan baju, kaki+celana; tata letak vanilla) |
| B1 | (64, 0)-(128, 8) | 16 strip mata 8 × 4: strip `i` di (64 + 8·(i mod 8), 4·(i div 8)) |
| B2 | (64, 8)-(128, 14) | 16 strip mulut 8 × 3: strip `i` di (64 + 8·(i mod 8), 8 + 3·(i div 8)) |
| B3 | (64, 14)-(128, 24) | cadangan (harus transparan; generator memeriksa) |
| C | (64, 24)-(128, 64) | kubus rambut dan hiasan kepala (40.3.6) |
| D | (0, 64)-(128, 128) | kubus busana, topi bambu, perlengkapan kecil (40.3.7); (58, 110)-(128, 128) cadangan |

**Nama strip wajah** (dipakai klip dan ekspresi; indeks = posisi di B1/B2):

| i | Strip mata (fy 2-5: alis, mata, bawah mata) | Strip mulut (fy 5-7) |
|---|---|---|
| 0 | `buka` (sama dengan wajah kulit) | `diam` |
| 1 | `setengah` (kedip tengah) | `bicara_A` (terbuka) |
| 2 | `tutup` | `bicara_O` (bulat) |
| 3 | `lirik_kiri` (pupil geser 1 px) | `bicara_E` (lebar setengah) |
| 4 | `lirik_kanan` | `senyum` |
| 5 | `lirik_atas` | `tertawa` (terbuka tersenyum, gigi 2 px) |
| 6 | `lirik_bawah` | `cemberut` |
| 7 | `marah` (alis turun ke dalam) | `marah` (gigi terkatup) |
| 8 | `sedih` (alis naik di dalam) | `kaget` (O besar) |
| 9 | `kaget` (alis tinggi, putih mata penuh) | `sedih` (sudut turun) |
| 10 | `senang` (mata melengkung ^ ^) | `tidur` (sedikit terbuka) |
| 11 | `mengantuk` (kelopak setengah) | `meniup` (bibir mengerucut) |
| 12 | `tutup_tenang` (meditasi, alis rileks) | `makan` (mengunyah) |
| 13 | `kilat` (iris diganti warna Dao/ranah terang, slot `IRIS` nada 220) | `batuk_darah` (1 px merah di sudut) |
| 14 | `sakit` (menyipit, alis berkerut) | `menyeringai` (satu sudut naik) |
| 15 | `malu` (mata ke bawah + rona pipi slot 1 di fx 1/6 fy 5) | `menggigit_bibir` |

Lapisan wajah (`WajahModel`, 40K) adalah anak kepala: 16 bidang mata `addBox(−4, −6, −4.12, 8, 4, 0,
EnumSet.of(Direction.NORTH))` dan 16 bidang mulut `addBox(−4, −3, −4.13, 8, 3, 0, EnumSet.of(Direction.NORTH))`
(overload `CubeListBuilder.addBox(..., Set<Direction>)` ada di 26.3), `texOffs` = posisi strip; tepat satu bidang mata dan
satu bidang mulut `visible` per frame. Di depan kepala dasar (jarak 0.12 px mencegah z-fighting dari jauh), di belakang
lapisan topi (−4.5; poni tetap di depan). Jangan bergantung pada `visibleSides` saja: Sodium menggambar kubus lewat jalur
cepatnya sendiri; kedalaman 0 membuat sisi lain degeneratif dan sisi belakang dibuang culling `entityCutout`.

Model tubuh Sosok dibangun dari mesh pemain dengan **ukuran tekstur 128 × 128** sehingga UV vanilla jatuh di wilayah A:
`LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, slim), 128, 128)` didaftarkan sebagai
`kultivator:sosok_tubuh` dan `kultivator:sosok_tubuh_ramping` (`ModelLayerRegistry.registerModelLayer`, pola
`CultivatorModels.register`). Lapisan luar (topi, jaket, lengan baju, celana) tetap bagian mesh pemain (inflasi vanilla
0.5 / 0.25).

#### 40.3.2 Lembar bagian dan pengodean (dibuat Python, dibaca Java)

Berkas: `assets/kultivator/textures/sosok/bagian/<kelompok>/<id>.png`, ukuran **sama dengan wilayah tujuan** (64 × 64 untuk
bagian kulit wilayah A, 8 × 4 / 8 × 3 untuk strip wajah, ukuran jejak UV untuk kubus 40.3.6-40.3.7). Satu piksel
lembar bagian berarti:

| Kanal | Arti |
|---|---|
| A | cakupan 0-255 (0 = tidak menggambar; 255 = menutup; antara = campur alfa di atas lapisan sebelumnya) |
| G | **slot palet** = `G / 16` (0-15), dibulatkan; 40.3.3 |
| R | **nada** 0-255 pada tangga warna slot itu (128 = warna dasar) |
| B | bendera: bit 0 tepi gelap (garis jahitan, nada −1 tingkat lagi), bit 1 derau kain ±3 %, bit 2 kilau (nada +1 tingkat bila arah cahaya atas), bit 3 **tetap**: RGB diambil dari lembar `_tetap` pasangannya |

Bagian berwarna tetap (emas kusam bersulam, bunga, simbol yin-yang) memakai lembar kedua `<id>_tetap.png` (RGBA biasa)
yang dipakai hanya di piksel dengan bit 3. Tangga warna (`Palet.tangga(int rgb, int nada)`), dihitung dalam ruang
**linear** (sRGB → linear dengan `pow 2.2`, kembali dengan `pow 1/2.2`):

```
f = nada / 128                     // 0 .. 2
bila f < 1:  c = lerp(gelap, dasar, f)   dengan gelap = dasar × 0.42 + biru-ungu (0.04, 0.03, 0.09)
bila f ≥ 1:  c = lerp(dasar, terang, f − 1) dengan terang = dasar × 1.18 + hangat (0.05, 0.04, 0.00), dijepit 1
```

Bayangan bergeser dingin, sorotan bergeser hangat (gaya piksel-art), sama untuk semua slot kecuali slot 0-1 (kulit) yang
memakai gelap = dasar × 0.62 + merah (0.06, 0.01, 0.0) agar kulit tidak abu-abu.

#### 40.3.3 Slot palet (16)

| Slot | Nama | Sumber warna |
|---|---|---|
| 0 | `KULIT` | nada kulit sosok (40.3.4) |
| 1 | `KULIT_RONA` | kulit × (1.0, 0.86, 0.84) (pipi, buku jari, bibir pucat) |
| 2 | `RAMBUT` | warna rambut (40.3.5) |
| 3 | `RAMBUT_KILAP` | rambut × 1.25 + biru (0.02, 0.03, 0.06) |
| 4 | `MATA_PUTIH` | #EDE8E0 (anak #F4F1EA, tua #E2DACB) |
| 5 | `IRIS` | warna iris (40.3.4) |
| 6 | `PUPIL` | #15121A (pupil ganda: #2A0E05) |
| 7 | `ALIS` | rambut × 0.8 (tua: rambut) |
| 8 | `BIBIR` | perempuan #B85A5E (riasan per gaya), laki-laki kulit × (0.92, 0.75, 0.74) |
| 9 | `KAIN1` | kain utama busana (40.3.8) |
| 10 | `KAIN2` | kain dalam/kedua |
| 11 | `TEPI` | tepi/sulam pangkat |
| 12 | `LOGAM` | logam hiasan (emas, perak, perunggu, besi) |
| 13 | `GIOK` | giok/permata |
| 14 | `SULAM` | warna sulaman/lambang kedua |
| 15 | `SAMAK` | kulit samak, sepatu, tali (#5A3A22 bawaan) |

#### 40.3.4 Wajah, kulit, mata (wilayah A, kepala depan 8 × 8 di (8, 8))

Koordinat wajah di bawah: `fx` 0-7 kiri→kanan **seperti dilihat dari depan** (fx 0-3 = sisi kanan pemakai), `fy` 0-7
atas→bawah. Kepala samping (0, 8) dan (16, 8) mendapat lanjutan rahang/pipi.

**Nada kulit (8, bobot bawaan; kota tema 38.T punya bobot sendiri 40.4.1):**

| Id | Nama | Dasar | Bobot |
|---|---|---|---|
| 0 | porselen | #F2D7C2 | 14 |
| 1 | gading | #EAC6A3 | 22 |
| 2 | gandum | #D8AC83 | 22 |
| 3 | sawo | #C0906A | 16 |
| 4 | sawo matang | #A2734E | 12 |
| 5 | cokelat | #835536 | 8 |
| 6 | cokelat tua | #5F3B25 | 6 |
| 7 | pucat arwah | #D9E0E3 | 0 (hanya arwah, hantu, Sisa Jiwa) |

**Bentuk wajah (4 per kelamin; lembar `wajah/bentuk_<k>_<n>.png` menulis bayangan rahang, tulang pipi, dagu):**
`lonjong` (oval: bayangan halus fy 6-7 di fx 0 dan 7), `persegi` (rahang tegas: nada 96 di fx 0-1/6-7 fy 6-7 + sisi kepala
fy 6-7), `hati` (dagu runcing: bayangan nada 96 di fx 0-1 dan 6-7 fy 6-7, dagu nada 104 di fx 3-4 fy 7), `bulat` (pipi rona
slot 1 di fx 1/6 fy 5; anak selalu `bulat`).

**Mata (8 bentuk; strip `buka` dan 15 strip lain diturunkan otomatis, 40.3.9):** setiap mata 2 px lebar, mata kanan
pemakai di fx 1-2, kiri di fx 5-6, baris fy 3-4.

| Id | Nama | Gambar (per mata, cermin untuk kiri) | Untuk |
|---|---|---|---|
| 0 | `phoenix` (danfeng) | fy3: [garis kelopak, garis kelopak terangkat ke luar 1 px di fy2 sudut luar]; fy4: [putih, iris] | laki-laki & perempuan dewasa (bobot tinggi) |
| 1 | `almond` | fy3: [kelopak, kelopak]; fy4: [putih, iris] | semua |
| 2 | `bulat` | fy3: [putih, iris]; fy4: [putih, iris] (iris 1 × 2) | anak, perempuan muda |
| 3 | `sipit` (mengantuk) | fy3 kosong-kulit; fy4: [kelopak, iris gelap] | tua, malas |
| 4 | `tajam` | fy3: [kelopak turun ke dalam (alis menekan)], fy4: [putih, iris]; sudut dalam nada gelap | penjaga, pedang, iblis |
| 5 | `lembut` (turun) | sudut luar fy4 sedikit turun (kelopak bawah slot 1) | penyayang, tabib |
| 6 | `tua` (berkerut) | `almond` + kerut nada 96 di fx 0/7 fy 3 dan garis dahi fy 1 fx 2-5 | usia TUA |
| 7 | `anak` | iris 2 × 2 penuh (fx 1-2 / 5-6, fy 3-4) dengan kilau 1 px putih di sudut atas | usia ANAK |

Perempuan menambah **bulu mata** (piksel `PUPIL` nada 80 di sudut luar fy 3) pada bentuk 0, 1, 2, 5.

**Iris (slot 5):** cokelat tua #3B2418 (60 %), cokelat #5A3A22 (25 %), hitam-cokelat #241812 (15 %). **Tanda ranah, Dao
dan fisik** (tidak acak; menimpa iris):

| Syarat | Iris | Tambahan |
|---|---|---|
| Dao Api, Inti Emas+ | ambar #E08A2A | — |
| Dao Es, Inti Emas+ | biru pucat #8FD8F0 | alis dan bulu mata nada kilap putih |
| Dao Petir, Inti Emas+ | ungu #9A6AF0 | — |
| Dao Ruang, Jiwa Baru Lahir+ | ungu tua #4A35B0 + 1 px bintang #E8E0FF | — |
| Dao Bayangan, Jiwa Baru Lahir+ | abu-ungu #6A5A88 | — |
| Abadi Sejati (ranah 9) | emas #F2C94C | bingkai `kilat` dipakai saat bertarung |
| Pupil Ganda (`PHYSIQUE_DOUBLE_PUPILS`) | **iris 2 × 2 emas kusam #C9A24A dengan DUA pupil**: piksel pupil di (fx1, fy3) dan (fx2, fy4) (cermin untuk kiri) | strip `kilat` memberi cincin emas terang; kelopak tajam |
| Tubuh Racun | hijau-ungu #6A8A3A, bibir slot 8 = #6A3A7A | — |
| Taixu | perak #C8CCD8 | — |
| Dou Di | cokelat-emas #A0702A | — |
| Tubuh Emas | iris emas #D8A83A; slot 0 × (1.04, 1.02, 0.94) | — |
| Pemakan (Devouring), Inti Emas+ | ungu kelam #3A1A4A; sklera slot 4 = #2A2430 | hanya saat `FIGHTING` (strip `kilat`) |
| Tulang Tertinggi | — | tanda tulang emas 2 px di dada atas (lapisan dasar badan (20, 20) fy0-1 fx 3-4), kelihatan di leher V kerah silang |
| Nadi Dewa Sesat | — | urat merah samar nada 90 di leher samping (lapisan dasar kepala sisi fy 7) |
| Tanpa Atribut | abu pucat #A8A8A8 | — |
| Iblis (peran atau jalan `iblis`) | merah #A01A1A | sklera tetap putih; strip `kilat` merah menyala |

**Alis (6, slot 7, fy 2):** `pedang` (jianmei: lurus naik ke luar, fx 1-2 dengan fx 1 satu piksel lebih tinggi pada fy 1,
laki-laki), `daun_willow` (liumei: tipis melengkung nada 110, perempuan), `tebal` (2 baris fy 1-2 di fx 1-2), `tipis`,
`berkerut` (miring ke dalam, sudut dalam di fy 3: pemarah/penjaga), `putih_panjang` (tua: slot 7 putih + kubus
`alis_panjang` 40.3.6).

**Hidung (slot 0 nada 104, 1-2 px):** dewasa fx 3-4 fy 5 (laki-laki 2 px, perempuan 1 px di fx 4); anak tidak ada; tua
nada 96.

**Mulut (6 bentuk diam; bibir slot 8):** `datar` (fx 3-4 fy 6), `senyum_tipis` (fx 3-4 fy 6 + sudut fx 2/5 fy 5 nada 110),
`tegas` (fx 2-5 fy 6 nada gelap tipis), `penuh` (perempuan: fx 3-4 fy 6 slot 8 + fy 7 fx 3-4 slot 8 nada 150), `kecil`
(fx 4 fy 6 saja; anak), `tua` (`datar` + garis senyum nada 96 di fx 2/5 fy 5-6).

**Rambut wajah (laki-laki DEWASA/TUA saja, slot 2 atau 7):** `tidak ada` (55 %), `kumis_tipis` (fx 2-5 fy 5 nada 100), `jenggot_kambing`
(fx 3-4 fy 7 + kubus `jenggot_pendek`), `jenggot_penuh` (fx 1-6 fy 7 + samping kepala fy 6-7 + kubus `jenggot_pendek`),
`jenggot_panjang` (tua/rahib/pertapa: kubus `jenggot_panjang`), `kumis_naga` (dua kubus `kumis_naga` menjuntai: tetua,
patriark, Raja Naga).

**Tanda wajah (slot dan bobot):** `huadian` (hiasan dahi perempuan: bunga plum 3 px merah #C0303A atau emas di fx 3-4 fy 1;
bangsawan, penari, peri; 20 % perempuan dewasa sipil kaya), `tanda_dao` (murid **Jiwa Baru Lahir+**: glif 2 × 2 warna
Dao di fx 3-4 fy 0-1, ditimpa poni bila ada; ciri ranah), `bekas_luka` (garis 3 px nada 150 slot 1 diagonal di pipi:
penjaga, pedang, rogue; 12 %), `tahi_lalat` (1 px #3A2418 di bawah mata kiri fx 6 fy 5: 8 %), `perban` (busana luka 2:
lengan), `coreng_jelaga` (jalan `alkimia`/`tempa`: 2 px abu di pipi fx 1 fy 5), `tinta_jari` (jalan `jimat`: ujung
lengan dasar fy 11 slot gelap).

#### 40.3.5 Rambut yang dilukis (kepala dasar + lapisan topi, slot 2/3)

Rambut dilukis di **dua lapisan**: kepala dasar (atas, belakang, samping, garis dahi fy 0) sebagai "akar", dan lapisan
topi (40-48, 0-16 dan sisi-sisinya) untuk volume, poni dan untaian. Bagian volume besar memakai kubus 40.3.6.

**Warna rambut (slot 2):** hitam #1B191E (62 %), hitam-biru #1D2333 (18 %), cokelat tua #3A291F (12 %), cokelat #57392A
(6 %), merah tua #5E1E1E (2 %, hanya bila jalan/peran `iblis` atau Dao Api ranah ≥ Jiwa Baru Lahir naik ke 100 %).
Usia TUA: abu #8E8C90 (40 %) atau putih #E6E3DC (60 %). Dao Es, Jiwa Baru Lahir+: perak #C9D6E6. Abadi Sejati / peran
`ABADI`: hitam-biru dengan kilau emas (slot 3 = #D8C080). Arwah: abu kebiruan #7A8690.

**Gaya rambut (id, kelamin/usia, lukisan, kubus 40.3.6):**

| Id | Nama | K / U | Lukisan kepala (atas · depan · belakang) | Kubus |
|---|---|---|---|---|
| `L1` | sanggul atas + ikat kain | L, M/D | atas: rambut disisir ke tengah dengan garis sisir nada 96; depan: garis dahi lurus fy 0, pelipis fy 1 di fx 0/7; belakang: rambut rapat sampai fy 7 | `sanggul` + `guan_rendah` (kain) |
| `L2` | sanggul + rambut tergerai (half-up) | L, M/D | seperti L1 + belakang topi: untaian turun lewat fy 7 | `sanggul`, `guan_rendah`/`guan_tinggi`, `rambut_atas`, `rambut_bawah` |
| `L3` | ekor kuda tinggi | L/P, M | atas: ditarik ke belakang-atas; depan: 2 untaian poni fx 1 dan 6 fy 0-2 di lapisan topi | `ekor_kuda_1`, `ekor_kuda_2`, `ikat_dahi_ekor` (bila pedang) |
| `L4` | panjang terurai + ikat dahi | L/P, M/D | atas: belahan tengah fx 3-4 nada 80; depan: poni samping fy 0-3 di fx 0-1/6-7; belakang: penuh | `rambut_atas`, `rambut_bawah` atau `rambut_bawah_panjang`, `poni_samping` ×2 |
| `L5` | gundul rahib | L, semua | kepala dasar = kulit nada 120 (kilap kulit kepala) + 6 titik ordinasi #8A5A3A di atas (2 baris 3) untuk Buddha; Tao **tidak** gundul | — |
| `L6` | sanggul tua putih | L, T | L1 dengan rambut slot 2 putih/abu, garis dahi mundur fy 1 (dahi tinggi) | `sanggul`, `guan_rendah` kayu, + `jenggot_*`, `alis_panjang` |
| `L7` | ikat kain kerja | L, M/D | rambut pendek di belakang; kain kepala dilukis di lapisan topi slot `KAIN2` fy 0-2 melingkar | `sanggul` kecil (skala 0.8) |
| `L8` | pendek acak | L/P, A/M | lapisan topi: ujung bergerigi di fy 0-2 dan sisi | — (pengemis, anak desa, tawanan) |
| `P1` | sanggul tinggi bertusuk | P, D/T | atas: disisir ke atas ke belakang; depan: garis dahi melengkung fy 0 dengan pelipis fy 2 | `sanggul_tinggi`, `tusuk_buyao` + `manik_buyao` ×3, `bunga_rambut` (bangsawan) |
| `P2` | sanggul kembar (shuangya) | P, A/M | belahan tengah; dua cepol | `cepol` ×2, `pita_rambut` ×2 (warna sekte/`TEPI`) |
| `P3` | setengah ikat berpita | P, M/D | belahan samping fx 2; poni tipis 3 untaian; belakang penuh | `sanggul` (skala 0.9), `rambut_atas`, `rambut_bawah`, `pita_rambut` ×2 |
| `P4` | ekor kuda tinggi pendekar | P, M/D | = `L3` dengan poni samping fx 0/7 fy 0-4 | `ekor_kuda_1`, `ekor_kuda_2`, `pita_rambut` ×1 |
| `P5` | kepang | P, A/M/D | belahan tengah; kepang dilukis di belakang | `kepang_1`, `kepang_2` (satu di belakang; varian dua kepang di depan bahu: desa, gurun) |
| `P6` | panjang terurai peri | P, M/D | = `L4` tanpa ikat dahi; hiasan dahi `huadian` | `rambut_atas`, `rambut_bawah_panjang`, `poni_samping` ×2, `bunga_rambut` |
| `P7` | sanggul rendah nenek | P, T | rambut abu disisir ke belakang | `sanggul_rendah`, `tusuk_guan` (kayu) |
| `A1` | dua kuncir anak (zongjiao) | L/P, A | kepala hampir gundul nada rambut 70 % cakupan (rambut tipis) | `jambul_anak` ×2 |
| `A2` | jambul depan | L, A | gundul + jambul depan dilukis fy 0-1 fx 3-4 | `jambul_anak` ×1 (depan) |

Hiasan rambut menurut **status** (palet slot `LOGAM`/`GIOK`/`KAIN2`), dipilih 40.4: `kain` (ikat kain warna busana),
`guan_kayu` (#7A5434), `guan_perak` (#C8CCD4), `guan_giok` (#5FA88C), `guan_emas` (#D8B04A), `mahkota_teratai` (emas +
giok: ketua sekte, patriark), `tusuk_kayu`, `tusuk_perak`, `buyao_emas` (bangsawan, Tetua Agung perempuan), `bunga`
(plum merah, peony merah muda, teratai putih untuk peri), `pita` (warna `TEPI`).

#### 40.3.6 Kubus rambut dan hiasan kepala (`client/sosok/busana/RambutBagian`, wilayah C)

Koordinat kubus dalam piksel model relatif pivot bagian induk (kepala: pivot leher, kepala −4..4, −8..0, −4..4; badan:
pivot leher, −4..4, 0..12, −2..2). "UV" = `texOffs` di atlas 128 × 128; jejak = 2(d + w) × (d + h) dari pembulatan ke
atas. Bagian berpasangan memakai **UV yang sama** (sisi kiri dengan `mirror`). Kolom "Pegas" merujuk rantai 40.5.8.

| Id | Induk | Pivot | Kotak (x0, y0, z0, w, h, d) | UV | Pegas |
|---|---|---|---|---|---|
| `sanggul` | kepala | (0, −8, 0.5) | (−1.5, −3, −1.5, 3, 3, 3) | (98, 47) | — |
| `guan_rendah` | sanggul | (0, −1, 0) | (−2, −1.2, −2, 4, 2, 4) | (110, 47) | — |
| `tusuk_guan` | guan_rendah | (0, −0.4, 0) | (−3.5, −0.3, −0.3, 7, 0.6, 0.6) | (70, 60) | — |
| `guan_tinggi` | sanggul | (0, −1.5, 0) | (−2, −2.5, −2, 4, 4, 4) | (64, 39) | — |
| `mahkota_teratai` | sanggul | (0, −1.5, 0) | (−2.5, −1.2, −2.5, 5, 2, 5) | (104, 39) | — |
| `rambut_atas` | kepala | (0, −8, 4.6) | (−4, 0, 0, 8, 8, 1) | (96, 24) | `RAMBUT` (kaku 0.6) |
| `rambut_bawah` | badan | (0, 0.3, 2.6) | (−3.5, 0, 0, 7, 10, 1) | (80, 24) | `RAMBUT` |
| `rambut_bawah_panjang` | badan | (0, 0.3, 2.6) | (−3.5, 0, 0, 7, 14, 1) | (64, 24) | `RAMBUT` |
| `ekor_kuda_1` | kepala | (0, −8.4, 3.2) | (−1, 0, −1, 2, 5, 2) diputar xRot 25° | (64, 47) | `EKOR` seg 1 |
| `ekor_kuda_2` | ekor_kuda_1 | (0, 5, 0) | (−0.9, 0, −0.9, 1.8, 6, 1.8) | (80, 39) | `EKOR` seg 2 |
| `cepol` (×2) | kepala | (±2.6, −8.2, 0.4) | (−1.5, −3, −1.5, 3, 3, 3) | (64, 54) | — |
| `sanggul_tinggi` | kepala | (0, −8, 1) | (−2.5, −3, −2, 5, 3, 4) | (72, 47) | — |
| `sanggul_rendah` | kepala | (0, −3.5, 4.2) | (−2, −1.5, 0, 4, 3, 3) | (76, 54) | — |
| `kepang_1` | badan | (0, 0.5, 2.4) (belakang) / (±2.6, 0.5, −2.2) (depan bahu) | (−1, 0, −1, 2, 6, 2) | (88, 39) | `KEPANG` seg 1 |
| `kepang_2` | kepang_1 | (0, 6, 0) | (−0.9, 0, −0.9, 1.8, 6, 1.8) + ikat ujung dilukis | (96, 39) | `KEPANG` seg 2 |
| `pita_rambut` (×2) | sanggul/cepol | (±1, −0.5, 1.5) | (−0.5, 0, 0, 1, 6, 0.3) | (90, 47) | `PITA` |
| `tusuk_buyao` | sanggul_tinggi | (1.5, −1.5, 0) yRot −20° | (−0.5, −0.5, −0.5, 5, 1, 1) | (86, 60) | — |
| `manik_buyao` (×3) | tusuk_buyao | (4.2, 0.4, 0) jarak 0.8 | (−0.4, 0, −0.4, 0.8, 3, 0.8) | (108, 54) | `BUYAO` |
| `bunga_rambut` | kepala | (3.2, −7.4, 0) | (−1, −1, −0.5, 2, 2, 1) | (64, 60) | — |
| `jenggot_pendek` | kepala | (0, −0.6, −4.2) | (−2, 0, −0.6, 4, 4, 1) | (94, 54) | `JENGGOT` |
| `jenggot_panjang` | kepala | (0, −0.6, −4.2) | (−2, 0, −0.6, 4, 8, 1) | (114, 24) | `JENGGOT` |
| `kumis_naga` (×2) | kepala | (±1.8, −2.2, −4.3) | (−0.5, 0, −0.5, 1, 4, 1) | (104, 54) | `KUMIS` |
| `alis_panjang` (×2) | kepala | (±3.4, −5.6, −4.2) | (−0.4, 0, −0.4, 0.8, 3, 0.8) | (112, 54) | `ALIS` |
| `jambul_anak` (×2) | kepala | (±2, −8, 0) / (0, −8, −2.5) | (−1, −2, −1, 2, 2, 2) | (116, 54) | — |
| `poni_samping` (×2) | kepala | (±3.6, −6.5, −2.6) | (−0.5, 0, −0.5, 1, 6, 1) | (94, 47) | `PONI` |
| `ikat_dahi_ekor` (×2) | kepala | (±0.8, −6.2, 4.4) | (−0.5, 0, 0, 1, 5, 0.3) | (90, 54) | `PITA` |

Atlas C terisi rapat sesuai tabel (diperiksa `tools/cek_sosok.py`: tidak ada tumpang tindih, semua di dalam
(64, 24)-(128, 64)). Ukuran kubus pecahan memakai UV jejak bilangan bulat; generator melukis seluruh jejak.

**Topi:** sosok yang memakai topi `KotaGear`/`Headwear` (40.4) menyembunyikan `sanggul_tinggi`, `guan_*`,
`mahkota_teratai`, `tusuk_*`, `manik_buyao`, `bunga_rambut`, `jambul_anak`, `cepol` dan lapisan topi kepala bagian atas
(baris atas wilayah topi dilukis ulang tanpa volume rambut di atlas varian "bertopi", lihat kunci cache 40.3.9). Tabel
`Busana.TOPI_MENUTUP[kind]` (40K) membaca konstanta `KotaGear.*`/`Headwear.*` (hanya baca).

#### 40.3.7 Busana: gaya, lukisan kulit, kubus (wilayah A lapisan dasar + luar, wilayah D)

**Gaya busana (`GayaBusana`, 22 nilai).** Lukisan: "dasar" = lapisan dasar badan/lengan/kaki (selalu ada); "luar" =
jaket/lengan baju/celana (memberi tebal 0.25-0.5 px). Kubus dari tabel kubus busana di bawah.

| Id | Nama (budaya) | Lukisan dasar · luar | Kubus | Kain1/Kain2/Tepi bawaan |
|---|---|---|---|---|
| `HANFU_L` | Jubah kerah silang pria (jiaoling paofu) | dasar: baju dalam putih `KAIN2` dengan V leher; luar: jubah `KAIN1` kerah silang **右衽** (garis tepi `TEPI` 1 px dari bahu kiri pemakai turun ke pinggang kanan), lengan baju lebar sampai pergelangan, celana `KAIN1` gelap | `rok_panjang_*` (4), `lengan_lebar_manset` ×2, `lengan_lebar_kantong` ×2, `sabuk`, `liontin_giok` | dari pemakai |
| `HANFU_P` | Ruqun dada tinggi (qixiong ruqun) | dasar: baju dalam; luar: baju atas pendek `KAIN2` dengan lengan lebar, rok `KAIN1` diikat **di dada** (pita `TEPI` di fy 2 badan + dua pita kubus) | `rok_panjang_*`, `lengan_lebar_*`, `pita_sabuk` ×2 (dari dada, pivot badan y 3), `selendang_*` (opsional bangsawan/peri) | — |
| `PENDEK` | Jubah pendek + celana ikat (duanda) | luar: jubah sampai lutut, celana `KAIN2` dengan balutan betis (garis silang `SAMAK` di kaki fy 8-12) | `rok_pendek_*`, `sabuk`, `pelindung_lengan` ×2 opsional | — |
| `TEMPUR` | Jubah tempur (lengan sempit, rok belah) | luar: lengan sempit diikat, pelat sabuk `LOGAM`, rok belah depan | `rok_pendek_sisi` ×2, `rok_zirah` (depan-belakang kain), `pelindung_lengan` ×2, `sabuk`, `bahu_zirah` kiri (opsional) | — |
| `TAO` | Jubah Tao (daopao) | luar: jubah biru-hitam lurus, kerah persegi putih, yin-yang `_tetap` 6 × 6 di punggung jaket (33, 37), tepi lebar `KAIN2` | `rok_panjang_*`, `lengan_lebar_*`, `sabuk` tali | #2B3A5C / #E8E4D8 / #E8E4D8 |
| `RAHIB` | Jubah rahib Buddha | dasar: baju abu; luar: jubah abu #8A8278 atau kuning tanah #B08A3A, kerah silang, kaus kaki putih + sandal jerami | `rok_panjang_*`, `lengan_lebar_*` (kantong kecil) | — |
| `KASAYA` | Kasaya kepala biara | `RAHIB` + kain kasaya merah-emas menyilang bahu kiri (lukisan petak 2 × 2 `_tetap`) | + `cincin_kasaya` | #8A2A1E / #D8B04A |
| `IBLIS` | Jubah iblis | luar: hitam #16121A dengan lapisan dalam merah darah #6A1420 di tepi, kerah tinggi, motif tulang `_tetap` di dada | `rok_panjang_*` + `rumbai_iblis` ×3 (ujung sobek), `kerah_tinggi`, `jubah_luar` (pemimpin) | — |
| `SAUDAGAR` | Baju saudagar | luar: jubah kerah bundar (yuanling) brokat `KAIN1` dengan pola koin `_tetap`, rompi bertepi bulu | `rok_panjang_*` (lebih lebar: `xScale 1.06`), `kantong`, `sabuk` | #7A1E22 / #D8B04A |
| `PETANI` | Baju petani | dasar: baju pendek kasar #8A7A60, celana digulung (kaki fy 8-12 kulit), kaki telanjang/sandal | — (tanpa rok; ringan) | — |
| `BANGSAWAN` | Sutra berlapis | luar: tiga lapis kerah terlihat (`KAIN2`, `TEPI`, `KAIN1`), sulaman emas `_tetap` di bahu dan ujung lengan | `rok_panjang_*`, `lengan_lebar_*`, `jubah_luar` (ekor jubah), `liontin_giok`, `selendang_*` (P) | — |
| `ABADI` | Jubah abadi putih-emas | luar: putih #F4EFE2 dengan awan emas `_tetap` di ujung rok dan lengan | `rok_panjang_*`, `lengan_lebar_*`, `selendang_1/2/punggung` (pibo melayang) | #F4EFE2 / #E8D9A8 / #D8B04A |
| `PENJAGA` | Seragam penjaga (dalam zirah) | dasar: baju tebal berlapis `KAIN1`; luar: celana + sepatu bot `SAMAK` sampai lutut | `rok_zirah` ×2 (logam), `bahu_zirah` ×2 (bila `KotaGear` tidak memberi) | — |
| `PEJABAT` | Jubah pejabat kerah bundar + buzi | luar: jubah `KAIN1`, **lencana pangkat (buzi) 4 × 4** `_tetap` di dada (bangau = sipil, harimau = militer) | `rok_panjang_*`, `sabuk` (sabuk giok), `lengan_lebar_*` | — |
| `SARJANA` | Jubah sarjana | luar: biru muda atau putih, kerah lurus, tepi hitam lebar | `rok_panjang_*`, `lengan_lebar_*` | #DDE6EE / #1E1A18 |
| `COMPANG` | Kain compang | luar: tambalan 3 warna `_tetap` acak dari benih, ujung bergerigi (alfa) | `rumbai_iblis` ×2 (sebagai sobekan, warna kain) | — |
| `ANAK` | Baju anak | dasar: baju pendek berkancing samping, celana ikat; perut `dudou` merah (bayi tidak ada) | — | warna cerah |
| `PELAYAN` | Baju pelayan | luar: jubah pendek `KAIN1`, celemek | `rok_pendek_*`, `celemek` | — |
| `LAUT` | Baju pelaut | dasar: rompi bergaris biru-putih, celana digulung | `sabuk` tali | — |
| `GURUN` | Jubah gurun | luar: jubah longgar pasir #C8A878 dengan selempang, kain penutup kepala dilukis | `rok_panjang_*`, `jubah_luar_bahu` | — |
| `KAFAN` | Kain kafan / arwah | luar: putih #E8E6E0, kerah **左衽** (kebalikan), tanpa sabuk | `rok_panjang_*`, `lengan_lebar_*` | — |
| `HAKIM_BAWAH` | Jubah hakim Dunia Bawah | luar: merah tua #5A1418 dengan buzi kepala harimau hitam | `rok_panjang_*`, `lengan_lebar_*`, `sabuk` | — |

**Lapisan tambahan atas gaya (`Tambahan`):** `jubah_bepergian` (`jubah_luar` + `jubah_luar_bahu` + `caping_bambu` +
`caping_puncak` + `buntalan` di punggung), `berkabung` (busana diganti putih kasar, ikat kepala putih), `pesta` (tepi
emas, `liontin_giok`, `bunga_rambut`), `kerja` (lengan diikat ke belakang: `lengan_lebar_*` disembunyikan, tali silang
`SAMAK` dilukis di punggung; celemek untuk dapur/alkimia), `tidur` (baju dalam putih saja, rambut terurai), `zirah_tugas`
(`bahu_zirah` ×2 + `rok_zirah`), `penyamaran` (`jubah_luar` bertudung gelap + cadar dilukis).

**Kubus busana (`client/sosok/busana/BusanaBagian`, wilayah D):**

| Id | Induk | Pivot | Kotak (x0, y0, z0, w, h, d) | UV | Gerak / pegas |
|---|---|---|---|---|---|
| `rok_panjang_depan` | badan | (0, 12, −2.1) | (−4.5, 0, −0.8, 9, 11.5, 0.8) | (80, 64) | dibuka kaki depan (40.5.8) |
| `rok_panjang_belakang` | badan | (0, 12, 2.1) | (−4.5, 0, 0, 9, 11.5, 0.8) | (100, 64) | dibuka kaki belakang |
| `rok_panjang_sisi` (×2) | badan | (±4.2, 12, 0) | (−0.4, 0, −2.5, 0.8, 11.5, 5) | (20, 64) | ikut `zRot` kaki |
| `rok_pendek_depan/belakang` | badan | sama | tinggi 6 | (24, 102) / (44, 102) | sama |
| `rok_pendek_sisi` (×2) | badan | sama | (−0.4, 0, −2.5, 0.8, 6, 5) | (0, 82) | sama |
| `lengan_lebar_manset` (×2) | lengan | (lengan kanan: −1, 6, 0; kiri: 1, 6, 0) | lebar (−3, 0, −3, 6, 4, 6); ramping (−2.5, 0, −3, 5, 4, 6) | (28, 82) | — |
| `lengan_lebar_kantong` (×2) | manset | (0, 2, 2.2) (belakang-bawah lengan) | (−2.5, 0, 0, 5, 7, 2) | (70, 82) | `LENGAN` (menggantung ke bawah dunia) |
| `pelindung_lengan` (×2) | lengan | (∓0.5, 6, 0) | (−2.5, 0, −2.5, 5, 4, 5) ramping (−2, 0, −2.5, 4, 4, 5) | (84, 82) | — |
| `jubah_luar` | badan | (0, 0.2, 2.4) | (−4.5, 0, 0, 9, 17, 1) | (0, 64) | `JUBAH` (seperti jubah pemain) |
| `jubah_luar_bahu` | badan | (0, 0, 0) | (−5, −0.5, −3, 10, 3, 6) | (0, 93) | — |
| `kerah_tinggi` | badan | (0, 0, 0) | (−4.5, −2.5, −2.5, 9, 3, 5) | (44, 93) | — |
| `sabuk` | badan | (0, 6.5, 0) | (−4.5, 0, −2.5, 9, 2, 5) | (64, 102) | — |
| `pita_sabuk` (×2) | sabuk | (±1, 1.6, −2.6) | (−0.5, 0, −0.3, 1, 6, 0.3) | (92, 102) | `PITA` |
| `liontin_giok` | sabuk | (2.2, 1.8, −2.7) | (−1, 0, −0.5, 2, 4, 1) (giok atas, rumbai bawah dilukis) | (6, 110) | `RUMBAI` |
| `kantong` | sabuk | (−2.8, 1.5, −2.6) | (−1, 0, −1, 2, 3, 2) | (12, 110) | `RUMBAI` lemah |
| `labu` | sabuk | (−4.6, 1.2, 0.4) | (−1.5, 0, −1.5, 3, 4, 3) | (96, 102) | `RUMBAI` lemah |
| `bahu_zirah` (×2) | lengan | (lengan: ∓0.5, −2, 0) | (−2.5, −0.5, −2.5, 5, 3, 5) | (72, 93) | — |
| `rok_zirah` (×2) | badan | depan (0, 11, −2.6) / belakang (0, 11, 2.6) | (−4.5, 0, −0.5, 9, 6, 1) | (108, 102) | dibuka kaki (kaku 1.6) |
| `selendang_1` (×2) | lengan | (lengan: ∓1, 7, 2) | (−1, 0, −0.25, 2, 8, 0.5) | (32, 93) | `SELENDANG` seg 1 |
| `selendang_2` (×2) | selendang_1 | (0, 8, 0) | (−1, 0, −0.25, 2, 8, 0.5) | (38, 93) | `SELENDANG` seg 2 |
| `selendang_punggung` | badan | (0, 4, 3) | (−6, 0, −0.25, 12, 3, 0.5) melengkung xRot 30° | (20, 110) | `SELENDANG` (busur) |
| `celemek` | badan | (0, 4, −2.4) | (−3.5, 0, −0.5, 7, 10, 1) | (12, 82) | dibuka kaki depan (lemah) |
| `lencana_giok` | sabuk | (−1.6, 1.8, −2.7) | (−1, 0, −0.5, 2, 3, 1) | (46, 110) | `RUMBAI` |
| `cincin_kasaya` | badan | (2, 3, −2.6) | (−1, −1, −0.5, 2, 2, 1) | (52, 110) | — |
| `buntalan` | badan | (0, 1, 2.2) | (−3, 0, 0, 6, 7, 3) diputar zRot 20° | (52, 82) | — |
| `rumbai_iblis` (×3) | badan | (−3/0/3, 12, ±2.2) | (−1, 0, −0.5, 2, 6, 1) | (0, 110) | `RUMBAI` |
| `kerah_bulu` | badan | (0, −0.5, 0) | (−5, −1, −3, 10, 2, 6) | (92, 93) | — |
| `caping_bambu` | kepala | (0, −8.2, 0) | (−6, −1, −6, 12, 1, 12) (kerucut: tepi) | (32, 64) | — |
| `caping_puncak` | caping_bambu | (0, −1, 0) | (−3, −2, −3, 6, 2, 6) | (0, 102) | — |

Semua kubus busana dan rambut adalah anak bagian `PlayerModel` di **satu model** `SosokBusanaModel extends PlayerModel`
(mesh 128 × 128, kepala/badan/lengan/kaki tanpa kubus kulit, pola `KotaGear.skeleton()`), sehingga pose cast, duel,
meditasi, tugas dan klip menggerakkannya bersama tubuh lewat `PlayerModelMixin`. Bagian yang tidak dipakai sosok itu
`visible = false` (biaya nol).

#### 40.3.8 Palet busana: Dao, unsur, sekte, pangkat, budaya, ranah

Urutan penentuan warna slot `KAIN1/KAIN2/TEPI/LOGAM/GIOK/SULAM` (yang pertama berlaku menang):

1. **Seragam sekte** (murid pemain dengan seragam dirancang, `JubahLook.of(state)` ≠ null; NPC sekte 38/36 dengan
   `LambangCache.get("npc:<sekte>")`): `KAIN1` = `JubahLook.robe(...)` pangkat itu, `TEPI` = `JubahLook.trim(...)`,
   `SULAM` = warna bidang lambang, gaya = potongan seragam (`CUT_PANJANG` → `HANFU_L`/`HANFU_P` menurut kelamin,
   `CUT_PENDEK` → `PENDEK`, `CUT_TEMPUR` → `TEMPUR`). **Lambang** (gambar sulaman aula 64 × 64: `TextureManager.getTexture(JubahLook.embroidery(entry))`, dipakai hanya bila
   `instanceof DynamicTexture`, lalu `getPixels()`; `LambangAtlas` tidak disunting):
   diperkecil 2:1 ke 8 × 8 di punggung jaket (32, 36)-(40, 44) mulai **Murid Inti** atau ranah **Jiwa Baru Lahir** (yang
   lebih dulu), dan 3 × 3 di dada kiri pemakai pada jaket (25, 37) mulai **Murid Dalam**. Stempel lambang masuk kunci cache.
2. **Peran** (40.4.1): warna tetap per peran/budaya.
3. **Dao** (murid tanpa seragam, NPC kultivator tanpa peran berwarna): `KAIN1` = warna Dao (`Dao.color()`) digelapkan ke
   kecerahan 0.55-0.7 (kain tidak menyilaukan), `KAIN2` = putih gading #EDE6D6, `SULAM` = warna Dao asli.

   | Dao | KAIN1 (kain) | SULAM | LOGAM | Motif sulam `_tetap` di ujung rok |
   |---|---|---|---|---|
   | Pedang | #7C8CA8 | #D8E6FF | perak | garis pedang miring |
   | Api | #8E2E1A | #FF6A2A | emas | lidah api |
   | Es | #5E8EA0 | #9FF3FF | perak | kristal salju |
   | Petir | #4A3A78 | #B57CFF | perak | kilat zigzag |
   | Tubuh | #7A5A34 | #D9A05B | perunggu | gunung |
   | Kehidupan | #3E6E46 | #6CFF8A | giok | daun |
   | Bayangan | #2E2A3A | #7A6AA0 | besi gelap | awan gelap |
   | Ruang | #2A2060 | #5A3FD0 | perak | bintang |

4. **Ranah** (sebelum Dao): `KAIN1` = warna ranah (`Realm.color()`) digelapkan 0.6; Fana = kain kasar #8A7A60.

**Tepi pangkat sekte (`TEPI`)** memakai `PhysiqueVisuals.RANK_TRIM` bila seragam tidak memberi: Tercatat abu, Luar
putih, Dalam biru, Inti giok, Tetua ungu, Tetua Agung putih-emas. Lebar tepi naik dengan pangkat: 1 px (Tercatat,
Luar), 1 px + garis kedua (Dalam, Inti), 2 px + sulam pola awan (Tetua, Tetua Agung).

**Tanda ranah (bukan aura; aura tetap `AuraEngine`):** Pemurnian Qi: tidak ada; Fondasi: kain tanpa tambalan (murid
miskin Fana/Qi boleh bertambal 1-2 petak); Inti Emas: `LOGAM` naik ke emas kusam dan `liontin_giok` muncul; Jiwa Baru
Lahir: `tanda_dao` di dahi, iris Dao (40.3.4), lambang di punggung; Transformasi Dewa: sulam ujung rok bercahaya halus
(nada slot `SULAM` +1 tingkat), hiasan rambut naik satu tingkat (kain → kayu → perak → giok → emas); Penyatuan Dao: rambut
mendapat kilau slot 3 = warna Dao 40 %; Naik Abadi dan Abadi Sejati: gaya `ABADI` boleh menimpa bila tidak bersekte
dirancang, selendang pibo muncul, `mahkota_teratai` untuk ketua.

**Palet budaya (peran NPC)** ada di kolom tabel 40.4.1. Variasi per individu: `KAIN1` digeser ±6 % kecerahan dan ±8°
rona dari benih (dalam ruang HSV) agar dua penjaga tidak identik; `TEPI`, `LOGAM`, warna sekte **tidak** digeser.

#### 40.3.9 Penyusunan di runtime (`client/sosok/kulit/**`)

- `KulitPenyusun.susun(SosokRupa rupa) → NativeImage(128, 128)`: urutan lapisan:
  1. kosongkan (alfa 0);
  2. tubuh dasar `kulit/dasar_<kelamin>_<usia>_<lebar|ramping>.png` (slot 0);
  3. bentuk wajah, mata, alis, hidung, mulut, rambut wajah, tanda;
  4. pakaian dalam `busana/dalam_<kelamin>.png` (tidak pernah telanjang bila busana luar transparan);
  5. busana gaya: `busana/<gaya>_dasar.png`, `busana/<gaya>_luar.png`, tepi pangkat `busana/<gaya>_tepi<n>.png`;
  6. tambahan (bepergian, kerja, pesta, luka: `luka/perban_lengan.png`);
  7. alas kaki `busana/kaki_<jenis>.png` (sepatu kain, sepatu bot, sandal jerami, telanjang);
  8. rambut kepala `rambut/<gaya>_dasar.png` lalu `rambut/<gaya>_topi.png` (varian `_bertopi` bila topi menutup);
  9. lambang sekte (40.3.8), lencana pangkat, `huadian`, `tanda_dao`;
  10. jejak UV kubus rambut (C) dan busana (D) dari `rambut/kubus/<id>.png` dan `busana/kubus/<id>.png` dengan palet sosok;
  11. strip wajah B1/B2: setiap strip dibuat dengan menyusun ulang wajah 8 × 8 (lapisan 2-3) memakai varian mata/mulut
      (`wajah/mata_<bentuk>_<keadaan>.png`, `wajah/mulut_<bentuk>_<keadaan>.png`), lalu memotong baris fy 2-5 / fy 5-7.
- `SosokRupa` = record yang dihitung `RupaResolver.dari(Sosok, Gaya, PeranRupa, warna...)`: indeks semua bagian + 16
  warna slot. **Kunci cache** = `long` hash FNV-1a 64 dari semua field `SosokRupa` (termasuk stempel lambang dan "bertopi").
- `KulitCache`: `LinkedHashMap<Long, Entri>` LRU, `Entri(Identifier id, DynamicTexture tekstur, long dipakai)`. Id tekstur
  `kultivator:sosok/<16 hex>` didaftarkan `Minecraft.getInstance().getTextureManager().register(id, new
  DynamicTexture(() -> "kultivator sosok", gambar))`; dilepas dengan `release(id)` saat keluar LRU, saat dunia ditutup
  (`ClientPlayConnectionEvents.DISCONNECT`, verifikasi nama Fabric) dan saat `uji penampilan muat_ulang`.
- **Anggaran per frame** (40.6.1): paling banyak N penyusunan per frame dan paling lama 2.0 ms (diukur `System.nanoTime`).
  Sosok yang belum tersusun memakai **atlas cadangan**: 24 PNG siap pakai `textures/sosok/cadangan/<kelamin>_<usia>_<kelas>.png`
  (kelas: `sipil`, `kultivator`, `penjaga`, `rahib`, `bangsawan`, `iblis`; dihasilkan generator dari sosok contoh) →
  tidak pernah kulit vanilla atau kotak polos. Bila lembar bagian hilang/rusak: log satu baris, bagian dilewati, atlas
  tetap jadi.
- Penyusunan memakai `int[]` 128 × 128 di Java (cepat), lalu satu kali salin ke `NativeImage.setPixel` per piksel
  terisi; lembar bagian dibaca sekali (`NativeImage.read`) dan disimpan sebagai `int[]` ARGB (`BagianCache`, ±1.2 MiB
  untuk semua lembar, dimuat malas per kelompok).

#### 40.3.10 Resep data dan RNG bersama

- `assets/kultivator/sosok/resep.json` (ditulis `tools/gen_sosok_resep.py`, dibaca Java sekali): daftar bagian per
  kelompok (`wajah`, `mata`, `alis`, `mulut`, `rambut`, `busana`, `kaki`, `tanda`) dengan **bobot per kelamin × usia ×
  kelas peran**, tabel warna (kulit, rambut, iris), tabel peran 40.4.1 (gaya busana, palet, hiasan, topi, kebijakan K/U,
  prop bawaan), tabel pangkat 40.4.2, tabel jalan 40.4.3. Format: objek JSON biasa (tanpa komentar), kunci string
  Indonesia sama dengan id di dokumen ini. Java membaca dengan `com.google.gson.JsonParser` (sudah ada di Minecraft).
- **RNG:** `SplitMix64` — `z = (seed += 0x9E3779B97F4A7C15); z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9; z = (z ^ (z >>>
  27)) * 0x94D049BB133111EB; return z ^ (z >>> 31)`; Python memakai `& 0xFFFFFFFFFFFFFFFF` di setiap langkah. Benih
  penyusun = `benih × 0x100000001B3 ^ kelompok.hashFnv32()`; pilih berbobot dengan `nextLong() >>> 11` dibagi 2^53.
  Urutan tarikan tetap seperti urutan kelompok di atas (wajah, mata, alis, mulut, rambut wajah, tanda, gaya rambut,
  warna rambut, kulit, iris, variasi kain). Dengan begitu `gen_sosok_pratinjau.py` menghasilkan atlas yang **sama
  piksel demi piksel** dengan Java (diuji `uji penampilan dump`, 40.6.4).

#### 40.3.11 Generator Python dan pemeriksaan visual

| Berkas | Isi |
|---|---|
| `tools/sosok_lib.py` | `SplitMix64`, `tangga()` (sama persis 40.3.2), `susun(rupa)` cermin `KulitPenyusun`, `UV` (tabel 40.3.6-40.3.7), `cek_uv()` |
| `tools/gen_sosok_bagian.py` | semua lembar bagian (`kulit/`, `wajah/`, `rambut/`, `rambut/kubus/`, `busana/`, `busana/kubus/`, `luka/`) dengan pengodean 40.3.2; dibuat dari fungsi gambar piksel per bagian (bukan derau acak): misalnya `jubah_hanfu()` menggambar kerah silang, lipatan lengan (nada 96 tiap 3 px), tepi, ujung rok |
| `tools/gen_sosok_resep.py` | `resep.json` (40.3.10) |
| `tools/gen_sosok_cadangan.py` | 24 atlas cadangan dari sosok contoh tetap |
| `tools/gen_sosok_pratinjau.py` | pratinjau (tidak dikirim ke jar) ke `tools/keluaran/sosok/`: (a) setiap gaya rambut × kelamin pada kepala 3 arah, (b) setiap gaya busana pada tubuh depan/belakang, (c) 8 sosok acak per peran 40.4, (d) lembar kontak "galeri" 16 × 8 sosok, (e) render isometrik sederhana tubuh + kubus (proyeksi kotak, pola `tempat_iso.py` 38MD bila sudah ada; bila belum, `sosok_iso()` sendiri), semua diperbesar 8× |
| `tools/cek_sosok.py` | UV tidak tumpang tindih dan di dalam wilayah; setiap lembar ukuran benar; setiap slot G kelipatan 16; tidak ada piksel A > 0 di wilayah cadangan; konstanta UV di `BusanaKatalog.java` sama dengan `sosok_lib.UV` (regex) ; setiap peran 40.4 punya baris resep |

Pemeriksaan visual wajib (aturan 38.O.0): pemeriksa **membuka setiap pratinjau dengan Read**, mencatat di
`docs/progres/penampilan-40K.md` per gambar: "dibaca, baik / diperbaiki: <apa>". Kriteria: wajah terbaca sebagai wajah di
8 px (mata, alis, mulut jelas), laki-laki dan perempuan bisa dibedakan dari depan **tanpa** melihat rambut kubus, tidak
ada piksel slot salah (misalnya kulit berwarna kain), kerah 右衽 benar, warna sekte terbaca dari 16 blok (pratinjau kecil
1×).

---------------------------------------------------------------------------------------------------------------------

### 40.4 Tabel rupa per peran dan per murid (data `resep.json`, kunci `peran.<id>`)

Kunci rupa adalah **string** (`PeranRupa`), bukan ordinal enum: `NpcRole.id()` untuk NPC K/38 (peran 38 yang belum ada
di enum sudah punya baris; begitu 38NL menambah konstanta, rupa langsung berlaku), `d.<jenis>` untuk orang Dimensi D,
`liar.<sekte 0-7>` untuk Kultivator Liar, `utusan`, `penonton.<jenis>`, `murid.<pangkat>` dan `jalan.<id>` untuk murid.
Peran tanpa baris memakai baris kelompok jadwal 38.N.2 (`kelompok.PEKERJA` dst.), lalu `kelompok.SIPIL`.

**Palet bernama** (dipakai di tabel; sama dengan `tools/tempat_palet.py` 38 bila nama sama): `lak` #8E1F1F, `emas`
#C9A24A, `emas_terang` #E8C860, `giok` #5FA88C, `kayu` #5A3A22, `kayu_muda` #9C7448, `besi` #7C8288, `perunggu` #8C6A3A,
`perak` #C8CCD4, `kertas` #E8DDC0, `tinta` #1E1A18, `sutra_biru` #3A5A8C, `nila` #2B3A5C, `kasar` #8A7A60, `gading`
#EDE6D6, `putih` #F4EFE2, `hitam` #1A1A20, `hijau_teh` #3E6E4E, `ungu_tua` #4A2A5A, `darah` #6A1420, `pasir` #C8A878,
`laut` #2E5A7A, `abu` #8A8278, `kuning_tanah` #B08A3A, `merah_muda` #E8A8B8, `biru_langit` #8EB8D8, `teh` #6E8E4E,
`cokelat` #6A4A30, `teal` #2E7A78.

**Kolom:** K = kebijakan kelamin (L, P, C<p> = campuran p % perempuan); U = usia (A anak, M muda, D dewasa, T tua; persen
bila campuran); Gaya = gaya busana 40.3.7 + tambahan; Kain = `KAIN1 / KAIN2 / TEPI / LOGAM`; Rambut L · P = gaya 40.3.5
(hiasan); Topi = perlengkapan kepala yang **tetap** dari `KotaGear` (K, 38MD) / `Headwear` (D) / 38 pita 16-47 (38MD,
38TV) / kubus Sosok (S); Prop = `PropId` 40.5.7 di tangan saat diam; Sikap · Jalan = klip sikap diam 40.5.10 dan profil
gaya jalan 40.5.6.

#### 40.4.1 NPC (semua peran)

**A. Kota lama (modul K, 15 peran yang ada).**

| Peran | K | U | Gaya | Kain | Rambut L · P | Wajah | Topi | Prop | Sikap · Jalan |
|---|---|---|---|---|---|---|---|---|---|
| `juru_lelang` | C25 | D | `PEJABAT` (buzi koin) | lak / gading / emas / emas | L1 · P1 (tusuk emas) | phoenix, senyum_tipis | K `FUTOU` | kipas_lipat (23) | tangan_belakang · bangsawan |
| `juru_lelang_abadi` | C25 | D | `PEJABAT` + pesta | emas_terang / putih / giok / emas | L2 (guan giok) · P1 (buyao emas) | phoenix | K `FUTOU_GOLD` | kipas_lipat | tangan_belakang · bangsawan |
| `pemilik_teh` | C55 | D 70, T 30 | `HANFU_L`/`HANFU_P` + kerja (celemek) | hijau_teh / gading / kayu_muda / perunggu | L7 · P3 (tusuk kayu) | lembut, senyum_tipis | K `TEA` (rambut K disembunyikan, 40.4.5) | teko (5) | tangan_lengan · santai |
| `makelar_gelap` | C30 | D | `HANFU_L` + penyamaran | hitam / ungu_tua / emas / emas | L4 · P6 | tajam, menyeringai, bekas_luka 40 % | K `BROKER` (tudung, cadar) | kantong_uang (29) | lipat_tangan · iblis |
| `pedagang_pil` | C40 | D | `SAUDAGAR` | teh / gading / emas / perunggu | L1 · P3 | almond, senyum_tipis | K `MERCHANT` | labu_pil (38) | tangan_lengan · pedagang |
| `pedagang_abadi` | C40 | D | `SAUDAGAR` + pesta | emas_terang / putih / giok / emas | L2 · P1 | almond | K `MERCHANT_GOLD` | sempoa (12) | tangan_lengan · pedagang |
| `pandai_pusaka` | C10 | D | `PENDEK` + kerja | kasar / cokelat / lak / besi | L7 · P4 | tajam, coreng_jelaga, jenggot_penuh 40 % | K `SMITH` | palu (8) | berkacak · gagah |
| `penjaga` | C15 | M 30, D 70 | `PENJAGA` | nila / kasar / lak / besi | L1 · P4 | tajam, bekas_luka 20 % | K `GUARD` | tombak (35) | tombak · militer |
| `warga` | C50 | A 15, M 20, D 45, T 20 | `PETANI` 40 %, `HANFU_L/P` kasar 40 %, `PENDEK` 20 % | kasar / gading / cokelat / perunggu | L1, L7, L8, L6 · P2, P3, P5, P7 | acak | K `FOLK_*` menurut look (lama) | — | santai_l/p, tua, anak · santai / tua_tongkat / anak_lompat |
| `pengembara` | C35 | M 40, D 60 | `TEMPUR` + bepergian 50 % | Dao (40.3.8) | L2, L3 · P4, P3 | tajam, bekas_luka 30 % | K `WANDERER_DOULI` / `WANDERER_GUAN` (rambut K disembunyikan) | — (pedang NpcGear) | gagang · gagah/anggun |
| `saingan` | C30 | M 60, D 40 | `TEMPUR` (pemimpin: + `jubah_luar`) | warna kelompok / hitam / emas / besi | L3 · P4 (pemimpin L2 guan emas · P1) | tajam, menyeringai | K `RIVAL` / `RIVAL_LEADER` | — | gagang / lipat_tangan · gagah |
| `abadi` | C50 | M 30, D 70 | `ABADI` | putih / gading / emas / emas | L2 (guan giok) · P6 | phoenix, kilat saat bertarung | K `IMMORTAL` (rambut, selendang K disembunyikan) | — | tangan_lengan · bangsawan (melayang 0.02) |
| `penceramah` | C30 | T | `TAO` | putih / gading / emas / emas | L6 (jenggot_panjang, alis_panjang) · P7 | tua, tanda_dao | K `SAGE` (jenggot, alis K disembunyikan) | kebut (65) | tangan_belakang · tua (tanpa tongkat) |
| `utusan` | L | D | `PEJABAT` (buzi bangau emas) | emas_terang / putih / giok / emas | L1 (di bawah topi) | tegas | K `ENVOY` | papan_titah (66) | tangan_belakang · militer |
| `penjaga_kebun` | C70 | M 50, D 50 | `HANFU_P`/`HANFU_L` + kerja | merah_muda / putih / giok / emas | L1 · P2 | lembut, senyum | K `GARDEN` | keranjang (18) | tangan_depan · anggun |

**B. Ibu kota, gilda, kampung, klan, kompleks, gua (peran 38.N.2).**

| Peran | K | U | Gaya | Kain | Rambut L · P | Wajah | Topi (38MD) | Prop | Sikap · Jalan |
|---|---|---|---|---|---|---|---|---|---|
| `pangeran_kota` | L | D | `BANGSAWAN` + pesta | kuning naga #C9A030 / putih / lak / emas | L2 (S `mahkota_teratai`) | phoenix, tegas, kumis_tipis | `mahkota_guan_giok` (38MD) | — | tangan_belakang · bangsawan |
| `menteri` | C20 | D 60, T 40 | `PEJABAT` (buzi bangau) | lak / gading / emas / emas | L1 · P1 | tegas, jenggot_kambing 50 % L | `topi_pejabat` | papan_catatan (67) | tangan_lengan · bangsawan |
| `pengawal_istana` | C20 | M/D | `PENJAGA` + zirah_tugas | lak / hitam / emas / besi | L1 · P4 | tajam | `helm_berumbai` | tombak | tombak · militer |
| `kapten_penjaga` | C20 | D | `PENJAGA` + zirah_tugas + `jubah_luar` | lak / hitam / emas / emas | L1 · P4 | tajam, bekas_luka 60 % | `helm_kapten` | — (dao NpcGear) | berkacak · militer |
| `penjaga_gerbang` | C15 | M/D | `PENJAGA` | nila / kasar / lak / besi | L1 · P4 | tajam | `helm_berumbai`, `perisai_bundar` | tombak | tombak · militer |
| `hakim` | C15 | D/T | `PEJABAT` (buzi harimau) | hitam / lak / emas / emas | L1 · P1 | tegas, jenggot_penuh L | `topi_pejabat` | palu_kayu (22) | tangan_belakang · bangsawan |
| `bankir` | C35 | D | `SAUDAGAR` | lak / emas / emas / emas | L1 · P1 | almond, tahi_lalat | `topi_bulat_pedagang` | sempoa | tangan_lengan · pedagang |
| `bandar_batu` | C25 | D | `PENDEK` + kerja (celemek) | cokelat / kasar / emas / perunggu | L7 · P4 | tajam | `celemek_kulit` | pahat (39) | berkacak · gagah |
| `pemotong_batu` | C20 | M/D | `PENDEK` + kerja | kasar / gading / cokelat / besi | L7 · P4 | coreng_jelaga | `kacamata_batu`, `kain_kepala` | pahat | santai · santai |
| `pedagang_binatang` | C30 | D | `SAUDAGAR` + `kerah_bulu` | cokelat / pasir / kayu / perunggu | L4 · P5 | tegas, bekas_luka 30 % | `topi_bulu` | tali_kekang (68) | berkacak · gagah |
| `pedagang_senjata` | C30 | D | `TEMPUR` | hitam / lak / emas / besi | L3 · P4 | tajam | (pedang di punggung 38MD) | — | lipat_tangan · gagah |
| `empu` | C10 | D/T | `PENDEK` + kerja | kasar / cokelat / lak / besi | L7 · P4 | tajam, coreng_jelaga, jenggot_penuh 50 % | `celemek_kulit`, `sarung_tangan_empu` | palu | berkacak · gagah |
| `alkemis` | C45 | D/T | `TAO` + kerja (lengan diikat) | ungu_tua / gading / emas / perunggu | L1 · P3 | almond, coreng_jelaga 30 % | `topi_alkemis` | labu_pil | tangan_lengan · santai |
| `ketua_gilda` | C30 | D/T | `BANGSAWAN` | warna gilda / gading / emas / emas | L2 (guan emas) · P1 | tegas | `lencana_bintang` | — | tangan_belakang · bangsawan |
| `petugas_gilda` | C50 | M/D | `SARJANA` | sutra_biru / gading / tinta / perunggu | L1 · P3 | almond | `lencana_bintang` | kuas (10) | tangan_depan · pedagang |
| `penguji` | C30 | D | `TEMPUR` | nila / hitam / perak / besi | L3 · P4 | tajam, bekas_luka 40 % | ikat kepala (S `ikat_dahi_ekor`) | — | lipat_tangan · militer |
| `penjaga_formasi` | C40 | D | `TAO` (trigram tepi) | nila / gading / perak / perak | L1 · P3 | tegas | `jubah_trigram` → **busana 40** (40.7.4) | piringan_formasi (69) | tangan_lengan · santai |
| `nakhoda` | C20 | D | `PENDEK` + `jubah_luar` | sutra_biru / gading / emas / perunggu | L4 · P4 | bekas_luka 30 % | `topi_nakhoda` | teropong (37) | berkacak · gagah |
| `kuli` | C10 | M/D | `PETANI` | kasar / kasar / — / — | L7 · P5 | coreng_jelaga | `kain_kepala` | pikulan (15) | santai_l · kuli |
| `pemilik_penginapan` | C50 | D | `HANFU_L/P` + kerja (celemek) | kayu_muda / gading / lak / perunggu | L1 · P3 | lembut, senyum | `topi_bulat_pedagang` (L) | kunci_kamar (70) | tangan_depan · santai |
| `koki` | C35 | D | `PENDEK` + kerja (celemek) | putih / gading / lak / besi | L7 · P2 | bulat, senyum | `topi_koki` | sendok_sayur (6) | berkacak · santai |
| `pelayan` | C65 | M | `PELAYAN` | kayu_muda / gading / lak / — | L7 · P2 | lembut | — | nampan (30) | tangan_depan · tergesa |
| `pustakawan` | C40 | D/T | `SARJANA` | nila / gading / tinta / perunggu | L1 · P1 | tua 40 %, almond | `topi_sarjana` | kitab (11) | tangan_lengan · santai |
| `penceramah_fana` | C30 | T | `SARJANA` | putih / gading / tinta / perak | L6 · P7 | tua, jenggot_panjang L | `topi_sarjana`, `kipas_lipat_pinggang` | kipas_lipat | tangan_belakang · tua |
| `rahib` | L | M/D/T | `RAHIB` | abu / gading / kuning_tanah / kayu | L5 | lembut, tenang | `tasbih` (38MD) | dupa (20) | doa (120) · rahib |
| `tabib` | C50 | D/T | `SARJANA` | gading / hijau_teh / giok / perunggu | L1 · P3 | lembut | — | kotak_obat (71) | tangan_depan · santai |
| `tukang` | C10 | M/D | `PENDEK` + kerja | kasar / cokelat / — / besi | L7 · P5 | coreng_jelaga | `kain_kepala`, `sabuk_alat` | palu | berkacak · santai |
| `penjaga_teka` | C60 | M/D | `HANFU_L/P` + pesta | lak / gading / emas / emas | L1 · P2 | senyum | — | lentera_tangan (28) | tangan_depan · santai |
| `juara_sekte` | C40 | M/D | seragam sekte (40.3.8) / `TEMPUR` | sekte | L3 · P4 | tajam, tanda_dao bila ranah | — | — (pedang) | gagang · gagah/anggun |
| `kepala_desa` | L | T | `HANFU_L` kasar | kasar / gading / cokelat / perunggu | L6 | tua, jenggot_panjang | `caping` | tongkat (13) | tua · tua_tongkat |
| `petani` | C40 | M/D/T | `PETANI` | kasar / gading / — / — | L7, L8 · P5 | bulat/persegi, kulit lebih gelap +1 tingkat 50 % (terbakar matahari) | `caping` | cangkul (17) | santai · santai |
| `nelayan` | C25 | D/T | `PETANI` | laut / kasar / — / — | L7 · P5 | persegi | `caping` | joran (16) | santai · santai |
| `pandai_desa` | C10 | D | `PENDEK` + kerja | kasar / cokelat / — / besi | L7 · P4 | coreng_jelaga | `celemek_kulit` | palu | berkacak · gagah |
| `pedagang_keliling` | C35 | D | `PENDEK` + bepergian (tanpa jubah) | cokelat / pasir / lak / perunggu | L7 · P5 | senyum | `keranjang_punggung` | — | tangan_depan · pedagang |
| `anak` | C50 | A | `ANAK` | warna cerah acak (merah, kuning, hijau muda, biru muda) / gading / lak / — | A1, A2, L8 · A1, P2 | anak, bulat | `ikat_rambut_anak` → **rambut 40** (`cepol`/`jambul_anak`) | layang_layang 10 % / gasing 10 % | anak · anak_lompat |
| `tetua_desa` | C50 | T | `HANFU_L/P` kasar | cokelat / gading / kayu / kayu | L6 · P7 | tua | `janggut_putih_panjang`/`sanggul_nenek` → **rambut 40** | tongkat / kipas_lipat | tua · tua_tongkat |
| `patriark_klan` | L | T | `BANGSAWAN` + `jubah_luar` | warna klan / gading / emas / emas | L6 (S `mahkota_teratai`, kumis_naga) | tua, tegas | `mahkota_guan_giok` | — | tangan_belakang · bangsawan |
| `tuan_muda` | L 80, P 20 (nona muda) | M | `BANGSAWAN` | warna klan / putih / emas / emas | L2 (guan giok) · P1 (buyao) | phoenix, menyeringai 30 % | `kipas_lipat_pinggang` | kipas_lipat | angkuh (119) · bangsawan |
| `tetua_klan` | C30 | T | `HANFU_L/P` gelap + `jubah_luar` | hitam / warna klan / emas / perak | L6 · P7 | tua, tegas | — | tongkat 30 % | tangan_belakang · tua |
| `murid_klan` | C40 | M | `PENDEK` | warna klan / gading / warna klan / besi | L3 · P4 | acak | — | pedang_kayu (81) saat latihan | santai · gagah/anggun |
| `murid_sekte_luar` | C45 | M | seragam sekte pangkat Luar | sekte | L1, L3 · P2, P4 | acak | — | sapu (7) saat tugas | santai · gagah/anggun |
| `murid_sekte_dalam` | C45 | M/D | seragam sekte pangkat Dalam | sekte | L2, L3 · P3, P4 | acak, tanda_dao bila ranah | — | — | gagang/tangan_depan · gagah/anggun |
| `tetua_sekte` | C35 | D/T | seragam Tetua + `jubah_luar` | sekte | L2/L6 (guan giok) · P1/P7 | tua 60 %, tanda_dao | — | kebut 40 % | tangan_belakang · bangsawan/tua |
| `ketua_sekte` (rupa baru, dipakai 36/38DS untuk ketua sekte NPC) | C35 | D/T | seragam Tetua Agung + `jubah_luar` + pesta | sekte | L2 (S `mahkota_teratai`) · P1 (buyao emas) | tegas, tanda_dao | — | — | tangan_belakang · bangsawan |
| `pelayan_sekte` | C55 | A 20, M 80 | `PELAYAN` (warna sekte pudar) | sekte × 0.7 / gading / — / — | L7 · P2 | acak | — | sapu | tangan_depan · tergesa |
| `pertapa` | C20 | T | `RAHIB` bertambal (`COMPANG` 30 % tambalan) | abu / kasar / — / kayu | L4 putih (jenggot_panjang, alis_panjang) · P6 putih | tua, tenang | `tasbih` | — | doa · tua |
| `sisa_jiwa` | C40 | D/T | `KAFAN` atau busana zaman kuno `BANGSAWAN` pudar | putih / abu / perak / perak | L2 · P6 | pucat arwah, mata tutup_tenang | — | — | melayang (arwah) |
| `tetua_iblis` | C30 | D/T | `IBLIS` + `jubah_luar` | hitam / darah / darah / tulang | L4 merah tua · P6 merah tua | tajam, iris merah, kilat | `jubah_iblis` → **busana 40** | — | lipat_tangan · iblis |
| `tawanan` | C45 | M/D | `COMPANG` | kasar kotor / abu / — / — | L8 · P5 | sedih, luka 2 | — | rantai_tangan (58) | berlutut_rantai (104) · tergesa |
| `pengemis` | C40 | A 30, T 70 | `COMPANG` | kasar / abu / — / — | L8 · P5, P7 | tua/anak, bekas_luka 30 % | — | mangkuk (1) | tunduk (118) · tua_tongkat / anak |
| `pendeta_tao` (rupa baru: kuil Tao 38KS, penjaga gua) | C30 | D/T | `TAO` | nila / gading / gading / perak | L6/L1 (guan kayu) · P1 (tusuk kayu) | tua 50 % | — | kebut | tangan_lengan · rahib |

**C. Kota bertema (peran 38.T.3.3-38.T.7.3).** Kota tema mengubah **bobot nada kulit** (pelabuhan & laut: +1 tingkat
gelap 40 %; gurun: nada 3-6 bobot ×2; gunung: nada 0-2 bobot ×1.5; iblis: tanpa perubahan) dan bobot warna rambut
(gurun: cokelat tua ×2).

| Peran | K | U | Gaya | Kain | Rambut L · P | Topi/perlengkapan (38TV) | Prop | Sikap · Jalan |
|---|---|---|---|---|---|---|---|---|
| `syahbandar` | C20 | D | `PEJABAT` | laut / gading / emas / emas | L1 · P1 | `topi_syahbandar` | papan_catatan | tangan_belakang · bangsawan |
| `petugas_bea` | C30 | M/D | `PEJABAT` | sutra_biru / gading / lak / perunggu | L1 · P3 | `topi_pejabat` | kuas | tangan_depan · pedagang |
| `pelaut` | C15 | M/D | `LAUT` | laut / putih / — / — | L4, L8 · P5 | `rompi_bergaris`, `kalung_gigi_hiu` | tali (44) | berkacak · gagah |
| `nakhoda_laut` | C20 | D/T | `PENDEK` + `jubah_luar` | laut / gading / emas / emas | L4 (jenggot_penuh) · P4 | `mantel_nakhoda` → **busana 40** | teropong | berkacak · gagah |
| `penjaga_suar` | C30 | D/T | `PENDEK` | kasar / gading / lak / perunggu | L7 · P5 | `kain_kepala` | lentera_tangan | santai · tua |
| `tukang_kapal` | C10 | D | `PENDEK` + kerja | kasar / hitam ter / — / besi | L7 · P4 | `celemek_ter` → **busana 40** | palu_dempul (45) | berkacak · santai |
| `penjual_ikan` | C60 | D | `PETANI` + celemek | laut / gading / — / — | L7 · P5 | `kain_kepala` | ikan_tali (43) | tangan_depan · pedagang |
| `pedagang_seberang` | C25 | D | `GURUN` | pasir / putih / emas / emas | L4 (jenggot_penuh) · P5 | `serban_seberang` | sempoa | tangan_lengan · pedagang |
| `pendeta_laut` | C50 | D/T | `TAO` (biru laut) | teal / putih / perak / perak | L6 · P6 | `mahkota_laut` | kebut | doa · rahib |
| `tuan_benteng` | L | D | `BANGSAWAN` (gurun) + `jubah_luar` | pasir / lak / emas / emas | L1 (jenggot_penuh) | `serban_emas` | — | tangan_belakang · bangsawan |
| `kafilah_bashi` | C20 | D | `GURUN` | pasir / cokelat / lak / perunggu | L4 · P5 | `topi_bulu_lebar` | cambuk_kafilah (60) | berkacak · gagah |
| `penuntun_unta` | C20 | M/D | `GURUN` | pasir / gading / — / — | L7 · P5 | `kerudung_pasir` | tali (44) | santai · santai (`pimpin_unta`) |
| `pedagang_bazar` | C45 | D | `SAUDAGAR` (gurun) | lak / pasir / emas / emas | L4 · P5 | `serban_seberang` 50 % | kantong_uang | tangan_depan · pedagang |
| `pendeta_air` | C55 | D/T | `TAO` (putih-biru) | biru_langit / putih / perak / perak | L6 · P6 | — | kendi (48) | doa · rahib |
| `pelukis_gua` | C40 | M/D | `SARJANA` + kerja | gading / pasir / tinta / — | L4 · P3 | — | kuas_besar (46), palet (47) | santai · santai |
| `penari_gurun` | P | M | `HANFU_P` ringan + selendang | lak / emas / emas / emas | P6 (bunga) | `selendang_penari` → **busana 40** (`selendang_*`) | — | tangan_depan · penari |
| `pengawal_gurun` | C20 | M/D | `PENJAGA` + zirah_tugas | pasir / cokelat / lak / perunggu | L1 · P4 | `helm_runcing`, `baju_sisik` → **busana 40** (lukisan sisik) | tombak | tombak · militer |
| `nyonya_sutra` | P | D | `BANGSAWAN` (sutra) | ungu_tua / merah_muda / emas / emas | P1 | `sanggul_giok` → **rambut 40** (`sanggul_tinggi` + 2 tusuk menyilang) | kipas_sutra (62) | angkuh · anggun |
| `penenun_sutra` | C80 | M/D | `HANFU_P` + kerja | merah_muda / gading / lak / — | P3 · L7 | — | — | tangan_depan · anggun |
| `pemelihara_ulat` | C70 | M/D | `PETANI` | hijau_teh / gading / — / — | P5 · L7 | `caping` | keranjang | santai · santai |
| `ahli_teh` | C50 | D/T | `SARJANA` (hijau teh) | hijau_teh / gading / emas / perunggu | L1 · P1 | `jubah_teh` → **busana 40** (warna) | teko | tangan_lengan · santai |
| `pemetik_teh` | C75 | M/D | `PETANI` | teh / gading / — / — | P5 · L7 | `caping` | keranjang_teh (49) | santai · santai |
| `pendayung` | C30 | M/D | `PETANI` | sutra_biru / gading / — / — | L7 · P5 | `caping_biru` | dayung_yuloh (41) | santai · gagah |
| `sastrawan` | C35 | M/D | `SARJANA` (biru) | biru_langit / gading / tinta / perak | L2 · P3 | `jubah_teh` (biru) → **busana 40** | kipas_lipat | tangan_belakang · santai |
| `kepala_biara` | L | T | `KASAYA` | kuning_tanah / gading / lak / emas | L5 (jenggot_panjang putih) | — | tongkat_cincin (53) | doa · rahib |
| `biksu_gunung` | L 85, P 15 (biksuni) | M/D | `RAHIB` | abu / gading / kuning_tanah / kayu | L5 (P: L5 juga) | `tasbih` | tongkat_bambu (54) | doa · rahib |
| `penjaga_lift` | C20 | D | `PENDEK` | kasar / gading / lak / besi | L7 · P4 | `kain_kepala` | — | berkacak · santai |
| `kuli_gunung` | C10 | M/D | `PETANI` | kasar / kasar / — / — | L7 · P5 | `kain_kepala` | rangka_punggung (56) | santai · kuli |
| `pemetik_herba` | C45 | M/D | `PENDEK` | teh / kasar / — / perunggu | L7 · P5 | `tali_melingkar` | sabit_herba (61) | santai · santai |
| `peziarah` | C50 | D/T | `RAHIB` bepergian (tanpa caping) | kuning_tanah / gading / — / — | L7, L6 · P7 | `ikat_kepala_kuning` | tas_dupa (55) | doa · tua_tongkat 30 % |
| `penguasa_iblis` | L 70 | D | `IBLIS` + `jubah_luar` + `kerah_tinggi` | hitam / darah / darah / emas | L4 merah tua · P6 | `mahkota_tanduk`, `jubah_naga_hitam` → **busana 40** | — | angkuh · iblis |
| `penegak_iblis` | C25 | D | `IBLIS` + zirah_tugas | hitam / darah / besi / besi | L1 · P4 | `topeng_besi`, `rantai_bahu` | — (dao) | lipat_tangan · militer |
| `jagal_arena` | C10 | D | `PENDEK` bertelanjang lengan | darah / kasar / — / besi | L5 (gundul preman), L7 | `rantai_bahu` | — | berkacak · gagah |
| `gladiator_iblis` | C30 | M/D | `TEMPUR` | hitam / darah / besi / besi | L3 · P4 | `zirah_gladiator` → **busana 40** (`bahu_zirah` kiri + sabuk lebar) | — | berkacak · gagah |
| `penjaga_kitab_terlarang` | C40 | T | `TAO` hitam | hitam / gading / ungu_tua / perak | L6 · P7 | `penutup_mata` | kitab | tangan_lengan · tua |
| `pembunuh_bayaran` | C40 | M/D | `TEMPUR` + penyamaran | hitam / abu / — / besi | L3 · P4 | `tudung_pembunuh` | pisau_ganda (57) | lipat_tangan · iblis |
| `warga_iblis` | C50 | A 10, M 30, D 45, T 15 | `HANFU_L/P` gelap, `PETANI` | ungu_tua / abu / darah / — | acak (rambut merah tua 10 %) | — | — | santai · santai |
| `pemburu_lurus` | C35 | M/D | seragam sekte Lurus / `TEMPUR` | putih / sutra_biru / perak / perak | L3 · P4 | `jubah_pemburu_lurus` → **busana 40** (warna + lambang) | — (pedang) | gagang · gagah/anggun |

**D. Orang Dimensi (D), Kultivator Liar, Utusan, Penonton.** Renderer-nya memasang Sosok di klien (40.2.2 "Orang D,
Liar..."). Kulit sendiri yang ikonik (**Kepala Lembu, Muka Kuda, Wuchang Putih/Hitam, Hantu Lapar, Raja Naga, Yaksha**)
**tetap** memakai PNG sendiri (bukan manusia biasa); Sosok hanya menambah gerak 40.5 pada mereka.

| Rupa | K | U | Gaya | Kain | Rambut | Topi (Headwear D) | Sikap · Jalan |
|---|---|---|---|---|---|---|---|
| `d.meng_po` | P | T | `HANFU_P` kasar + kerja | cokelat / gading / — / — | P7 | `MENG_PO` | tangan_depan · tua_tongkat |
| `d.hakim` (1-10) | L | D/T | `HAKIM_BAWAH` | darah / hitam / emas / emas | L1 (jenggot_penuh/panjang menurut nomor) | `JUDGE` | tangan_belakang · bangsawan |
| `d.raja_yama` | L | D | `HAKIM_BAWAH` + `jubah_luar` + pesta | hitam / darah / emas / emas | L1 (jenggot_penuh, alis tebal) | `YAMA` | angkuh · bangsawan |
| `d.arwah` | C50 | A/M/D/T dari look | `KAFAN` | putih / abu / — / — | acak, warna arwah | `GHOST_HAIR`/`GHOST_EYES` **dimatikan** (rambut Sosok arwah) | — · arwah (melayang) |
| `d.penjaga_laut` (Istana Laut) | C25 | M/D | `PENJAGA` + zirah_tugas | teal / laut / mutiara #E8E8F0 / perak | L1 · P4 | `GUARD` | tombak · militer |
| `d.pertapa_laut` | L | T | `TAO` | laut / gading / perak / perak | L6 (jenggot_panjang) | `HERMIT` | tangan_lengan · tua |
| `d.dewa_penglai` | C40 | D | `ABADI` | putih / biru_langit / emas / emas | L2 · P6 | `PENGLAI` | tangan_lengan · bangsawan |
| `d.nelayan` | C20 | D/T | `PETANI` | laut / kasar | L7 · P5 | `FISHER` | santai · santai |
| `d.pedagang` | C40 | D | `SAUDAGAR` | teal / pasir / emas / perunggu | L1 · P3 | `MERCHANT` | tangan_lengan · pedagang |
| `liar.<0-7>` (8 sekte iblis) | C30 | M/D | `IBLIS` (tingkat rendah: `TEMPUR` gelap) | hitam / warna unsur sekte digelapkan 0.5 / darah / besi | L3, L4 · P4, P6 | `RogueMaskLayer` lama tetap | lipat_tangan · iblis |
| `liar.bayangan` / `liar.energi` | ikut sumber | — | ikut sumber | — | — | tembus pandang lama | — |
| `utusan_langit` | L | D | `ABADI` + zirah_tugas | emas_terang / putih / emas / emas | L2 (guan emas) | — (lingkaran cahaya PurbaFx) | militer · militer |
| `penonton.utusan` (36) | C40 | M/D | seragam sekte tamu | warna bidang/tepi lambang (lama) | acak | kepala lama `PenontonModels` | duduk/sorak (lama) |
| `penonton.warga` (36) | C50 | A 15, D 60, T 25 | `PETANI`/`HANFU` kasar | homespun lama | acak | lama | lama |

#### 40.4.2 Murid menurut pangkat sekte dan peran

Murid tanpa seragam rancangan memakai baris ini; dengan seragam rancangan, warna dan potongan dari `JubahLook`
(40.3.8) **menimpa** kolom Gaya/Kain, sedangkan hiasan, lencana dan tambahan tetap dari baris ini.

| Pangkat / peran | Gaya bawaan (L / P) | Kain bawaan | Hiasan rambut | Lencana (`lencana_giok`) | Tambahan | Sikap bawaan |
|---|---|---|---|---|---|---|
| 0 Tercatat | `PENDEK` / `PENDEK` | kasar / gading / abu | kain | kayu (#7A5434), tanpa rumbai | celana ikat, sandal jerami | tunduk (118) |
| 1 Luar | `PENDEK` / `HANFU_P` pendek (rok_pendek) | Dao/ranah (40.3.8) / gading / putih | kain warna `TEPI` | perunggu, rumbai putih | `sabuk` | santai |
| 2 Dalam | `HANFU_L` / `HANFU_P` | Dao / gading / biru | guan kayu · tusuk perak | perak, rumbai biru | `sabuk`, `liontin_giok` | santai / tangan_depan |
| 3 Inti | `HANFU_L` / `HANFU_P` + selendang tipis | Dao / gading / giok | guan perak · buyao perak | giok, rumbai giok | lambang punggung, `liontin_giok` | tangan_belakang (L) / tangan_depan (P) |
| 4 Tetua | `HANFU_L`/`TAO` (bila Dao Ruang/Bayangan/formasi) + `jubah_luar` | Dao gelap / gading / ungu | guan giok · buyao emas | giok ungu | `jubah_luar`, `kerah_tinggi` (P: selendang) | tangan_belakang |
| 5 Tetua Agung | `BANGSAWAN` + `jubah_luar` | Dao / putih / putih-emas | `mahkota_teratai` · buyao emas + bunga | emas, rumbai emas | pesta tetap, selendang pibo (P) | tangan_belakang (angkuh bila SOMBONG) |
| Calon | `PETANI`/`ANAK` (usia) | kasar | kain | — | buntalan di punggung | tunduk |
| Saingan (rival) | `TEMPUR` + `jubah_luar` | hitam / warna Dao / emas | ikat_dahi_ekor / buyao | — | — | lipat_tangan |
| Bayangan (phantom) | ikut sumber | ikut | ikut | ikut | tembus pandang lama + mata `tutup_tenang` | — |
| Klon Ilahi | **sama persis** sumber | sama | sama | sama | putih memudar lama | — |

Tambahan otomatis murid: `luka 1` (pincang, HP < 35 % atau setelah gagal tembus) → klip jalan `pincang` dan perban kaki;
`luka 2` → perban lengan dilukis + lengan kiri digendong (klip lapisan atas); `luka 3` (habis qi, `DEVIATING` atau
kesengsaraan) → wajah `sakit`, bahu turun; `busanaKhusus 1` (bepergian) saat misi luar (38/39) atau perintah IKUT jarak
jauh; `2` (berkabung) 3 hari dunia setelah sesama murid gugur (`ACT_MOURN`); `5` (tidur) saat klip tidur.

#### 40.4.3 Murid menurut jalan hidup (DESIGN 39.3.1; kunci `jalan.<id>`, bit `jalan` di `Gaya`)

Id dan bit **persis** sepuluh jalan 39 (bit 1-10); bit 11-15 adalah rupa jalan tambahan yang boleh dipakai modul lain
(38 untuk NPC, atau 39 kelak). 39 memanggil `SosokApi.setJalan(murid, id)` saat jalan dipilih, diganti, ditimpa `iblis`
atau ditebus (C40.I3). Rupa jalan **ditambahkan** di atas baris pangkat; "ciri terlihat" kolom 39.3.1 diwujudkan di sini.

| Bit | Id (39) | Tanda rupa | Kubus / prop pakai | Sikap diam · klip khas |
|---|---|---|---|---|
| 1 | `pedang` | rambut L3/P4 + `ikat_dahi_ekor`, mata `tajam`, `bekas_luka` 30 %; jubah `TEMPUR` bila tanpa seragam | `pelindung_lengan` ×2; pedang di pinggang kiri (prop 81 di `PINGGANG_KIRI` bila tidak ada pedang natal) | gagang (112) · `latihan_pedang_1..3`, `tebas_udara`, `asah_pedang`, ACT 40 |
| 2 | `alkemis` | `coreng_jelaga`, lengan diikat (tambahan `kerja`) | `labu`, `kantong` (herba); botol pil (76) di `PINGGANG_KANAN` | tangan_lengan (110) · `kipas_tungku`, `racik_herba`, `gulung_pil`, ACT 41 |
| 3 | `formasi` (formasi & jimat) | `tinta_jari`, tepi `TEPI` bermotif trigram, kuas di sanggul (prop 10 di `KEPALA`) | `kantong` kertas jimat; piringan formasi (69) di `PINGGANG_KANAN` | tangan_lengan · `tulis_jimat`, `tempel_jimat`, `pasang_bendera`, ACT 42 |
| 4 | `pertapa` | rambut terurai L4/P6, busana bertambal 1-2 petak, mata `tutup_tenang` saat diam lama | tasbih tangan (87) | doa (120) · `meditasi_napas`, `sirkulasi_qi`, `tiang_berdiri`, ACT 45/53/56 |
| 5 | `petualang` | tambahan `bepergian` (tanpa caping di sekte), kulit +1 tingkat gelap, `bekas_luka` 20 % | `buntalan`, `labu`, `jubah_luar` | berkacak (117) · `minum_arak`, ACT 46/47/52 |
| 6 | `pemburu` | `kerah_bulu`, `bekas_luka` cakar (3 garis) | trofi taring (88) di `BAHU_KIRI`, `kantong` | berkacak · `beri_makan_binatang` (binatang roh guru), ACT 40 |
| 7 | `pedagang` | gaya `SAUDAGAR` saat di kota (JELMAAN) | `kantong`, sempoa (12) di `PINGGANG_KANAN`, kotak di punggung (56 `rangka_punggung`) saat ACT 47 | tangan_lengan · `hitung_sempoa`, `tawar` (30), ACT 49/50 |
| 8 | `cendekia` | gaya `SARJANA` di dalam paviliun, `huadian` tidak | gulungan (72) di `PINGGANG_KIRI` | tangan_belakang (108) · `membaca`, `menulis`, `mengajar`, `angguk_dengar`, ACT 44 |
| 9 | `penjaga` | tambahan `zirah_tugas` | `bahu_zirah` ×2, `rok_zirah`; tombak (35) saat jaga | tombak (111) / gagang · `berdiri_jaga` (15), ACT 55 |
| 10 | `iblis` (lapisan) | rambut menggelap ke merah tua bertahap (`godaan` 85 → 100 % di ranah Inti Emas+), iris merah, tepi `darah`; busana `IBLIS` di Inti Emas+; ditebus → kembali ke jalan asal dalam 1 hari dunia (atlas baru) | `rumbai_iblis` | lipat_tangan (109) · `semedi_darah` (105), menghindari tatap guru (`Tatap` membalik prioritas guru) |
| 11 | `tempa` (tambahan) | lengan kanan telanjang, `coreng_jelaga` | `celemek` slot `SAMAK`, `pelindung_lengan` | berkacak · `palu_landasan` |
| 12 | `tabib` (tambahan) | mata `lembut`, kain gading | kotak obat (71) | tangan_depan (121) · `periksa_nadi` |
| 13 | `petani_roh` (tambahan) | kulit +1 tingkat gelap, lengan digulung | `caping_bambu` di kebun | santai · `mencangkul`, `memanen` |
| 14 | `musisi` (tambahan) | `bunga_rambut` (P), alis `daun_willow` | seruling (79) / guqin (80) | tangan_lengan · `main_seruling`, `main_guqin` |
| 15 | `bangsawan` (tambahan) | gaya `BANGSAWAN`, `huadian` (P) | `liontin_giok` emas | angkuh (119) · `kipas` (52) |

**Tubuh `JELMAAN` (39.6.6):** 39C menyalin `sosok` dan `gaya` murid asli ke tubuh tampilan dengan `SosokApi.setSosok` +
`setBusanaKhusus(jelmaan, 1)` (bepergian) — rupa identik dengan murid di rumah, ditambah caping dan buntalan. Atlas
memakai kunci cache yang sama bila `busanaKhusus` sama (tidak ada atlas ganda).

#### 40.4.4 Contoh nyata (pemeriksa membandingkan dengan pratinjau)

- **Murid perempuan, Luar, Dao Es, sifat PENYAYANG+RAJIN, usia MUDA, benih 0x2A51C3:** kulit gading, wajah hati, mata
  bulat dengan bulu mata, alis daun willow, mulut penuh; rambut P2 sanggul kembar dengan pita putih; `HANFU_P` pendek
  biru es #5E8EA0 dengan tepi putih, rok pendek, sabuk; lencana perunggu; diam `tangan_depan` dengan senyum, jalan anggun.
- **Murid laki-laki, Tetua, Dao Api, Jiwa Baru Lahir, SOMBONG, DEWASA:** kulit gandum, wajah persegi, mata phoenix iris
  ambar, alis pedang, kumis tipis; rambut L2 dengan `guan_giok` dan rambut tergerai; `HANFU_L` merah tua #8E2E1A,
  `jubah_luar`, kerah tinggi, sulam lidah api di ujung rok, lambang sekte di punggung, `tanda_dao` api di dahi; diam
  `angkuh` (dagu naik), jalan bangsawan.
- **Penjaga kota perempuan:** P4 ekor kuda di bawah helm `GUARD` (ekor kuda tetap terlihat di belakang helm: ekor kuda
  **tidak** termasuk `TOPI_MENUTUP`), `PENJAGA` nila, tombak, sikap tombak, jalan militer versi perempuan (40.5.6).
- **Anak desa laki-laki:** skala 0.66, kepala 1.22, A1 dua kuncir, baju anak merah, berlari `jalan_anak` melompat.
- **Nenek tetua desa:** P7 sanggul rendah abu, mata tua berkerut, `HANFU_P` cokelat, tongkat, punggung bungkuk 18°,
  jalan `tua_tongkat` dengan ketukan tongkat sinkron langkah.

#### 40.4.5 Perlengkapan lama yang memuat rambut (aturan sembunyi; data `SosokGear` milik 40K, dipakai `KotaGearLayer` 40R)

Bila sosok aktif, `KotaGearLayer` menyembunyikan bagian bernama di bawah (rambut/janggut kini dari Sosok) dan Sosok
menggambar padanannya dengan warna yang sama (`guan` emas pemimpin, giok pengembara):

| `KotaGear` jenis | Bagian K yang disembunyikan | Padanan Sosok |
|---|---|---|
| `TEA` | `bun`, `pin`, `pin_bead`, `hairband` | P3/L7 + `tusuk_guan` kayu |
| `FOLK_BUN` | `bun`, `pin` | P1/P3 + tusuk giok |
| `FOLK_KNOT` | `knot`, `wrap` | L7 + kain putih |
| `WANDERER_GUAN` | `knot`, `guan`, `pin`, `ribbon_l`, `ribbon_r` | L2 + `guan_tinggi` giok + `pita_rambut` ×2 |
| `RIVAL`, `RIVAL_LEADER` | `knot`, `guan`, `gem` (`band`, `tail_l/r` **tetap**: ikat kepala kelompok) | L3/L2 + `guan_tinggi` emas (pemimpin) |
| `IMMORTAL` | `guan`, `pin`, `hair`, `shawl`, `ribbon` | L2/P6 + `guan_giok` + `selendang_*` |
| `SAGE` | `beard`, `brows` | `jenggot_panjang`, `alis_panjang` |
| semua lain | — | — |

`Headwear` D: `GHOST_HAIR` dan `GHOST_EYES` tidak digambar bila Sosok aktif (rupa `d.arwah`). Topi yang menutup kepala
(`TOPI_MENUTUP`): K `FUTOU`, `FUTOU_GOLD`, `BROKER`, `MERCHANT`, `MERCHANT_GOLD`, `GUARD`, `FOLK_HAT`, `FOLK_SCARF`,
`WANDERER_DOULI`, `SAGE`, `ENVOY`; D `JUDGE`, `YAMA`, `GUARD`, `MERCHANT`, `FISHER`, `PENGLAI`, `HERMIT`; 38 (ditambah
38MD ke tabel yang sama lewat C40.K2): `topi_pejabat`, `helm_*`, `topi_*`, `caping*`, `serban_*`, `kerudung_pasir`,
`mahkota_tanduk`, `tudung_pembunuh`, `topi_bulu*`, `kain_kepala`.

---------------------------------------------------------------------------------------------------------------------

### 40.5 Sistem animasi Gerak (modul 40G)

#### 40.5.1 Arsitektur dan urutan per frame

```
extractRenderState (renderer 40R, sekali per frame per entitas)
  └─ SosokRender.ekstrak(entity, state)          // sosok, gaya, atlas id, LOD
  └─ PoseRenderData.extract(entity, state, pt)    // cast 27 + duel 35 (tidak berubah)
  └─ GerakPengendali.evaluasi(entity, state, age) // 40G
        ├─ bila POSE kosong: susun Snapshot dari lapisan 4-9 → data POSE  (dibaca PoseApplier, seperti NpcPoses kini)
        ├─ GerakData (kanal tambahan, profil jalan, pegas, wajah, tatap, prop) → data GERAK
        └─ ubah state.walkAnimationPos/Speed menurut profil jalan (sebelum model membaca)

submit → model.setupAnim(state) untuk SETIAP PlayerModel entitas (tubuh, busana, jubah lama, perlengkapan K/D, ...)
  HumanoidModel.setupAnim (vanilla: jalan, ayun lengan, kepala)
  PlayerModelMixin TAIL: MeditationPose.apply → PoseApplier.apply (Snapshot) → GerakApplier.apply (kanal tambahan) [40G]
  DutyPoses.Model / SosokBusanaModel / RobeModel: DutyPoses.apply (tugas 1-4 → klip data; Sekte 13-24 & Langit 25-27 fungsi)
  SosokBusanaModel.setupAnim akhir: pegas + panel rok + sembunyi/tampil bagian [40K]
setupRotations (renderer): MeditationPose.lower → PoseApplier.lift → GerakApplier.angkat (BOB, SPIN) → super → PoseApplier.tilt
```

Satu sumber gerak untuk semua `PlayerModel` entitas (kulit, busana, perlengkapan) → tidak ada bagian yang tertinggal.
`GerakApplier` dan `GerakPengendali` tidak melakukan apa-apa bila render state tidak membawa data `GERAK` (pemain, Bayangan
Cermin, Iblis Hati): **pemain tidak pernah disentuh**.

#### 40.5.2 Kanal

18 kanal `PoseLibrary` dipakai apa adanya (arti tanda menurut javadoc `PoseLibrary`). Gerak menambah 6 kanal yang
**tidak** lewat Snapshot (diterapkan `GerakApplier` setelah `PoseApplier`):

| Indeks | Nama | Satuan | Diterapkan pada |
|---|---|---|---|
| 18 | `HZ` | derajat | `head.zRot +=` (memiringkan kepala) |
| 19 | `BZ` | derajat | `body.zRot +=`; pinggul: kaki `x` digeser ±0.4 px lawan arah; lengan ikut pivot bahu (rumus `PoseApplier.bendBody` dengan sumbu z) |
| 20 | `BOB` | piksel model | `poseStack.translate(0, −BOB/16 × skala, 0)` di `setupRotations` (naik-turun seluruh tubuh tanpa melepas kaki dari tanah >0.5 px) |
| 21 | `SPIN` | derajat/tick | yaw render tambahan (tari putar 87); dijumlah per entitas, di-reset saat klip berakhir |
| 22 | `BAHU` | piksel | `rightArm.y/leftArm.y −=` (angkat bahu: takut, menangis, tertawa) |
| 23 | `NAPAS` | pengali | amplitudo lapisan napas (0 = tahan napas, 2.5 = terengah) |

Konversi: tabel klip ditulis dalam **derajat** (seperti 38.N.5) dan dikonversi ke radian saat `GerakKatalog` dibangun
(`Mth.DEG_TO_RAD`); `LIFT` dalam blok, `BOB`/`BAHU` dalam piksel.

#### 40.5.3 Format klip (`client/sosok/gerak/Klip`)

```java
record Klip(int id, String nama, int durasi, Mode mode, long mask, Kunci[] kunci, Osilator[] osc, int mods, float modAmp,
            Lapisan lapisan, Sumber sumber, int propKanan, int propKiri, int propPunggung, PropPasang[] pasang,
            Peristiwa[] peristiwa, int wajahMata, int wajahMulut, Varian[] varian, int bendera)
enum Mode { LOOP, SEKALI, SEKALI_TAHAN }          // SEKALI kembali ke lapisan bawah, SEKALI_TAHAN menahan kunci terakhir
enum Lapisan { PENUH, ATAS, KEPALA, JALAN }        // ATAS: lengan+kepala+badan atas; KEPALA: HX/HY/HZ; JALAN: profil jalan
enum Sumber { DATA, FUNGSI, CAST, DUEL }
record Kunci(int t, float[] nilai /* 24, NaN = tidak ditulis */, Easing masuk)
record Osilator(int kanal, float amp, float periode, float fase, Bentuk bentuk)  // ditambahkan setelah interpolasi kunci
enum Bentuk { SIN, COS, SIN_POSITIF /* max(0, sin) */, SIN_KUBIK /* (0.5+0.5 sin)^3 */, SEGITIGA }
record Peristiwa(int t, Jenis jenis, String id, float a, float b) // SUARA (id, volume, pitch), PARTIKEL (id, jumlah, warna Dao=−1), WAJAH (mata, mulut), PROP (slot, prop), KAIT (id kait terdaftar modul lain: asap kuali, busur pedang, batu roh mengorbit)
record Varian(int syarat, Kunci[] kunci, Osilator[] osc)          // syarat: P, L, ANAK, TUA, BELA_DIRI, sifat <n>
record PropPasang(int prop, Titik titik, float dx, float dy, float dz, float rx, float ry, float rz, float skala)
```

- **Interpolasi:** antara dua kunci berurutan per kanal yang ditulis keduanya; kanal yang hanya ditulis di sebagian kunci
  diinterpolasi antar kunci yang menulisnya. LOOP: setelah kunci terakhir kembali ke kunci pertama dalam
  `durasi − t_akhir` tick dengan easing kunci pertama.
- **Easing (`Easing`):** `LINEAR`; `SINE_IO` = `−(cos(πx) − 1) / 2` (bawaan); `CUBIC_OUT` = `1 − (1 − x)^3`; `CUBIC_IN` =
  `x^3`; `BACK_OUT(1.08)` = `1 + 2.08 (x − 1)^3 + 1.08 (x − 1)^2` (pukulan, hentak); `ELASTIS` = `2^(−10x) sin((10x −
  0.75) 2π/3) + 1` (pita, tepuk); `TANGGA` (tanpa interpolasi: kedip, ketuk).
- **Mods** (bit): `NAPAS_X` (dada naik-turun lewat HX ±1°), `GEMETAR` (±modAmp° acak frekuensi tinggi, benih id), `DETAK`
  (pulsa periode 8), `GELENG` (HY ±12° periode 10), `ANGKAT_TUMBUH` (LIFT bertambah sampai modAmp), `GUNCANG_MUATAN`
  (GEMETAR bertambah sepanjang klip). Nilai sama dengan `PoseLibrary.MOD_*` bila ada padanannya.
- **Fase:** klip LOOP digeser `(id entitas × 7) mod durasi` tick kecuali bendera `SINKRON` (pakai `DATA_GERAK_T0` server:
  tanding berpasangan, pikul tandu, dayung).
- **Kaki saat berjalan:** kanal kaki (`RLX..LLY`) tidak diterapkan bila `walkAnimationSpeed > 0.1` kecuali bendera
  `KAKI_PAKSA` (sama dengan aturan `PoseApplier`).
- **Bendera** lain: `DUDUK` (memakai duduk meditasi/kursi; kanal kaki dari `MeditationPose` atau klip 10/11), `BERBARING`
  (klip 13: renderer memutar tubuh 90°), `PROP_LAMA` (prop digambar `DiscipleHeldItemLayer`, bukan `SosokPropLayer`),
  `MATA_TUTUP`, `BICARA` (mulut bergerak), `SINKRON`, `KAKI_PAKSA`, `TANPA_NAPAS`.

#### 40.5.4 Lapisan, prioritas dan pencampuran

| Prio | Lapisan | Sumber | Masuk / keluar (tick) | Catatan |
|---|---|---|---|---|
| 1 | Duel 35 | `DuelPoses` (data `POSE`) | milik 35 | Gerak lapisan 4-8 memudar ke 0 dalam 4 tick; tatap dan napas mati; wajah `marah`/`kilat` |
| 2 | Cast 27 | `CastPoses` (data `POSE`) | milik 27 | sama dengan duel; wajah menurut pose (ROAR → mulut `bicara_A` lebar) |
| 3 | Duduk meditasi | `MeditationPose` | 8 | klip ber-`DUDUK` boleh menimpa lengan dan kepala di atasnya (122, 123, 140, 143) |
| 4 | Klip sekali server | `DATA_GERAK` mode SEKALI | 3 / 4 | hormat, kaget, terobosan, tepuk bahu |
| 5 | Pose aktivitas | `DutyPoses` (klip data 5/23/44/51, peta `AktivitasKlip` 40-56, fungsi 13-27) | 6 / 6 | gerbang lama `DutyPoses` tetap (diam & tenang) |
| 6 | Klip loop server | `DATA_GERAK` LOOP (jadwal 38, kegiatan 39) | 6 / 6 | — |
| 7 | Reaksi klien | `GerakReaksi` (petir: kaget 18; pemain baru tiba: lirik; ledakan dekat: kaget) | 3 / 4 | tidak bila lapisan 4-6 aktif |
| 8 | Sikap diam | tabel peran/pangkat/jalan/sifat | 10 / 10 | hanya saat `walkAnimationSpeed ≤ 0.15`; sikap jalan (lengan saja) untuk sikap bertanda `JALAN_OK` (108, 110, 115, 120) |
| 9 | Gaya jalan | profil 40.5.6 | kontinu | mengubah vanilla, bukan menimpa |
| + | Aditif | napas, gestur iseng, tatap kepala/mata, wajah, bahu perempuan, skala kepala anak, pegas | — | dimatikan per LOD 40.6.1 |

Algoritme `GerakPengendali.evaluasi` (per entitas, per frame):

```
target[24] = NaN, bobot[24] = 0
for lapisan in 9 → 4:                         // bawah ke atas
    klip = lapisan.klipSekarang(entitas)       // null bila tidak ada
    nilai = Sampler.sampel(klip, tKlip, varian)   // NaN untuk kanal di luar mask
    w = lapisan.bobot(now)                     // kurva masuk/keluar easing SINE_IO
    for c: if !NaN(nilai[c]): target[c] = NaN(target[c]) ? nilai[c] : lerp(w, target[c], nilai[c]); bobot[c] = max(bobot[c], w)
blend.lerp per kanal dengan a = 1 − 0.72^dt (pola NpcPoses.Blend; hindari lompatan saat sumber berganti)
Snapshot(Pose.NONE, val[0..17], w[0..17], null, null, 1, twoHanded=klip.bendera DUA_TANGAN) → POSE bila POSE kosong
GerakData.extra = val[18..23] + aditif
```

Biaya: ≤ 3 klip disampel per entitas (sekali, loop, sikap) + aditif; tanpa alokasi per frame (array per entitas dipakai
ulang, `Int2ObjectOpenHashMap<GerakKeadaan>`; dibersihkan tiap 200 tick seperti `DiscipleLook.cleanup`).

#### 40.5.5 Sumber klip (memakai ulang, tidak menyalin)

- `DATA`: kunci dari `GerakKatalog` (40.5.10-40.5.12).
- `FUNGSI`: pose imperatif terdaftar — `SektePoses` 13-24 (DESIGN 36) dan `LangitPoses` 25-27 (37). Gerak **tidak**
  memanggilnya sendiri (tetap lewat `DutyPoses.apply` + `LangitDutyPosesMixin`), hanya mencatat id katalognya (150-164,
  40.5.12) agar 38/39 bisa meminta "belajar", "tambang" dsb. dengan satu id. Saat murid memainkan aktivitas 13-27, Gerak
  mengosongkan lapisan 6 (tidak bertabrakan).
- `CAST`: `CastPoses.sample(Pose, t01, ageTicks)` (publik) untuk bentuk telapak, segel, seruling, doa, angkat pusaka;
  klip menyebut `Pose` dan durasi. Tidak memanggil `CastPoses.start` (itu jalur cast sungguhan berprioritas 2).
- `DUEL`: urutan `DuelPose` disampel lewat `DuelPoseLibrary.sample(...)` + `modelChannels(...)` (latihan pedang 60-62,
  128, tanding 66/67) dengan `DuelPoseTable.Ctx.NONE`; tidak memakai `DuelPoses` (itu duel sungguhan).

#### 40.5.6 Gaya jalan (`ProfilJalan`) dan varian kelamin/usia

Diterapkan dengan mengubah `state.walkAnimationPos` (irama) dan `walkAnimationSpeed` (amplitudo) **sebelum** model
membaca, ditambah kanal tambahan per langkah (fase langkah `φ = walkAnimationPos × 0.6662`, rumus vanilla).

| Profil | Irama ×pos | Ayun lengan × | Ayun kaki × | `BZ` pinggul (°, × sin φ) | `HY` kontra (°) | `BOB` (px, × \|sin φ\|) | `LEAN` (°) | Khusus |
|---|---|---|---|---|---|---|---|---|
| `gagah` (L) | 1.00 | 1.00 | 1.00 | 1.5 | 0 | 0.25 | 2 | dada sedikit maju (`HX −2`) |
| `anggun` (P) | 1.12 | 0.55 | 0.80 | 3.5 | −1.5 | 0.15 | 0 | tangan agak ke depan (`RAY −8`, `LAY 8` saat ayun), kaki jatuh sedikit ke tengah (`RLY 4`, `LLY −4`) |
| `santai` | 0.92 | 0.85 | 0.90 | 2 | 0 | 0.2 | 3 | — |
| `tergesa` | 1.25 | 1.30 | 1.10 | 1 | 0 | 0.35 | 8 | (= klip 38 #2) |
| `militer` | 1.00 | 0.70 (lengan kaku) | 1.05 | 0 | 0 | 0.15 | 0 | kepala tegak `HX −3`; perempuan: irama 1.05 |
| `bangsawan` | 0.85 | 0.25 (tangan di lengan baju/punggung: sikap 108/110 bertanda `JALAN_OK`) | 0.80 | 1 | 0 | 0.1 | −2 (dagu naik) | — |
| `pedagang` | 1.15 | 0.70 | 0.85 | 1.5 | 0 | 0.25 | 6 | — |
| `rahib` | 0.85 | 0.20 (tangan doa 120 bertanda `JALAN_OK`) | 0.75 | 0.5 | 0 | 0.1 | 4 | kepala tunduk `HX 8` |
| `kuli` | 0.90 | 0.60 | 1.00 | 1 | 0 | 0.45 (berat) | 10 | muatan: klip 4/5 |
| `tua_tongkat` | 0.75 | 0.40 | 0.60 | 1 | 0 | 0.1 | 18 (bungkuk) | tongkat di tangan kanan: `RAX = −35 + 12 sin(φ)`; **ketukan** tongkat saat sin φ lewat 0 naik (peristiwa suara `block.bamboo.step` 0.15, pitch 1.4) |
| `tua` (tanpa tongkat) | 0.80 | 0.45 | 0.65 | 1 | 0 | 0.1 | 15 | tangan di punggung (108) |
| `anak_lompat` | 1.35 | 1.25 | 1.15 | 2 | 0 | 0.6 | 4 | setiap langkah kedua: `LIFT +0.06` (lompat kecil, *skipping*); 20 % anak memakai varian `lari_riang` (klip 68 saat jarak > 4 blok) |
| `penari` | 1.20 | 0.40 (lengan diangkat lembut `RAX −30`, `RAZ 10`) | 0.85 | 4 | −2 | 0.2 | 0 | ujung kaki (`RLX` dipotong pada sisi belakang) |
| `iblis` | 0.95 | 0.50 | 0.95 | 1 | 0 | 0.15 | 10 | kepala condong maju `HX 6`, langkah mengintai |
| `arwah` | 0.60 | 0.15 | 0.10 (hampir tidak melangkah) | 2 (sin lambat) | 0 | `LIFT 0.25` tetap + sin 0.04 periode 60 | 0 | ujung busana `rok_*` bergoyang pegas tanpa kaki |

Pemilihan profil: kolom "Jalan" tabel 40.4; bila tidak ada: `gagah`/`anggun` menurut kelamin, `anak_lompat` (ANAK),
`tua` (TUA), lalu ditimpa: luka 1 → `pincang` (klip 146 lapisan JALAN), muatan berat → `kuli`, berlari
(`walkAnimationSpeed > 0.9`) → profil lari: irama ×1, lengan ×1.2, `LEAN 12`, `BOB 0.5`, perempuan lengan ×0.9.

**Varian kelamin/usia pada klip** (field `varian`), aturan umum yang wajib ditulis untuk setiap klip sapaan dan sikap:
- Hormat 32/33 untuk perempuan non-bela-diri (gaya busana bukan `TEMPUR`/`PENJAGA`, jalan bukan `pedang`/`penjaga`):
  **wanfu** (万福): t0-6 tangan menyatu di pinggang kanan `RAX −28, RAY −42, LAX −32, LAY 18`; t6-18 lutut menekuk `RLX
  12, LLX −8, LIFT −0.08`, `HX 15`, `LEAN 8`, `BZ −3`; t30 kembali. Laki-laki dan perempuan bela diri: baoquan.
- Sikap diam perempuan memakai 114/121 bila tabel tidak menyebut lain; laki-laki 113.
- Anak: semua klip kerja diskala amplitudo 1.15 dan durasi ×0.8 (gerak lebih cepat dan "berlebihan"); sikap 116.
- Tua: durasi ×1.3, amplitudo lengan ×0.8, `LEAN +10` dasar (bungkuk), `GEMETAR 0.6°` pada tangan saat memegang prop.

#### 40.5.7 Prop dan titik pasang (`client/sosok/gerak/SosokPropLayer`, data `PropKatalog`)

**Indeks prop** (`dev.kultivator.sosok.PropId`, milik 40I, konstanta saja): 1-40 = 38.O.4, 41-62 = 38.T.8.1 (indeks dan
nama **persis** sama; model item dibuat 38MD/38TV di `kultivator:fx/tempat/<id>` dan `kultivator:fx/tempat/tema/<id>`),
63-88 = prop baru 40 (model item 40K, `kultivator:fx/sosok/<id>`, aturan model 38.O.0: ≤ 40 elemen, tekstur 32 px):

| Id | Nama | Elemen (px) | Dipakai |
|---|---|---|---|
| 63 | `kertas_jimat` | lembar 3 × 0.2 × 8 kuning #E8D070, glif merah dilukis | 134, 135 |
| 64 | `kipas_tungku` | cakram anyaman 7 × 0.4 × 7 (3 kubus diputar 0/±45) + gagang 1 × 5 × 1 | 130 |
| 65 | `kebut` | gagang 1 × 8 × 1 + ekor 3 elemen menyempit 3 × 6 × 1, 2 × 5 × 1, 1 × 4 × 1 putih | penceramah, pendeta Tao, tetua |
| 66 | `papan_titah` | gulungan kuning 2 × 8 × 2 + tutup ujung 2.4³ ungu | utusan |
| 67 | `papan_catatan` | papan hu 1 × 9 × 2.5 gading, ujung membulat (2 kubus) | menteri, syahbandar |
| 68 | `tali_kekang` | gulungan 4 × 1 × 4 + ujung 1 × 5 × 0.5 | pedagang binatang |
| 69 | `piringan_formasi` | segi delapan 6 × 0.6 × 6 (3 kubus diputar 0/±45) bertrigram + jarum 0.4 × 0.4 × 3 | 133, penjaga formasi |
| 70 | `kunci_kamar` | cincin 3 × 0.4 × 3 + 2 kunci 0.5 × 3 × 1 | penginapan |
| 71 | `kotak_obat` | 5 × 4 × 3 kayu + tali 0.5 × 6 × 0.5 | tabib |
| 72 | `gulungan` | 1.5 × 7 × 1.5 + pita | pengajar, pustakawan |
| 73 | `cawan_arak` | 2 × 1.5 × 2 + kaki 1 × 1 × 1 | 141 varian duduk |
| 74 | `labu_arak` | labu 3³ + 2³ + tutup, tali merah | 141, pengembara |
| 75 | `pil` | 1 × 1 × 1 pancar (warna mutu pil) | 142 |
| 76 | `botol_pil` | badan 2 × 3 × 2 porselen putih-biru + tutup merah | alkemis |
| 77 | `batu_roh` | gumpal 2 × 2 × 1.5 (2 kubus diputar) pancar hijau-biru | 143 |
| 78 | `herba` | ikat 3 batang 0.5 × 5 × 0.5 + daun 2 × 2 × 0.2 ×3 | 131 |
| 79 | `seruling` | bambu 0.8 × 0.8 × 12 + 3 ruas gelap | 139 |
| 80 | `guqin` | papan 4 × 1.5 × 14 kayu hitam-lak + 7 senar (tekstur) + kaki 1 × 1 × 1 ×2 | 140 (titik `PANGKUAN`) |
| 81 | `pedang_kayu` | bilah 1 × 14 × 2 kayu muda + pelindung 4 × 1 × 2 | latihan murid Tercatat/Luar, anak |
| 82 | `peluit` | tulang 0.8 × 0.8 × 3 | jalan binatang |
| 83 | `cap_sekte` | stempel 2 × 3 × 2 giok + ukiran | Tetua (138 varian) |
| 84 | `bendera_formasi` | tiang 0.6 × 10 × 0.6 + kain 5 × 4 × 0.2 warna Dao | 133, punggung jalan formasi (3 bendera) |
| 85 | `kantong_qiankun` | kantong 2 × 2.5 × 2 bersulam emas + pancar tipis | murid Inti+ (pinggang) |
| 86 | `cermin_perunggu` | cakram 5 × 0.5 × 5 perunggu + gagang | peri, nyonya |
| 87 | `tasbih_tangan` | 12 manik 1³ melingkar (maks 40 elemen) | rahib, peziarah saat doa |
| 88 | `trofi_taring` | 3 taring 0.8 × 2.5 × 0.8 pada tali 6 × 0.4 × 0.4 | jalan `pemburu` (`BAHU_KIRI`) |

**Titik pasang (`Titik`):** `KANAN`, `KIRI` (`translateToHand`, pola `DiscipleHeldItemLayer.hand`), `DUA_TANGAN`
(jangkar tangan kanan, putaran dari posisi kedua tangan pada frame itu: vektor tangan kanan→kiri dihitung dari
`rightArm`/`leftArm` setelah pose), `PUNGGUNG` (badan, translate (0, 4, 2.7)/16, rx 0), `PINGGANG_KIRI` (badan, (4.6, 9,
0)/16), `PINGGANG_KANAN` (badan, (−4.6, 9, 0)/16), `KEPALA` (kepala, (0, −8.6, 0.5)/16: kuas di sanggul, bunga),
`PANGKUAN` (badan, (0, 11.5, −4)/16, rx −90°; hanya saat duduk), `BAHU_KIRI` (badan, (4.2, 0.6, −1.6)/16), `DEPAN_KAKI` (akar model, (0, 23.5, −7)/16: gasing, ikan
kayu). Prop digambar sebagai `ItemStack(Items.STICK)` dengan `DataComponents.ITEM_MODEL` (pola `PhysiqueVisuals.fx`),
dibuat sekali per id (cache) — tidak di inisialisasi statis (aturan `NpcGear`). Prop hilang saat bertarung (`FIGHTING`,
`FLAG_HOSTILE` aktif menyerang), saat cast/duel, dan di LOD ringan (40.6.1).

#### 40.5.8 Gerak sekunder: pegas (`client/sosok/gerak/Pegas`)

Setiap rantai menyimpan sudut θ dan kecepatan ω per sumbu (x, z). Masukan per entitas per frame (dari `extract`, tanpa
fisika dunia): kecepatan dalam bingkai badan `v = R(−bodyRot) × (pos − posLalu)` per tick (maju `vF`, samping `vS`),
kecepatan naik `vY`, laju putar badan `ω_b = wrapDegrees(bodyRot − bodyRotLalu)` per tick, kecepatan sudut lengan dari
`xRot` frame lalu, dan pose badan akhir (lean, kepala). Integrasi semi-implisit Euler dengan sub-langkah 0.5 tick, dt
dijepit ≤ 3 tick:

```
target = istirahat + kompensasi_gravitasi(−sudut induk dunia × g) + seret(vF, vS, vY, ω_b)
ω += (k × (target − θ) − 2ζ√k × ω) × dt ;  θ += ω × dt ;  θ = clamp(θ, min, max)
```

| Rantai | Bagian | k | ζ | Istirahat | Seret | Batas (°) |
|---|---|---|---|---|---|---|
| `RAMBUT` | `rambut_atas` (kaku 0.6), `rambut_bawah(_panjang)` | 0.10 | 0.55 | x: menggantung lurus dunia (−lean badan × 0.9) | x: `+50° × vF`, `+25° × max(0, vY)`; z: `−20° × vS`, `±8° × ω_b` | x −10..65, z −20..20 |
| `EKOR` | `ekor_kuda_1` → `_2` | 0.14 / 0.10 | 0.45 / 0.40 | seg1 x 25° (miring belakang) − kepala.xRot × 0.7 | x: `+40° × vF`, z: `−30° × vS`, `−1.2 × ω_b`; seg2 menerima 0.6 gerak seg1 tertunda | seg1 x 0..80, z −35..35 |
| `KEPANG` | `kepang_1` → `_2` | 0.16 / 0.12 | 0.5 | menggantung | seperti EKOR × 0.7 | x −30..50 |
| `PITA` | `pita_rambut`, `pita_sabuk`, `ikat_dahi_ekor` | 0.07 | 0.30 | menggantung | `+60° × vF` + kibas `8° × sin(0.9 t + fase) × min(1, \|v\| × 6)` | x −10..85 |
| `BUYAO` | `manik_buyao` ×3 | 0.20 | 0.25 | menggantung dunia | laju putar kepala (yaw/pitch) × 1.5; tiap manik fase 0.15 berbeda | x/z −30..30 |
| `JENGGOT` | `jenggot_*` | 0.25 | 0.60 | menggantung dunia (−kepala.xRot) | `+15° × vF` | x −40..30 |
| `KUMIS`, `ALIS` | `kumis_naga`, `alis_panjang` | 0.18 | 0.40 | menggantung | kecil | ±30 |
| `PONI` | `poni_samping` | 0.20 | 0.50 | menggantung | `+20° × vF` | ±25 |
| `LENGAN` | `lengan_lebar_kantong` | 0.12 | 0.50 | **menggantung ke bawah dunia**: `−(lengan.xRot) × 0.85`, `−lengan.zRot × 0.6` | laju sudut lengan × −0.8 (tertinggal) | x −160..20 |
| `JUBAH` | `jubah_luar` | 0.08 | 0.60 | 6° belakang | x: `+70° × vF` (seperti jubah pemain), z: `−15° × ω_b/10` | x 0..75 |
| `SELENDANG` | `selendang_1/2`, `selendang_punggung` | 0.05 / 0.04 | 0.25 | busur; peran `ABADI`/peri: **melayang** (gravitasi 0, ayun sin 6° periode 70) | `+45° × vF`, `−25° × vS` | ±70 |
| `RUMBAI` | `liontin_giok`, `lencana_giok`, `kantong`, `labu`, `rumbai_iblis` | 0.20 | 0.35 | menggantung | ayun pinggul `BZ`, langkah `±6° × sin φ` | ±35 |

**Panel rok** (kinematik + penghalus pegas k 0.30 ζ 0.7): depan `xRot = min(0, min(RLX, LLX)) × 0.85 − badan.xRot × 0.9`,
belakang `xRot = max(0, max(RLX, LLX)) × 0.85 − badan.xRot × 0.9`, sisi `zRot = ±max(0, ±kakiSisi.zRot) × 0.9`; duduk
meditasi: depan `xRot −1.35`, `yScale` 0.45, belakang `yScale` 0.35, sisi `zRot ±0.9` (rok terhampar di lantai); duduk
kursi (klip 10): depan `xRot −1.5` (di atas paha), belakang `yScale 0.5`; berlari: bonus seret depan/belakang `±10°`.
`rok_zirah` sama dengan kekakuan ×1.6. Hasilnya tidak ada kaki yang "menembus" rok saat melangkah.

Pegas hanya dihitung di LOD **Penuh** dan maksimal N entitas terdekat (40.6.1); lainnya θ = target (diam). Nilai pegas
disimpan di `GerakData` dan diterapkan `SosokBusanaModel` (40K) pada bagian bernama.

#### 40.5.9 Tatap, wajah, napas, gestur iseng

- **Tatap kepala** (`Tatap`): sasaran berprioritas: `DATA_LOOK_AT` → lawan bicara (pasangan obrolan 38) → **guru** (murid
  diam ≤ 8 blok dari pemiliknya; sifat SETIA 2× lebih sering, LICIK melirik saja) → pemain terdekat ≤ 6 blok (2 s, sekali
  per 30 s per pemain) → titik minat klip (tungku, meja) → arah jalan. Batas yaw ±55°, pitch ±30°; badan ikut (`TWIST`)
  bila yaw > 40°; kecepatan 8°/tick dengan easing `SINE_IO`. Aditif di atas putaran kepala server (LookControl), dijepit
  agar jumlahnya tetap dalam batas.
- **Mata:** strip `lirik_kiri/kanan` bila selisih sasaran melewati batas kepala > 10° atau saat lirikan cepat (LICIK,
  PENAKUT tiap 60-120 tick); `lirik_atas` saat `FLAG_GAZE`/`tatap_langit`; `lirik_bawah` saat `tunduk`, membaca, menulis.
- **Kedip:** jeda acak 60-140 tick (benih id + waktu), urutan strip `setengah` (1 tick) → `tutup` (2 tick) → `setengah`
  (1 tick); 15 % kedip ganda. Mata tertutup tetap untuk `MATA_TUTUP` (meditasi `tutup_tenang`, tidur, doa, sujud).
- **Mulut berbicara** (`Mulut`): saat `FLAG_TALK`, gelembung kalimat, klip ber-`BICARA`, atau klip 171 `bicara` (murid:
  `VoiceDisciples.say` memanggil `SosokApi.bicara(murid, tick)` yang menyetel `DATA_GERAK` = 171 SEKALI selama panjang
  baris suara, 40I). Urutan dibuat dari teks baris (`KultivatorNpc.line()`): per karakter, vokal (a i u e o) → `bicara_A`/`bicara_E`
  /`bicara_O` (a→A, i/e→E, o/u→O), konsonan → `diam` setengah (`bicara_E` 1 tick), spasi → `diam` 2 tick, tanda baca → jeda
  6 tick; 2 tick per karakter, maks 60 karakter lalu mengulang selama baris masih tampil. Tanpa teks (murid): pola
  semu-acak A/E/O dari benih.
- **Ekspresi dasar** (bila tidak ada klip yang menulis wajah): dari `suasana` (senang → `senyum`, sedih → `sedih`/`sedih`,
  marah → `marah`/`marah`, takut → `kaget`/`menggigit_bibir`, mengantuk → `mengantuk`, malu → `malu`/`menggigit_bibir`,
  bangga → `buka`/`menyeringai`), lalu dari sifat: SOMBONG `menyeringai` 30 %, PENYAYANG `senyum`, PENDENDAM `cemberut`,
  MALAS `mengantuk` 40 %, TENANG `tutup_tenang` sesekali saat diam lama, PENAKUT lirikan sering. Bertarung: `marah`;
  ranah ≥ Jiwa Baru Lahir bertarung: mata `kilat`; terluka (HP < 35 %): `sakit`.
- **Napas** (aditif, lapisan 8+): `BOB ±0.2 px`, `HX ±1°`, bahu `RAZ/LAZ ±1.2°`; periode 80 tick (anak 60, tua 100,
  meditasi 120); setelah berlari > 3 s: amplitudo ×2.5 meluruh ke ×1 dalam 100 tick (terengah, mulut `bicara_A`).
- **Gestur iseng** (`Iseng`, hanya saat sikap diam > 200 tick, satu per 160-320 tick, benih id + waktu/200): lihat
  sekeliling (`HY ±30`), pindah tumpuan (`BZ` bertukar sisi), rapikan lengan baju (klip 148 varian 1), garuk kepala (anak,
  MALAS), menghela napas (`NAPAS 2`, 40 tick), RAJIN: `tebas_udara` (128) sekali, BERANI: regang bahu, PENAKUT: kaget kecil
  saat ada suara mob ≤ 10 blok.

#### 40.5.10 Katalog klip 1-107 (kanonik; data dari 38.N.5 dan 38.T.8.1 dengan perubahan di bawah)

`GerakId` (milik 40I, konstanta `int` + nama) memuat **id dan nama persis** 38.N.5.1-38.N.5.3 (1-78) dan 38.T.8.1
(79-107); kunci, durasi, prop dan peristiwa di tabel 38 itu **adalah data katalog** (40G mengetiknya ke
`GerakKatalog`), kecuali perubahan berikut yang menyatukan pose yang sudah ada (angka dari kode, radian → derajat):

| Id | Perubahan | Asal angka |
|---|---|---|
| 5 `jalan_pikul` | + varian 1 `diam` (tugas air murid): `RAX −166 + osc(2.3°, periode 25)`, `RAZ −11.5`, `LAX −14 − osc`, `LAZ −8.6`, `LEAN 3`, `HZ −3.4`; `PROP_LAMA` | `DutyPoses` WATER |
| 23 `masak` | kunci diganti: `RAX −60 + osc SIN 10.3° periode 21`, `RAZ osc COS 11.5° periode 21`, `LAX −40`, `LAZ −8.6`, `HX 26`; prop 6 (NPC) / `PROP_LAMA` (murid) | `DutyPoses` KITCHEN |
| 44 `memanen` | + varian 1 `sabit` (kebun herba murid): `LEAN 31.5`, `HX 34`, `RAX −63 + osc SIN 17.2° periode 35`, `RAZ 5.7`, `LAX −43`, `LAY 8.6`, `LAZ −5.7`; `PROP_LAMA`. (`bend()` lama diganti `LEAN` lewat `PoseApplier.bendBody`) | `DutyPoses` GARDEN |
| 51 `lempar_pakan` | + varian 1 `loop` (ternak murid): `RAX −51.6 − 28.6 × SIN_POSITIF(periode 28.6)`, `RAZ −14.3 − 28.6 × SIN_POSITIF`, `LAX −31.5`, `LAZ 17.2`, `HX 17.2`; `PROP_LAMA` | `DutyPoses` FARM |
| 25 `palu_landasan` | kunci diganti: `RAX −28.6 − 97.4 × SIN_KUBIK(periode 21)`, `RAZ 4.6`, `LAX −40`, `LAY 17.2`, `HX 20`, `LEAN 6.9`; peristiwa landasan pada puncak turun | `NpcPoses.work` smith |
| 22 `tuang_teh` | + varian 1 `loop`: `RAX −60`, `RAY −14.3 + 8.6 × p`, `RAZ −14.3 × p` (p = 0.5 + 0.5 sin, periode 52), `LAX −48.7`, `LAY 25.8`, `HX 17.2` | `NpcPoses.work` tea |
| 29 `hitung_sempoa` | kunci diganti: `RAX −48.7 + osc SIN 6.9° periode 7`, `RAY −20`, `LAX −48.7`, `LAY 20`, `HX 20` | `NpcPoses.work` abacus |
| 65 `tai_chi_pagi` | kunci diganti (periode 90): `RAX −74.5 + 40.1 sin`, `LAX −74.5 − 40.1 sin`, `RAZ 11.5 + 20 cos`, `LAZ −(11.5 − 20 cos)`, `RLZ 6.9`, `LLZ −6.9`, `LEAN 2.3 sin` | `NpcPoses.work` WARGA |
| 32 `hormat` | kunci baoquan diganti angka `NpcPoses` (`RAX −74.5, RAY −35.5, LAX −71.6, LAY 35.5, LEAN 18.3, HX 17.2`) + varian P `wanfu` (40.5.6) | `NpcPoses` FLAG_BOW |
| 38 `bicara_tangan` | kunci: `RAX −34.4 + osc 14.3° periode 19`, `RAY −14.3 + osc 5.7° periode 37`, `RAZ 8 + osc 4.6° periode 30`, `HX osc 4°`, `HY osc 5.2°`; + varian `megah` (juru lelang, penceramah): `RAX −83 + osc 23°` | `NpcPoses` FLAG_TALK |
| 56 `tatap_langit` | kunci: `HX −45.8`, `RAZ 6.9`, `LAZ −6.9` (bobot 0.6); + varian `tunjuk` (bicara sambil menatap): `RAX −143`, `RAY −5.7`, `RAZ 5.7`, `HX −40` | `NpcPoses` FLAG_GAZE |
| 13 `tidur` | dipakai juga murid (asrama, 39): renderer 40R memutar tubuh 90° di `setupRotations` bila klip 13 aktif; busana `tidur` | 38.N.5.1 |
| 60-62 `latihan_pedang_1..3` | sumber `DUEL`: urutan `KUDA_TENGAH(20) → TEBAS_ATAS(10) → TEBAS_KANAN(10) → TUSUK(10) → PUTAR(15) → KUDA_TINGGI(20) → TEBAS_BAWAH_KIRI(10) → TANGKIS(10) → KUDA_TENGAH(15)` (1); (2) lebih cepat ×0.8 dengan `TEBAS_ATAS_KANAN/KIRI` bergantian; (3) dua tangan berat: `ANGKAT_BERAT → TEBAS_ATAS → HENTAK → SIKAP_PEMBUKA`; pedang = senjata NPC (`NpcGear`) atau pedang natal/kayu (prop 81) murid | `DuelPoseLibrary` |
| 66/67 `tanding_a/b` | sumber `DUEL`, bendera `SINKRON`: a = `TEBAS_KANAN` di t10, b = `TANGKIS` di t10, bertukar di t40 (`TEBAS_KIRI`/`TANGKIS`) | `DuelPoseLibrary` |

Klip 38 lain tidak berubah. 38NL **tidak** membuat katalognya sendiri (40.7.4).

#### 40.5.11 Klip sikap diam 108-121 (dari `NpcPoses` + baru)

Semua LOOP, lapisan ATAS (kaki dibiarkan kecuali ditulis), napas aditif, bendera `JALAN_OK` = boleh tetap saat berjalan.

| Id | Nama | Durasi | Kunci (derajat) | Catatan / asal |
|---|---|---|---|---|
| 108 | `sikap_tangan_belakang` | 80 | `RAX 28.6, RAZ −12.6, LAX 28.6, LAZ 12.6` | `NpcPoses` (lelang, penceramah, utusan); `JALAN_OK` |
| 109 | `sikap_lipat_tangan` | 80 | `RAX −57.3, RAY −44.7, RAZ 3.4, LAX −54.4, LAY 44.7, LAZ −3.4` | `NpcPoses.folded` |
| 110 | `sikap_tangan_lengan` | 80 | `RAX −28.6, RAY −28.6, LAX −28.6, LAY 28.6`; varian `abadi`: `RAX/LAX −41.3` | `NpcPoses` (lengan baju); `JALAN_OK` |
| 111 | `sikap_tombak` | 60 | `RAX −24, RAZ 3.4, LAX −6.9, LAZ 6.9`; `HY osc ±25° periode 60` (menyapu, = 38 #15) | `NpcPoses` PENJAGA |
| 112 | `sikap_gagang` | 80 | `LAX −21.8, LAY 18.3, LAZ 9.2` | `NpcPoses.hilt` |
| 113 | `sikap_santai_l` | 240 | `RLZ 3.5, LLZ −3.5, RAZ 4, LAZ −4`; t0-120 `BZ 1`, t120-240 `BZ −1` (pindah tumpuan, transisi 20) | baru |
| 114 | `sikap_santai_p` | 240 | `RAX −22, RAY −26, LAX −22, LAY 26`; t0-120 `LLZ −4, BZ 2.5, HZ −2`; t120-240 `RLZ 4, BZ −2.5, HZ 2` | baru |
| 115 | `sikap_tua` | 100 | `LEAN 18, HX −10, RAX 28, RAZ −10, LAX 28, LAZ 10`; varian `tongkat`: `RAX −35, RAZ 10, LAX −35, LAY 25` (dua tangan di tongkat) + `GEMETAR 0.6` | baru; `JALAN_OK` (tanpa varian tongkat) |
| 116 | `sikap_anak` | 120 | `HY osc ±25° periode 60`; t40-48 `LIFT 0 → 0.06 → 0` (jinjit); `RAZ/LAZ osc ±10° periode 30` (lengan berayun) | baru |
| 117 | `sikap_berkacak` | 80 | `RAX 8, RAY 20, RAZ 25, LAX 8, LAY −20, LAZ −25, LEAN −4, HX −4` | baru (tinju di pinggang) |
| 118 | `sikap_tunduk` | 80 | `HX 18, LEAN 6, RAX −15, RAY −30, LAX −15, LAY 30` | baru (Tercatat, tawanan, pengemis) |
| 119 | `sikap_angkuh` | 80 | `HX −12, LEAN −5` + kunci 108 | baru (SOMBONG, tuan muda) |
| 120 | `sikap_doa` | 80 | `RAX −70, RAY −38, LAX −70, LAY 38, HX 10`; `MATA_TUTUP` 50 % waktu | baru (rahib); `JALAN_OK` |
| 121 | `sikap_tangan_depan` | 120 | `RAX −30, RAY −32, LAX −30, LAY 32, BZ 1.5` | baru (perempuan sopan, pelayan) |

Sifat menimpa sikap bawaan (bila tabel tidak mengunci): SOMBONG → 119, RENDAH_HATI → 118 (setengah amplitudo), BERANI →
117, PENAKUT → 121 + `BAHU 0.6` + lirikan, MALAS → 113 dengan `LEAN 6`, `HX 8`, gestur menguap (148) tiap ~600 tick,
TENANG → 110 + napas periode 120, AMBISIUS → 109, RAJIN → sikap bawaan + `tebas_udara` iseng, LICIK → 109 + lirik.

#### 40.5.12 Klip murid dan kultivator 122-171

**Ringkasan daftar klip murid** (semua id di katalog yang sama; yang bertanda * sudah ada di 1-107):

| Kelompok | Klip |
|---|---|
| Kultivasi | 122 meditasi_napas, 123 sirkulasi_qi, 124 segel_tangan, 125 tiang_berdiri, 126 telapak_latihan, 143 genggam_batu_roh, 142 makan_pil, 105* semedi_darah (jalan iblis) |
| Pedang | 60-62* latihan_pedang_1..3, 128 tebas_udara, 127 asah_pedang, 172 latih_jurus, 66/67* tanding_a/b, 59* hunus |
| Alkimia & jimat & formasi | 130 kipas_tungku, 131 racik_herba, 132 gulung_pil, 26* tumbuk_herba, 134 tulis_jimat, 135 tempel_jimat, 133 pasang_bendera |
| Tugas sekte | 5* (air), 44* (kebun), 23* (dapur), 51* (ternak), 24* sapu, 4*/41* angkat & bawa, 46* angkat_air, 43* mencangkul, fungsi 150-161 (Sekte), 162-164 (Langit) |
| Hormat & sosial | 32*/33* hormat (baoquan/wanfu), 129 sujud_guru, 16* berlutut, 34* lambai, 36* tertawa, 165 tepuk_tangan, 166 tepuk_bahu, 167 angguk_dengar, 168 lari_lambai, 21*/22*/92* teh, 141 minum_arak |
| Belajar & mengajar | 28* membaca, 27* menulis, 138 mengajar, 136 periksa_nadi, 137 beri_makan_binatang, 139 main_seruling, 140 main_guqin |
| Perasaan & keadaan | 144 terobosan_berhasil, 145 terobosan_gagal, 146 pincang, 147 lengan_digendong, 149 menangis, 170 marah_hentak, 148 menguap_peregangan, 18* kaget |
| Istirahat | 13* tidur, 14* tidur_duduk, 169 tidur_meja, 175 bersandar, 20* makan |
| Kegiatan mandiri 39 (40-56) | 40.5.13 (172-176 + varian) |

| Id | Nama | Mode / durasi | Kunci (derajat; t tick) | Prop / wajah / peristiwa |
|---|---|---|---|---|
| 122 | `meditasi_napas` | LOOP 120, `DUDUK` | duduk meditasi + tangan dhyana: `RAX −40, RAY −38, LAX −40, LAY 38`; t0 `BOB 0, HX 2` → t60 `BOB 0.3, HX −2` → t120 kembali (napas dalam); varian 1 `lutut` (tangan di lutut = `MeditationPose` asli, tanpa kunci lengan) | mata `tutup_tenang`; partikel qi warna Dao tiap 40 (oleh aura lama, bukan Gerak) |
| 123 | `sirkulasi_qi` | LOOP 160, `DUDUK` | t0 `RAX/LAX −40, RAY/LAY ∓38`; t40 `RAX/LAX −95, RAY −40, LAY 40` (telapak naik ke dada, `CUBIC_OUT`); t80 `RAZ 10, LAZ −10` (telapak berbalik), `LIFT 0.03`; t120 `RAX/LAX −60`; t160 kembali | mata tutup; peristiwa PARTIKEL `kultivator:qi_tipis` 6 (warna Dao) di t40 |
| 124 | `segel_tangan` | SEKALI 70 | 6 segel × 10 tick (`TANGGA` + `CUBIC_OUT` 2 tick): (1) `RAX/LAX −80, RAY −40, LAY 40` (2) `RAX −95, LAX −70, RAY −20, LAY 50` (3) `RAX −80, RAZ 15, LAX −80, LAZ −15` (4) `RAX −110, LAX −110, RAY −30, LAY 30` (5) `RAX −70, RAY −50, LAX −90, LAY 20` (6) = (1); `HX 5` | mata `kilat` di segel 6; suara `block.amethyst_block.chime` 0.3 di t60 |
| 125 | `tiang_berdiri` (zhan zhuang) | LOOP 120 | `RLX −15, LLX −15, RLZ 6, LLZ −6, LIFT −0.08, LEAN 4`; memeluk bola: `RAX −70, RAY −30, RAZ 15, LAX −70, LAY 30, LAZ −15`; napas ×1.5; setelah 600 tick `GEMETAR 0.4` | mata `tutup_tenang` |
| 126 | `telapak_latihan` | LOOP 80 | sumber `CAST`: `PALM_THRUST` (t0-20), `TWIN_PALM` (t20-40), `PALM_THRUST` cermin kiri (t40-60), `SEAL` (t60-80); `TWIST ±15` mengikuti; kaki kuda-kuda `RLZ 20, LLZ −20, LIFT −0.15` | suara `entity.player.attack.sweep` 0.2 tiap dorong |
| 127 | `asah_pedang` | LOOP 24, `DUDUK` (lantai, klip 11) | `LEAN 20, HX 25`; `RAX −50`, `RAY −20 → +20 (t12) → −20` (usap kain); `LAX −55, LAY 15` (memegang bilah) | prop kanan kain (dilukis di prop 36 `kain` skala 0.4), kiri pedang natal/kayu; kilau t12 |
| 128 | `tebas_udara` | SEKALI 30 | sumber `DUEL`: `HUNUS → TEBAS_ATAS → SARUNG` | pedang |
| 129 | `sujud_guru` (tiga kali) | SEKALI 110 | t0-10 `berlutut` (16); (t15-30, t45-60, t75-90) `LEAN 62, HX 20, RAX/LAX −100` (dahi ke tanah, `SINE_IO`), di antaranya `LEAN 10`; t100-110 bangkit | mata tutup saat sujud; suara `block.wool.step` 0.2 di t30/t60/t90 |
| 130 | `kipas_tungku` | LOOP 20 | jongkok `LIFT −0.35, LEAN 30, RLX −70, LLX −70`; `RAX −70, RAZ osc ±20° periode 10`; `LAX −40` | prop 64; PARTIKEL asap putih tiap 10 |
| 131 | `racik_herba` | LOOP 30 | t0 `RAX −60` + `LAX −40, LAY 20` (ambil dari keranjang); t10 `RAX −95, RAY −10`; t14 `RAX −75` (`BACK_OUT`, lempar ke tungku); `LEAN 15`, `HX 20` | kanan 78, kiri 18 skala 0.6; PARTIKEL percik api di t15 |
| 132 | `gulung_pil` | LOOP 16 | `RAX −75, RAY −35, LAX −75, LAY 35`; `RAZ osc ±6° periode 8`, `LAZ` lawan fase; `HX 25` | pil (75) di antara tangan (`DUA_TANGAN`) |
| 133 | `pasang_bendera` | SEKALI 40 | t0 `RAX −150`; t12 `RAX −40, LEAN 30, LIFT −0.2` (`BACK_OUT`); t30 kembali | prop 84; SUARA `block.wood.place` t12; PARTIKEL Dao |
| 134 | `tulis_jimat` | LOOP 30 | `LEAN 15, HX 25`; kuas: t0-8 `RAY −15 → 10`, t8-14 `RAX −55 → −70`, t14-20 `RAY 10 → −5`, t20-24 `RAZ 15` (hiasan), t24-30 kembali; `LAX −50` | kanan 10 (kuas), kiri 63; PARTIKEL glif emas t20 |
| 135 | `tempel_jimat` | SEKALI 20 | t0 `RAX −100`; t6 `RAX −85, TWIST 10, HX −5` (`BACK_OUT`); t20 kembali | kanan 63; SUARA `item.book.page_turn` t6 |
| 136 | `periksa_nadi` | LOOP 80, `DUDUK` (kursi) | `RAX −55, RAY −20` (dua jari di pergelangan pasien), `LAX −45`; `HX 20`, angguk kecil t40 `HX 26` | mata `mengantuk` (konsentrasi) |
| 137 | `beri_makan_binatang` | LOOP 40 | `LIFT −0.35, LEAN 35, RLX −70, LLX −70`; `RAX −60 → −40 (t15) → −60`; `HX 25` | kanan herba/daging (78); wajah `senyum` |
| 138 | `mengajar` | LOOP 120 | `LAX 28.6, LAZ 12.6` (tangan kiri di punggung); `RAX −50 + osc 15° periode 30`, `RAZ 10`; `HX osc 0..6° periode 40`; `HY osc ±20° periode 120` | `BICARA`; varian Tetua: kanan 83 (cap) / 65 (kebut) |
| 139 | `main_seruling` | LOOP 80 | sumber `CAST` `FLUTE` + `HZ 8` | prop 79; mulut `meniup`; PARTIKEL `note` tiap 20 |
| 140 | `main_guqin` | LOOP 24, `DUDUK` | duduk meditasi; `LEAN 15, HX 25`; `RAX −45, RAY −10, RAZ osc ±5° periode 6` (petik), `LAX −45, LAY 15 + osc 6° periode 24` (geser) | prop 80 `PANGKUAN`; SUARA `block.note_block.harp` acak nada dari benih tiap 6 (vol 0.25, hanya ≤ 12 blok) |
| 141 | `minum_arak` | SEKALI 40 | t0 `RAX −40`; t10 `RAX −150, RAY −20, HX −35` (tahan sampai t30); t40 kembali | kanan 74; wajah `senang` setelahnya |
| 142 | `makan_pil` | SEKALI 24 | t0 `RAX −60`; t8 `RAX −100, HX −15`; t10 `HX 5` (menelan); t24 kembali | kanan 75; peristiwa WAJAH `makan` t10 |
| 143 | `genggam_batu_roh` | LOOP 80, `DUDUK` | `RAX −45, RAY −35, LAX −45, LAY 35` (dua tangan menangkup di perut) | prop 77 `DUA_TANGAN`; PARTIKEL qi hijau masuk ke tubuh tiap 20; mata tutup |
| 144 | `terobosan_berhasil` | SEKALI 60 | t0 mata `kilat`; t6 `RAX/LAX −170, RAZ 30, LAZ −30, HX −25` (`BACK_OUT`); t20/28/36 tinju `RAX −150 ↔ −120`; t40 `LIFT 0.2`, t44 `LIFT 0`; t60 kembali; varian TENANG: hormat ke langit (`sikap_doa` + `HX −20`, senyum); varian SOMBONG: `sikap_lipat_tangan` + `tertawa` 36 | wajah `tertawa`; tanpa suara baru (FX terobosan lama tetap) |
| 145 | `terobosan_gagal` | SEKALI_TAHAN 70 | t0 `LEAN 25, RAX −70, RAY −45, HX 15` (tangan di dada); t10 mulut `batuk_darah`; t20 satu lutut `RLX −90, LIFT −0.4`; t30-70 `GEMETAR 1.2` | PARTIKEL `dust` merah 4 di mulut t10 |
| 146 | `pincang` | LOOP (lapisan JALAN) | ayun kaki kanan ×0.4; `BZ 6 × sin φ`; `LEAN 6`; `LAX −20, LAY 20` (memegang sisi); `HX 8` | wajah `sakit`; dipakai juga NPC terluka |
| 147 | `lengan_digendong` | LOOP (lapisan ATAS) | `LAX −60, LAY 40, LAZ −5` | perban (atlas `luka 2`) |
| 148 | `menguap_peregangan` | SEKALI 50 | t0-15 `RAX/LAX −170, LEAN −10, HX −20` (meregang); mulut `bicara_A`, mata tutup; t30 kembali; t40 `HY ±10`; varian 1 `rapikan_lengan`: `LAX −60, LAY 40` menyentuh lengan kanan 20 tick | — |
| 149 | `menangis` | LOOP 40 | `RAX −120, RAY −30, LAX −120, LAY 30` (tangan menutup wajah), `LEAN 15, HX 20, BAHU osc 0.5 periode 10` | wajah `sedih`; dipakai `ACT_MOURN` NPC dan murid berkabung (bukan `PaviliunPoses.mourn` yang tetap fungsi 14) |
| 150-164 | (id katalog untuk pose **fungsi** yang sudah ada; tidak ada kunci di Gerak) | — | 150 = `SektePoses` 13 belajar, 151 = 14 berkabung-dupa, 152 = 15 tambang, 153 = 16 rawat, 154 = 17 kantor, 155 = 18 sosial-teh, 156 = 19 merajuk, 157 = 20 apel, 158 = 21 mendaki, 159 = 22 pergi, 160 = 23 sujud tetua, 161 = 24 cadangan, 162 = `LangitPoses` 25 terbang pedang, 163 = 26 jaga tepi, 164 = 27 memandang | `SosokApi.gerak(murid, 150..164)` menyetel **aktivitas** 13-27 (bukan `DATA_GERAK`), sehingga pose lama yang dipakai |

Klip sapaan murid yang lain memakai id 38: `hormat` 32/33, `lambai` 34, `berlutut` 16, `tertawa` 36, `tunjuk` 35,
`kaget` 18, `minum_teh` 21, `seduh_teh` 92 (menyajikan teh untuk guru), `tanding_a/b` 66/67, `sapu` 24, `angkat_bawa` 41,
`jalan_bawa` 4, `angkat_air` 46, `membaca` 28, `menulis` 27, `tidur` 13, `tidur_duduk` 14, `makan` 20, `sorak_arena` 100
(menonton turnamen), `lari` 8.

**Tambahan untuk tepuk dan gestur sosial** (dipakai 39 untuk hubungan antar-murid): 165 `tepuk_tangan` (LOOP 10: `RAX/LAX
−70, RAY −35 ↔ −20, LAY 35 ↔ 20` bertemu di t5), 166 `tepuk_bahu` (SEKALI 30: `RAX −100, RAY −30` ke bahu sasaran
`DATA_LOOK_AT` di t10, tepuk `RAX ±8` di t14/t18), 167 `angguk_dengar` (LOOP 60: `HX 0 → 10 (t10) → 0 (t20) → 8 (t35) →
0`, dasar 121), 168 `lari_lambai` (lapisan ATAS saat lari: `RAX −160, RAZ 20 ± 25` periode 12, wajah senang), 169
`tidur_meja` (LOOP 120, `DUDUK` kursi: `LEAN 40, HX 40, RAX −80, RAY −40, LAX −80, LAY 40`, napas lambat, mata `tutup`, mulut `tidur`),
170 `marah_hentak` (SEKALI 30: t0 `RLX −40, KAKI_PAKSA`; t8 `RLX 0, LIFT −0.05` + SUARA `entity.player.big_fall` 0.2;
tinju `RAZ 15`; wajah `marah`).
171 `bicara` (SEKALI, durasi dari pemanggil, lapisan KEPALA: `HX osc 3° periode 14`, `HY osc 4° periode 37`, `BICARA`; tidak
menyentuh lengan sehingga murid bisa bicara sambil bekerja).

#### 40.5.13 Aktivitas murid mandiri 40-56 (DESIGN 39.4.2, 39.4.8)

`SektePoses.register` hanya menerima 13-24 (40.1), jadi pose 40-56 **tidak** didaftarkan lewat `SektePoses`. 40G memetakan
aktivitas ke klip katalog di `AktivitasKlip` (bagian dari `GerakKatalog`); `DutyPoses.apply` (40G) menganggap aktivitas
yang punya peta sebagai "tugas" (gerbang lama tetap: diam, tenang, tanpa cast/tarung, campur 6 tick) dan menerapkan klip
itu lewat `Sampler.terapkan`. 39E (`MandiriPoses`) **hanya** mendaftarkan prop 39 (`PropKatalog.daftar(100-149, Identifier
model)`) dan kait efek (`GerakKatalog.daftarKait(String, KaitGerak)`: asap kuali, busur tebasan, batu roh mengorbit, kertas
segel melayang, "Z"); ia boleh menimpa peta dengan `GerakKatalog.petakanAktivitas(act, klip, varian)` untuk klip 200-299
miliknya.

| ACT (39) | Klip (varian) | Kunci tambahan / catatan | Prop · Kait |
|---|---|---|---|
| 40 `LATIH_JURUS` | **172** `latih_jurus` (baru) | sumber `DUEL`, LOOP 60: `TUSUK`(15) → `PUTAR`(15) → `TANGKIS`(15) → `KUDA_TENGAH`(15); `TWIST ±30` mengikuti | pedang latih 39 (100) atau pedang natal · kait `mandiri:busur` di t15/t30 |
| 41 `ALKIMIA` | 130 `kipas_tungku` varian 1 `duduk` | `DUDUK` (bersila); kiri mengipas `LAX −60, LAZ osc ±20° periode 17` (1.2 Hz); kanan menabur t40 `RAX −80, RAY −10` → t46 `RAX −60` (`BACK_OUT`), LOOP 80; `HX 20` | kiri 64, kuali kecil 39 di `DEPAN_KAKI` · kait `mandiri:asap` tiap 10 |
| 42 `JIMAT` | 134 `tulis_jimat` varian 1 `duduk` | `DUDUK`, `LEAN 25`, gerak kuas zig-zag ×1.2 | kanan 10, kiri 63, meja 39 di `DEPAN_KAKI` · kait `mandiri:jimat_jadi` di akhir |
| 43 `TANDING` | 66/67 `tanding_a/b` (`SINKRON`) + 32 `hormat` di awal/akhir (39 memutar `gerak` SEKALI 32 sebelum dan sesudah) | 6 pertukaran = 3 siklus 60 tick | pedang kayu (81) |
| 44 `MENGAJAR` | 138 `mengajar` | junior memakai `ACT_STUDY` (fungsi 13) | gulungan (72) |
| 45 `TUTUP_DIRI` | 122 `meditasi_napas` varian 2 `tutup_diri` | napas periode 160, `NAPAS 0.6`, mata `tutup_tenang` tetap | kait `mandiri:segel` (kertas segel melayang) |
| 46 `BERKEMAS` | **173** `berkemas` (baru) | LOOP 30: `berlutut` (16) + `RAX −60 → −30 (t15)`, `LAX −50`, `HX 30` | buntalan 39 di `DEPAN_KAKI` |
| 47 `BAWA` | 4 `jalan_bawa` varian 1 `punggung` | lengan memegang tali bahu `RAX −20, RAY 15, LAX −20, LAY −15`; profil jalan `kuli` | 56 `rangka_punggung` atau kotak 39 di `PUNGGUNG` |
| 48 `MAKAN` | 20 `makan` | duduk (`duduk_kursi` 10 atau `duduk_lantai` 11 menurut titik) | 1, 2 |
| 49 `TAWAR` | 30 `tawar` | sasaran tatap = pedagang (`SosokApi.tatap`) | kantong uang (29) |
| 50 `LIHAT_LAPAK` | **174** `lihat_barang` (baru) | LOOP 40: `LEAN 20, HX 25`, `RAX −70, LAX −70, RAY osc ±20° periode 40, LAY` lawan fase (memutar barang) | barang = item vanilla di tangan utama (39 menyetel `setItemSlot`) |
| 51 `ISTIRAHAT` | 13 `tidur` varian 1 `miring` (kamar) / **175** `bersandar` (baru: pohon, bangku) | 175 LOOP 120: `LIFT −0.55, RLX −80, LLX −60, RLY 10, LEAN −15, HX 10, RAX −20, LAX −10, LAZ −10`; mata `tutup`, napas periode 110 | kait `mandiri:z` (Fana-Fondasi) |
| 52 `TERBANG_JAUH` | **176** `berdiri_di_pedang` (baru; angka = `LangitPoses.fly` agar sama dengan patroli 37) | LOOP: `RLX 2.9, RLZ 3.4, LLX −2.9, LLZ −3.4, KAKI_PAKSA`; `LEAN 10.3 + osc 2.3° periode 27`; `BZ osc 2.9° periode 70`; `RAX −77.3 + osc 2.3°, RAY −31.5, RAZ 6.9`; `LAX 31.5, LAY 14.3, LAZ 20`; `HX 5.7`, `HZ osc −2.3°` | pedang terbang 39 (kait) |
| 53 `MERENUNG` | 56 `tatap_langit` varian 2 `renung` | `HX −20` (bukan −45), tangan di punggung (108); duduk bila titik kursi | kait `mandiri:kelopak` |
| 54 `SERAP_BATU` | 143 `genggam_batu_roh` varian 1 `terbuka` | telapak terbuka ke atas: `RAX −50, RAY −20, RAZ 20, LAX −50, LAY 20, LAZ −20` | kait `mandiri:batu_orbit` |
| 55 `JAGA_TEROBOSAN` | 109 `sikap_lipat_tangan` | tatap ke sahabat yang menembus (`SosokApi.tatap`), kepala menyentak ke sambaran (`GerakReaksi`) | — |
| 56 `MEDITASI_MANDIRI` | 122 `meditasi_napas` | — | — |

Klip baru dari tabel ini memakai id **172-176**; `GerakId` 40 berisi 1-176.

---------------------------------------------------------------------------------------------------------------------

### 40.6 Performa, Sodium/Iris, setelan, perintah uji, uji terima

#### 40.6.1 Anggaran dan LOD (laptop lemah)

Tingkat detail (`SosokDetail.tingkat()`, 40R) = setelan "Detail animasi" (40.6.2). Jarak diukur dari kamera
(`state.distanceToCameraSq`).

| Anggaran | Tinggi | Sedang (bawaan Otomatis+Normal) | Rendah |
|---|---|---|---|
| Atlas sosok di cache (LRU) | 96 (≈ 12 MiB GPU+RAM) | 64 (≈ 8 MiB) | 32 (≈ 4 MiB) |
| Penyusunan atlas per frame / batas waktu | 2 / 2.0 ms | 1 / 1.5 ms | 1 / 1.0 ms |
| LOD **Penuh** (semua lapisan Gerak, pegas, wajah, prop, semua kubus) | ≤ 20 blok | ≤ 16 | ≤ 12 |
| LOD **Kurang** (klip & sikap disampel tiap 2 frame, tanpa pegas/wajah/gestur iseng/tatap mata; kubus besar saja: `rok_*`, `lengan_lebar_*`, `jubah_luar`, `rambut_bawah*`, `ekor_kuda_*` diam, `caping_*`; prop besar saja) | ≤ 40 | ≤ 32 | ≤ 24 |
| LOD **Siluet** (tubuh beratlas saja: busana sudah terlukis di lapisan luar kulit; jalan vanilla + sikap diam beku; tanpa lapisan busana/wajah/prop) | ≤ 72 | ≤ 56 | ≤ 40 |
| Lebih jauh | tubuh dengan **atlas cadangan** kelasnya (tanpa atlas unik, tidak memicu penyusunan), tanpa lapisan | sama | sama |
| Entitas dengan Gerak penuh per frame (terdekat) | 24 | 16 | 10 |
| Entitas dengan pegas | 12 | 8 | 4 |
| Shadow pass Iris (`ShaderCompat.shadowPass()`) | tubuh + `rok_*` + `rambut_bawah*` + `caping_*`; tanpa wajah, prop kecil, pita, manik | tubuh + `rok_*` | tubuh saja |
| `EffectQuality` Minimal | memaksa Rendah; partikel klip ×0 (sudah lewat `EffectQuality.scale`) | — | — |

- Ambang LOD memakai histeresis 2 blok (tidak berkedip saat berdiri di batas).
- Penyusunan memprioritaskan entitas terdekat di LOD Penuh; antrean maks 64, sisanya memakai cadangan sampai giliran.
- Tidak ada alokasi per frame di jalur panas (`GerakKeadaan` dan `float[24]` dipakai ulang; kunci cache `long`).
- **Target terukur** (dicek `/sosok info`): Gerak + ekstrak ≤ 0.06 ms per entitas Penuh, penyusunan satu atlas ≤ 0.8 ms
  rata-rata di laptop pengguna, total Sosok ≤ 1.5 ms per frame di ibu kota 38 (28 tubuh) pada Sedang.
- Penonton turnamen 36 (sampai 160) **tidak pernah** memakai atlas unik: atlas cadangan per kelas + warna sekte lama
  (aturan ringan 36.3.9 tetap).

#### 40.6.2 Setelan

- Klien (`config/kultivator-client.json`, lewat `EffectQuality.get/put` pola `AuraSettings`): `sosok_detail` =
  `otomatis` (bawaan: Maksimal → Tinggi, Normal → Sedang, Minimal → Rendah) / `tinggi` / `sedang` / `rendah`;
  `sosok_wajah` = true (ekspresi dan kedip; mati = wajah diam, hemat sedikit). Baris di hub **Pengaturan → Tampilan &
  Efek**, judul baru "Penampilan & Animasi" (`kultivator.sosok.setelan.judul`): talisman putar "Detail animasi:
  Otomatis/Tinggi/Sedang/Rendah" (tooltip menjelaskan jarak dan jumlah per tingkat) dan koin "Ekspresi wajah". Disunting
  di `SettingsRows.tampilan` (40R) setelah judul aura.
- Server (`DuniaConfig.section("sosok", SosokConfig.class, ...)`, dijepit saat dimuat): `rasioPerempuan` 0.5 (0-1),
  `anakSaatRekrut` 0.10 (0-0.5), `dewasaSaatRekrut` 0.08 (0-0.5), `hariAnakKeMuda` 48 (8-400), `hariMudaKeDewasa` 160
  (24-1000), `skalaTubuh` true (pakai `Attributes.SCALE`; false = semua 1.0 kecuali anak 0.66 tetap), `variasiTinggi` true.

#### 40.6.3 Sodium, Iris dan mod lain

- **Hanya `RenderTypes` vanilla** (`entityCutout` untuk tubuh+busana+wajah, `entityTranslucent` untuk arwah, klon,
  bayangan, tubuh roh) → shader entitas paket Iris berlaku normal; tidak ada `RenderPipeline`, shader, atau format
  verteks sendiri. Tekstur dinamis diikat lewat `Identifier` biasa (Iris membaca `TextureManager`).
- **Shadow pass**: lapisan kecil dilewati (40.6.1); `GerakPengendali` **tidak** dievaluasi ulang di shadow pass (render
  state yang sama dipakai; Iris menggambar ulang dari state yang sudah diekstrak).
- **Sodium**: menggambar `ModelPart` lewat jalur kubus cepatnya; Sosok hanya memakai `visible`, rotasi, posisi dan skala
  bagian (didukung) dan bidang wajah berkedalaman 0 (aman, 40.3.1). Tidak menyentuh render chunk.
- **Player Animation Library / Emotecraft**: PAL hanya menganimasikan `Avatar` (pemain); NPC dan murid bukan `Avatar`
  sehingga tidak pernah dianimasikan PAL. Gerak tidak berbuat apa-apa pada render state tanpa data `GERAK` (pemain), jadi
  animasi PAL pemain dan `PalBridge` duel tidak berubah. Uji: dengan PAL terpasang + `duel_pal` aktif, duel pemain vs murid
  memutar animasi PAL pada pemain dan pose duel 35 + wajah/pegas Sosok pada murid.
- **Better Combat**: tidak berubah (milik 35.5.6).
- **EMF/ETF**: tubuh Sosok memakai lapisan model sendiri `kultivator:sosok_tubuh(_ramping)`, bukan `minecraft:player`,
  sehingga paket `.jem` pemain tidak mengganti model NPC; id tekstur dinamis `kultivator:sosok/<hex>` tidak punya varian
  ETF. Entity Culling: memakai kotak tabrak (sudah diskala `SCALE`).

#### 40.6.4 Perintah uji

**Server** (`/kultivasi uji penampilan ...`, op 2, didaftarkan `DuniaCommands.test("penampilan", SosokCommands::node)`,
40I). Semua NPC uji adalah `KultivatorNpc` ber-tag `kultivator.sosok_uji`, `setNoAi(true)` kecuali disebut, hilang
sendiri setelah 600 detik atau dengan `hapus`; tidak disimpan (tipe `noSave`).

| Perintah | Hasil yang terlihat |
|---|---|
| `galeri peran [halaman]` | baris 8 × 4 NPC di depan pemain (jarak 2 blok), satu peran per kolom (L dan P berpasangan, usia bawaan); papan nama = kunci rupa; 40.4.1 A-D berhalaman (± 125 rupa, 32 per halaman) |
| `galeri murid` | 6 pangkat × (L, P) + calon, saingan, bayangan, klon (rupa `murid.*` lewat `DATA_RUPA`) dengan Dao berbeda per kolom |
| `galeri jalan` | 16 jalan hidup 40.4.3 × (L, P) |
| `galeri usia <peran>` | satu peran × 4 usia × 2 kelamin (skala, kepala anak, bungkuk tua terlihat) |
| `galeri dao` | murid Dalam 8 Dao × ranah Inti Emas dan Jiwa Baru Lahir (iris, tanda dahi, sulam) |
| `galeri fisik` | 9 fisik langka (pupil ganda dst.) pada wajah dekat (NPC 1.5 blok, menghadap pemain) |
| `acak [n]` | n (bawaan 16, maks 32) sosok acak dengan benih baru → bukti variasi; ulangi = rupa lain |
| `klip <id\|nama> [radius]` | NPC uji/murid dalam radius (bawaan 8) memainkan klip (`SosokApi.gerak`) |
| `klip semua` | 24 NPC berbaris, masing-masing memutar katalog bergiliran (durasi klip + 20 tick), papan nama = `id nama` |
| `klip kelompok <tempat\|sikap\|murid\|fungsi\|aktivitas>` | hanya kelompok itu (1-107, 108-121, 122-176, 150-164, aktivitas 39 40-56 lewat murid uji) |
| `jalan` | 14 NPC (satu per profil 40.5.6) berjalan bolak-balik antara dua tanda 12 blok (AI navigasi hidup), papan nama = profil |
| `wajah` | NPC 1.5 blok di depan: setiap strip mata (2 s) lalu setiap strip mulut (2 s), lalu baris bicara "Salam, Tuan Muda!" |
| `pegas` | 4 NPC: rambut panjang, ekor kuda, lengan lebar + jubah luar, selendang pibo; berlari mengelilingi pemain radius 5 lalu melompat (AI hidup) |
| `murid <acak\|kelamin l\|p\|usia ..\|jalan <id>\|luka 0-4\|suasana <id>\|busana 0-7>` | mengubah murid **milik pemain** yang ditatap (≤ 8 blok) secara permanen (uji) |
| `pratinjau_benih <benih> <rupa>` | satu NPC dengan benih tepat (dibandingkan dengan pratinjau Python benih yang sama) |
| `hapus` | menghapus semua NPC uji Sosok |

**Klien** (`/sosok ...`, `ClientCommandRegistrationCallback` pola `EpicClientCommands`, 40R): `info` (tingkat, jumlah
per LOD, atlas n/maks + MiB, penyusunan rata-rata/maks 5 s terakhir, antrean), `lod` (menyalakan tulisan LOD kecil di atas
kepala setiap sosok selama 30 s), `dump [entitas yang ditatap | benih <n> <rupa>]` (menulis atlas ke
`<gameDir>/screenshots/sosok/<hex>.png` dengan `NativeImage.writeToFile`), `muat_ulang` (melepas semua atlas, membaca ulang
`resep.json` dan lembar bagian), `bingkai` (menggambar kotak kubus busana dengan garis tipis untuk memeriksa UV, 30 s).

#### 40.6.5 Uji terima (setiap baris harus terlihat oleh penguji)

| Fitur | Bukti | Cara cepat |
|---|---|---|
| Murid L/P eksplisit | rekrut 10 murid: ±5 laki-laki dan ±5 perempuan; nama, suara (`grunt.female/male`) dan rupa cocok; tidak ada murid perempuan dengan wajah/kumis laki-laki | rekrut, `galeri murid` |
| Kulit unik | 32 sosok `acak` tidak ada yang kembar; dua penjaga bersebelahan beda wajah/rambut tetapi seragam sama | `acak 32`, ibu kota |
| Gaya kultivator | murid tiap pangkat terlihat naik tingkat (tepi, hiasan rambut, lencana, jubah luar Tetua, mahkota Tetua Agung) | `galeri murid` |
| Seragam sekte | ubah seragam di perancang lambang (36): warna, potongan, lambang punggung murid Inti berubah dalam 1 s | perancang 36 |
| Rambut xianxia | sanggul + guan, rambut tergerai, ekor kuda, sanggul kembar, buyao bergoyang, kepala gundul rahib dengan titik ordinasi, janggut putih tetua | `galeri peran`, `wajah` |
| Fisik langka | murid Pupil Ganda menampakkan dua pupil emas dari 2 blok; Tulang Tertinggi tanda emas di dada | `galeri fisik` |
| Peran NPC | setiap peran 40.4.1 punya busana, topi dan prop berbeda; tidak ada NPC berkulit Steve/Alex | `galeri peran` semua halaman |
| Proporsi | anak 0.66 dengan kepala besar, perempuan lebih ramping, tua membungkuk; kotak tabrak anak kecil (F3+B) | `galeri usia warga` |
| Animasi berlapis | NPC bicara sambil berjalan (mulut + tangan) tanpa kaki berhenti; hormat wanfu (P) vs baoquan (L) | `klip 32` di galeri campuran |
| Pegas | rambut panjang dan ekor kuda terlambat mengikuti saat berbelok, lengan lebar menggantung ke bawah saat lengan diangkat, jubah luar berkibar saat lari | `pegas` |
| Rok tidak tembus | berjalan dan berlari: kaki tidak menembus rok panjang; duduk bersila: rok terhampar | `jalan`, meditasi murid |
| Wajah | kedip acak, lirik mengikuti pemain, mulut bergerak sesuai suku kata, mata tertutup saat meditasi | `wajah`, mendekati NPC |
| Gaya jalan | 14 profil terlihat berbeda: tua bertongkat mengetuk sinkron langkah, anak melompat-lompat, rahib menunduk | `jalan` |
| Klip murid | meditasi bernapas, sirkulasi qi, alkimia, jimat, sujud guru, terobosan berhasil/gagal, pincang, tidur | `klip kelompok murid` |
| Penyatuan pose lama | tugas air/kebun/dapur/ternak, Sekte 13-24, Langit 25-27, cast 27 dan duel 35 tetap tampil sama/lebih baik dan tidak saling tumpang | tugas sekte, duel murid |
| LOD | `/sosok info` di ibu kota: Penuh ≤ batas, atlas ≤ batas; frame tidak turun > 10 % dibanding Sosok mati (`sosok_detail rendah`) | ibu kota 38 |
| Iris | dengan paket shader umum: tidak ada tekstur ungu-hitam, bayangan NPC ada, tidak ada kedip | Iris + paket |

---------------------------------------------------------------------------------------------------------------------

### 40.7 Modul implementasi (tepat empat), pemilik berkas, kontrak, kaitan 38/39, daftar uji

#### 40.7.1 Empat modul dan berkas baru (milik penuh, tidak bertumpuk)

| Modul | Lingkup | Berkas baru | Aset, generator, lang |
|---|---|---|---|
| **40I** Identitas & data (server + bersama) | 40.2, 40.4 data server, `GerakId`/`PropId`, API, perintah server, galeri | `src/main/java/dev/kultivator/sosok/{Sosok, Gaya, SosokGen, SosokApi, SosokConfig, SosokUsia, SosokJalan, RupaKunci, GerakId, PropId, SosokCommands, GaleriUji}.java` | `lang-src/sosok.{id_id,en_us}.json` (nama murid 36-71, `kota.nama_diri_l/p`, nama klip `kultivator.sosok.klip.<id>`, nama prop, nama rupa, pesan perintah) |
| **40K** Kulit & busana (klien + generator) | 40.3, 40.4 data resep, prop model 63-87 | `src/client/java/dev/kultivator/client/sosok/kulit/{KulitPenyusun, KulitCache, KulitCadangan, BagianCache, Resep, RupaResolver, SosokRupa, Palet, SplitMix64, LambangSalin}.java`, `src/client/java/dev/kultivator/client/sosok/busana/{SosokLapisan, SosokTubuhModel, SosokBusanaModel, WajahModel, BusanaKatalog, RambutBagian, BusanaBagian, BusanaLayer, WajahLayer, SosokGear}.java` | `tools/{sosok_lib, gen_sosok_bagian, gen_sosok_resep, gen_sosok_cadangan, gen_sosok_pratinjau, gen_sosok_prop, cek_sosok}.py`; `assets/kultivator/textures/sosok/**`, `assets/kultivator/sosok/resep.json`, `assets/kultivator/{models/item,items}/fx/sosok/**`, `assets/kultivator/textures/item/fx/sosok/**` |
| **40G** Gerak (klien) | 40.5 | `src/client/java/dev/kultivator/client/sosok/gerak/{GerakKlien, GerakPengendali, GerakKeadaan, GerakData, GerakApplier, Klip, Kunci, Easing, Osilator, Sampler, SumberCast, SumberDuel, GerakKatalog, KatalogTempat, KatalogSosok, ProfilJalan, Pegas, Tatap, Wajah, Iseng, GerakReaksi, PropKatalog, SosokPropLayer}.java` | — |
| **40R** Integrasi renderer, setelan, uji klien | 40.2.3 (klien), 40.6.2-40.6.4 klien, pemasangan semua renderer | `src/client/java/dev/kultivator/client/sosok/{SosokClient, SosokRender, SosokDetail, SosokKlienPerintah}.java` | `lang-src/sosok_klien.{id_id,en_us}.json` (setelan, `/sosok`) |

#### 40.7.2 Pemilik berkas lama (satu pemilik per berkas; semua suntingan aditif atau pengalihan yang disebut)

| Berkas lama | Pemilik | Suntingan |
|---|---|---|
| `Kultivator.java` | 40I | satu baris `dev.kultivator.sosok.Sosok.init();` setelah `Disciples.init()` |
| `disciple/DiscipleEntity.java` | 40I | `DATA_SOSOK, DATA_GAYA, DATA_GERAK, DATA_GERAK_T0, DATA_PROP, DATA_LOOK_AT`; NBT `sosok`/`gaya`; tablet & kepergian; `SCALE` saat sosok disetel; getter `syncedSosok()` dst. |
| `disciple/Disciples.java` | 40I | rekrut: kelamin/usia/benih (`SosokGen.untukMurid`), nama berkelamin, `slim` = perempuan; `setupBody`/klon menyalin sosok |
| `disciple/DiscipleNames.java` | 40I | kolam L/P (40.2.5), `pick(random, taken, kelamin)` |
| `disciple/DuelRules.java` | 40I | anak tidak boleh duel (satu syarat) |
| `voice/VoiceDisciples.java` | 40I | `female()` dari sosok; `say` memanggil `SosokApi.bicara` |
| `dunia/kota/KultivatorNpc.java` | 40I | field data Sosok + `DATA_RUPA` (String); `setup` menurunkan sosok dari peran+look, memaksa `voice` sesuai kelamin, `SCALE`; `female()` dari sosok |
| `dunia/kota/KotaNames.java` | 40I | `person(look, perempuan)`, `plate` memakai `SosokGen.kelamin(peran, look)` |
| `client/KultivatorClient.java` | 40R | satu baris `dev.kultivator.client.sosok.SosokClient.init();` setelah `DiscipleClient.init()` |
| `client/disciple/DiscipleRenderer.java` | 40R | model tubuh Sosok (128 tex) + `BusanaLayer`/`WajahLayer`/`SosokPropLayer`; `DiscipleRobeLayer` digerbang (`!SosokRender.aktif`); tekstur = atlas; tidur 90°; `GerakApplier.angkat` di `setupRotations` |
| `client/disciple/DiscipleRenderState.java` | 40R | tidak ada field baru wajib (data lewat `FabricRenderState`); boleh menambah `public int sosok, gaya;` untuk kecepatan |
| `client/dunia/kota/NpcRenderer.java` | 40R | sama seperti murid; `CulledRobe` digerbang; gelembung kalimat tetap |
| `client/dunia/kota/KotaGearLayer.java` | 40R | sembunyikan bagian `SosokGear.SEMBUNYI[kind]` saat Sosok aktif; LOD (tidak digambar di Siluet) |
| `client/dunia/dimensi/render/HumanNpcRenderer.java`, `ArwahRenderer.java`, `LautNpcRenderer.java` | 40R | Sosok untuk rupa `d.*` manusia; gestur lama (ArmPose BLOCK, crouch untuk bow) diganti klip 38/32; kulit ikonik tetap |
| `client/musuh/RogueRenderer.java`, `client/purba/EnvoyRenderer.java` | 40R | Sosok rupa `liar.*` / `utusan_langit`; lapisan topeng/lingkaran lama tetap |
| `client/sekte/turnamen/PenontonRenderer.java` | 40R | atlas cadangan per kelas (tanpa atlas unik) |
| `client/screen/SettingsRows.java` | 40R | judul + 2 baris (40.6.2) di `tampilan` |
| `client/mixin/PlayerModelMixin.java` | 40G | satu baris `GerakApplier.apply(model, state)` setelah `PoseApplier.apply` |
| `client/disciple/power/DutyPoses.java` | 40G | 4 kasus tugas → `Sampler.terapkan(model, state, GerakId.X, varian, s, t)`; aktivitas yang punya peta `AktivitasKlip` (40-56 dari 39) ikut gerbang `duty`; gerbang dan `Model` tetap; `bend()` dihapus (diganti LEAN) |
| `client/dunia/kota/NpcPoses.java` | 40G | badan diganti pemetaan bendera/peran → klip (108-121, 32, 38, 56, 25/22/29/65) lewat `GerakPengendali`; `clear()` tetap |

**Tidak disunting oleh 40:** `PoseLibrary`, `CastPoses`, `PoseApplier`, `PoseRenderData`, `MeditationPose`,
`CultivatorModels`, `DiscipleRobeLayer`, `DiscipleLook`, `DiscipleHeldItemLayer`, `SektePoses`, `LangitPoses`,
`LangitDutyPosesMixin`, `KotaGear` (mesh), `Headwear`, `DuelPoses`, `DuelPoseLibrary`, `PalBridge`, `LambangAtlas`,
`LambangCache`, `JubahLook`, `kultivator.mixins.json` (tidak ada mixin baru), `network/**` (sinkron lewat entity data),
`docs/DESIGN.md`, semua berkas 37 dan 38.

**Syarat waktu:** `DiscipleEntity` juga disunting 39A (`Role.JELMAAN`, ACT 40-56, `addModeListener`): 40I menyunting
setelah langkah 1 39A mendarat (keduanya aditif, field berbeda). 40 mendarat **setelah 37 selesai** (37 sedang menyunting kode; `KultivatorClient`, `Kultivator`,
`DiscipleEntity` mungkin disentuh 37G/37V; cek `docs/progres/langit-37*.md` dan `git log` sebelum menyunting) dan
**sebelum langkah 2 DESIGN 38** (38.E.2 menyerahkan `KultivatorNpc`, `NpcPoses`, `NpcRenderer` ke 38NL; dengan 40 lebih dulu,
38NL hanya menambah konstanta `NpcRole` dan gol/jadwal di `KultivatorNpc`/`NpcAi`/`NpcLife`, 40.7.4). Bila 38 sudah mulai
lebih dulu, suntingan 40I pada `KultivatorNpc` dan 40R pada `NpcRenderer` dilakukan **setelah** 38NL mendarat dan bersifat
aditif di atasnya.

#### 40.7.3 Kontrak antarmodul (nama tetap)

- **C40.I1 `SosokApi`** (40I, server): `Sosok sosok(LivingEntity)`, `void setSosok(LivingEntity, Sosok)`, `void
  setUsia(LivingEntity, int usia)`, `Gaya gaya(LivingEntity)`, `void setJalan(DiscipleEntity, String jalanId)`, `void
  setSuasana(LivingEntity, int suasana, int tick)` (kembali netral setelah tick; 0 = tetap), `void setLuka(LivingEntity,
  int)`, `void setBusanaKhusus(LivingEntity, int)`, `void gerak(LivingEntity, int klip, int varian, int tick)`, `void
  gerak(LivingEntity, int klip, int varian, int tick, int propKanan, int propKiri, int propPunggung)`, `void
  gerakSinkron(LivingEntity, int klip, long t0)`, `void henti(LivingEntity)`, `void tatap(LivingEntity, @Nullable Entity)`,
  `void bicara(LivingEntity, int tick)`, `void setRupa(KultivatorNpc, String kunciRupa)` (format `kunci[|sekte=<kunci
  LambangCache>][|dao=<n>][|pangkat=<n>]`), `boolean perempuan(LivingEntity)`, `int usia(LivingEntity)`. Untuk entitas
  selain `DiscipleEntity`/`KultivatorNpc`: getter memberi nilai bawaan, setter tidak berbuat apa-apa.
  `gerak` dengan id 150-164 menyetel aktivitas murid 13-27 (40.5.12) bila modul pemiliknya aktif.
- **C40.I2 Rentang id** (`GerakId`, `PropId`; tidak pernah diurutkan ulang): klip 1-107 = 38, 108-199 = 40, **200-299 =
  39**, 300-399 = tambahan 38 (setelah 107), 400-499 cadangan; prop 1-62 = 38, 63-99 = 40, **100-149 = 39**, 150-199 = 38.
- **C40.I3 `SosokJalan`**: id string 40.4.3 (+ `ALIAS` untuk nama 39); `int bit(String)`, `String id(int)`.
- **C40.K1** (klien, 40K): `Identifier KulitCache.atlas(SosokRupa)` (null = belum; pakai `KulitCadangan.untuk(kelamin,
  usia, kelas)`), `SosokRupa RupaResolver.dari(...)`, `void KulitCache.lepasSemua()`.
- **C40.K2 `SosokGear`** (40K): `TOPI_MENUTUP`, `SEMBUNYI`; `static void daftarTopi(int kotaGearKind, boolean menutup,
  String... bagianDisembunyikan)` dan `daftarHeadwear(int kind, ...)` untuk jenis perlengkapan baru 38MD/38TV.
- **C40.G1 `GerakKatalog`** (40G): `@Nullable Klip klip(int id)`, `static void daftar(Klip)` (hanya id 200-399; 39 dan 38
  mendaftarkan klip tambahan dari klien init-nya), `static void petakanAktivitas(int aktivitas, int klip, int varian)`
  (aktivitas murid → klip; 40-56 sudah dipetakan 40G, 40.5.13), `static void daftarKait(String id, KaitGerak kait)` (efek
  klien pada peristiwa `KAIT`; `KaitGerak.picu(LivingEntity, DiscipleRenderState, float t)`); `PropKatalog.daftar(int id,
  Identifier modelItem)` (prop 100-149 untuk 39, 150-199 untuk 38), `Klip.Pembangun` fluent (`Klip.baru(id, nama, durasi).loop().kunci(t,
  kanal, nilai, ...).osc(...).prop(...).varian(...)`).
- **C40.G2 `GerakData`** (render data key `kultivator:gerak`): dibaca 40K (`SosokBusanaModel`: pegas, panel rok, wajah) dan
  40R (`setupRotations`: BOB/SPIN, tidur).
- **C40.K3 inisialisasi klien:** `SosokClient.init()` (40R) memanggil `SosokLapisan.register()` (40K, lapisan model sebelum
  baking, pola `CultivatorModels.register`) dan `GerakKlien.init()` (40G: katalog, pembersih tiap 200 tick, perintah tidak).
- **C40.R1 `SosokRender`** (40R): `void ekstrak(LivingEntity, DiscipleRenderState, float pt, RupaMasukan)` dipanggil semua
  renderer manusia; `boolean aktif(EntityRenderState)`; `RupaMasukan(String kunciRupa, int realm, int dao, int sectRank,
  int physique, @Nullable LambangCache.Entry seragam, int kotaGearKind, int headwearKind, boolean tembus)`.
- **C40.R2 `SosokDetail`** (40R): `Tingkat tingkat()`, `Lod lod(EntityRenderState)` (`PENUH, KURANG, SILUET, JAUH`),
  `boolean wajah()`; dibaca 40K dan 40G.

Urutan kerja: (1) semua modul mulai paralel dengan kontrak di atas (berkas terpisah); 40I lebih dulu menulis
`GerakId`/`PropId`/`Sosok`/`Gaya` (dibutuhkan semua) dalam sesi pertama; (2) tiap modul menjaga
`docs/progres/penampilan-<modul>.md` (checklist + langkah berikutnya); (3) langkah akhir: jalankan generator 40K
(`gen_sosok_*`, `cek_sosok.py`), periksa pratinjau dengan Read, `mergeLang`, lalu **satu** langkah Gradle (`compileJava`,
`compileClientJava`, `jar`, `--no-daemon`). Tidak ada tes, `runClient` atau `gradlew build` (aturan pengguna).

#### 40.7.4 Kaitan wajib DESIGN 38 dan 39 (mengikat; menggantikan bagian 38 yang disebut)

| Rencana 38/39 | Sekarang |
|---|---|
| 38.N.1 `DATA_ANIM`, `DATA_ANIM_T0`, `DATA_PROP`, `DATA_LOOK_AT` (38NL di `KultivatorNpc`) | sudah ada dari 40I dengan nama `DATA_GERAK`, `DATA_GERAK_T0`, `DATA_PROP`, `DATA_LOOK_AT`; 38NL **tidak** menambahkannya |
| 38.N.5 `NpcKlip`, `KlipKatalog`, `NpcKlipPlayer`, `TatapKepala` (38NL) | **tidak dibuat**; katalog dan pemutar = 40G; `HidupApi.klip(npc, klip, prop, tick)` memanggil `SosokApi.gerak(npc, klip, 0, tick, prop, 0, 0)`; `HidupApi.tatap` → `SosokApi.tatap` |
| 38.N.5.4 `NpcPropLayer` (38NL), `PropId` di `dunia/tempat/hidup` | **tidak dibuat**; `SosokPropLayer` (40G) dan `dev.kultivator.sosok.PropId` (40I; indeks 1-62 sama) |
| 38.O.1 `NpcFaceLayer`, atlas `wajah.png`, skala anak, tidur | **tidak dibuat** oleh 38MD; wajah, skala dan tidur = 40 |
| 38.O.1 perlengkapan badan `jubah_naga`, `zirah_lamela` (bagian badan; helm tetap 38MD), `jubah_trigram`, `jubah_lambang_sekte`, `jubah_iblis`, `kain_compang`, `celemek_kulit`; rambut `janggut_putih_panjang`, `sanggul_nenek`, `ikat_rambut_anak` | menjadi busana/rambut 40 (gaya 40.3.7, kubus 40.3.6) lewat tabel 40.4.1; 38MD membuat sisanya (topi, helm, perisai, kacamata, tasbih, sabuk alat, keranjang punggung, kipas pinggang, lencana bintang, sarung tangan empu, topi bulu) dan mendaftarkan topi lewat C40.K2 |
| 38.T.8.2 badan `rompi_bergaris`, `mantel_nakhoda`, `celemek_ter`, `baju_sisik`, `selendang_penari`, `jubah_teh`, `kasaya`, `zirah_gladiator`, `jubah_pemburu_lurus`, `jubah_naga_hitam`; rambut `sanggul_giok` | menjadi busana/rambut 40; 38TV membuat topi/aksesori lainnya |
| 38.O.1 `ImpostorModel` (abu 32 × 32) | memakai **atlas cadangan** 40K (`KulitCadangan.untuk`) dengan UV wilayah A, agar rupa jauh sama dengan rupa dekat |
| 38.E.2 pemilik `NpcPoses.java`, `NpcRenderer.java` = 38NL | pemilik = 40G / 40R; 38NL tidak menyunting keduanya |
| 38.E.2 `KultivatorNpc.java` = 38NL | 40I menambah data Sosok lebih dulu; 38NL tetap pemilik untuk gol/jadwal/`pendudukId` (aditif) |
| 38DS NPC sekte (`MURID_SEKTE_*`, `TETUA_SEKTE`) | memanggil `SosokApi.setRupa(npc, "murid.<pangkat>|sekte=npc:<sekte>")` / `"ketua_sekte|sekte=..."` |
| 38ST dialog | tidak berubah: `KultivatorNpc.say` sudah menggerakkan mulut |
| 38TK entitas bersinkron (dayung, tandu) | `SosokApi.gerakSinkron(npc, klip, t0)` |
| 39.4.8 / C39.E3 `MandiriPoses` "mendaftarkan semua pose 40-56" lewat `SektePoses.register` | **tidak mungkin** (`register` melempar di luar 13-24) dan tidak perlu: pose 40-56 = klip 40 (40.5.13); `MandiriPoses` hanya mendaftarkan prop 39 (`PropKatalog.daftar` 100-149) dan kait efek (`daftarKait`), boleh `petakanAktivitas` ke klip 200-299 miliknya |
| 39.6.6 tubuh `JELMAAN` "menyalin kulit" | menyalin `sosok`/`gaya` dengan `SosokApi.setSosok` + `setBusanaKhusus(1)` (40.4.3) |
| 39.3.1 "ciri terlihat" per jalan | diwujudkan 40.4.3 lewat `SosokApi.setJalan(murid, id)` (bit 1-10 = id 39) |
| **39** murid mandiri | wajib: `setJalan` saat jalan dipilih/berubah, `gerak` untuk kegiatan (klip 1-176 atau 200-299 miliknya lewat C40.G1), `tatap` saat berinteraksi, `setSuasana`, `setLuka`, `setBusanaKhusus` (bepergian ke kota, berkabung), `setUsia` bila 39 memodelkan umur; **tidak pernah** menyetel kulit, `DATA_SKIN` atau model sendiri |

#### 40.7.5 Pembagian kerja di dalam modul (urut, untuk berkas progres)

- **40I:** `Sosok`/`Gaya` + pengemasan → `GerakId` (1-176, nama) + `PropId` (1-88) → `SosokGen` (tabel kebijakan K/U per
  kunci rupa ditulis di Java karena server tidak membaca `assets/`; `cek_sosok.py` 40K membandingkannya dengan
  `resep.json` lewat regex) → field entitas + NBT → rekrut/nama/suara → `SosokApi` → `SosokUsia` → perintah + galeri →
  lang.
- **40K:** `sosok_lib.py` (RNG, tangga, UV) → lembar bagian per kelompok (kulit, wajah, rambut, busana, kubus) dengan
  pratinjau dan Read per kelompok → `resep.json` → cadangan → Java penyusun + cache → model lapisan + `BusanaKatalog` →
  `SosokBusanaModel` (bagian, panel rok, pegas dari `GerakData`) → `WajahModel` → prop 63-87.
- **40G:** `Klip`/`Easing`/`Sampler` → `KatalogTempat` 1-107 (ketik dari 38 + perubahan 40.5.10) → `KatalogSosok` 108-176 + `AktivitasKlip` (40-56)
  → `GerakPengendali` (lapisan, prioritas) → `GerakApplier` + mixin → `ProfilJalan` → `Pegas` → `Tatap`/`Wajah`/`Iseng` →
  `DutyPoses`/`NpcPoses` pengalihan → `SosokPropLayer`.
- **40R:** `SosokDetail` (setelan, LOD) → `SosokRender.ekstrak` → `DiscipleRenderer` → `NpcRenderer` + `KotaGearLayer` →
  D/Liar/Utusan/Penonton → `SettingsRows` → `/sosok`.

#### 40.7.6 Daftar uji (Indonesia; centang di `docs/progres/penampilan-<modul>.md`, salin ke `docs/uji/FASE-40.md` saat finish)

Semua di dunia uji baru dengan op, pada laptop pengguna, setelan Sedang kecuali disebut.

**Identitas**
- [ ] Rekrut 10 murid: kelamin tercampur; `grunt.female.*` hanya pada murid perempuan; nama dari kolam yang benar.
- [ ] `uji penampilan galeri usia warga`: anak setinggi ±1.2 blok dengan kepala besar, perempuan lebih ramping, tua
      membungkuk; F3+B: kotak tabrak anak kecil.
- [ ] `murid usia anak` lalu tunggu 48 hari dunia (`/time add`): murid anak menjadi muda; murid Fondasi tidak menua.
- [ ] Simpan-keluar-masuk: rupa murid sama persis; Prasasti Jiwa: murid yang dibangkitkan berupa sama.
- [ ] NPC kota: Meng Po perempuan, Raja Yama laki-laki, rahib laki-laki gundul, penari gurun perempuan.

**Kulit dan busana**
- [ ] `galeri peran` semua halaman: tidak ada kulit Steve/Alex/Ari dll.; setiap peran punya busana, topi, prop berbeda.
- [ ] `acak 32`: 32 wajah berbeda; ulangi: 32 lain.
- [ ] `galeri murid`: pangkat terlihat dari tepi, hiasan rambut, lencana, jubah luar; Tetua Agung bermahkota teratai.
- [ ] Rancang seragam sekte (36): murid Inti memakai warna, potongan dan lambang punggung baru dalam 1 detik.
- [ ] `galeri dao`: iris ambar (Api), biru pucat (Es), ungu (Petir); tanda Dao di dahi dari Jiwa Baru Lahir.
- [ ] `galeri fisik`: dua pupil emas terlihat pada murid Pupil Ganda dari 2 blok.
- [ ] Kerah silang orang hidup menutup kanan-ke-kiri (panel kiri pemakai di atas); arwah kebalikan.
- [ ] Topi menutup sanggul (tidak ada sanggul menembus helm/futou); ekor kuda tetap terlihat di bawah helm.
- [ ] `/sosok dump` sebuah benih dan `tools/gen_sosok_pratinjau.py --benih` yang sama: PNG identik (bandingkan dengan Read).
- [ ] Pratinjau 40K sudah dibaca semua dan dicatat di `penampilan-40K.md`.

**Animasi**
- [ ] `uji penampilan klip semua`: setiap klip 1-176 diputar tanpa pose rusak (lengan memutar 360°, kepala terbalik).
- [ ] `jalan`: 14 profil berbeda; tua bertongkat mengetuk; anak melompat; perempuan langkah pendek pinggul berayun.
- [ ] `wajah`: 16 strip mata dan 16 strip mulut tampil; kedip acak; bicara mengikuti suku kata.
- [ ] `pegas`: rambut, ekor kuda, lengan lebar, jubah luar, selendang bergerak tertunda dan berhenti halus (tidak bergetar terus).
- [ ] Murid tugas air/kebun/dapur/ternak terlihat sama seperti sebelum 40 (angka sama), dengan prop lama.
- [ ] Murid aktivitas Sekte 13-24 dan Sekte Langit 25-27: pose lama tampil, tanpa klip lain bertumpuk.
- [ ] Murid melakukan teknik (cast) saat bekerja: pose cast menang, lalu kembali ke kerja dengan halus.
- [ ] Duel pemain vs pengembara: pose duel 35 penuh, wajah marah, pegas jalan; setelah duel sikap gagang kembali.
- [ ] Murid perempuan non-bela-diri memberi hormat wanfu; murid pedang perempuan baoquan.
- [ ] Meditasi murid: duduk bersila, napas dalam, mata tertutup, rok terhampar; terobosan berhasil: lengan terangkat,
      tertawa; gagal: memegang dada, batuk darah, berlutut.
- [ ] HP murid < 35 %: berjalan pincang dengan wajah sakit.
- [ ] Murid mandiri 39 (bila 39 sudah ada): aktivitas 40-56 menampilkan klip 40.5.13 (latih jurus, meramu bersila dengan
      kipas dan asap, menulis jimat, tanding bersinkron diawali-diakhiri hormat, tidur/bersandar, berdiri di pedang); tidak ada
      `IllegalArgumentException` dari `SektePoses.register` di log.

**Performa dan kompatibilitas**
- [ ] Ibu kota 38 atau kota lama ramai: `/sosok info` ≤ anggaran; FPS turun < 10 % dibanding Rendah.
- [ ] Setelan Detail animasi Tinggi/Sedang/Rendah/Otomatis berubah langsung; Ekspresi wajah mati → wajah diam.
- [ ] Turnamen 36 dengan 160 penonton: tidak ada penyusunan atlas baru (`/sosok info` antrean 0).
- [ ] Sodium + Iris (paket shader umum): tekstur benar, bayangan NPC ada, tidak ada kedip; shadow pass tanpa wajah/prop.
- [ ] Player Animation Library terpasang + `duel_pal`: animasi PAL pemain normal, murid tetap memakai pose 35.
- [ ] Keluar dunia lalu masuk dunia lain: `/sosok info` atlas 0 sebelum NPC terlihat (tekstur dilepas).
