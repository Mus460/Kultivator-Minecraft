## 35. Duel Pedang Kultivator

Permintaan pengguna (2026-10-22, bahasa Indonesia, mengikat): "Untuk pertarungan, bisakah dibuat benar-benar keren:
duel pedang antar kultivator dengan adegan senjata saling beradu dan percikan api. SANGAT DETAIL juga untuk yang ini."

Bagian ini adalah kontrak implementasi lengkap. Semua nama kelas, field dan metode di bawah adalah nama yang **harus**
dipakai. Yang ditandai "(verifikasi)" sudah dicek polanya di kode tetapi nama persisnya wajib dicocokkan lagi dengan
`butterbot-fabric/reference/mcsrc` atau jar sebelum dipakai. Tidak ada angka di sini yang berasal dari novel; semua angka
adalah desain game (baris Sumber hanya menjelaskan ilham).

---------------------------------------------------------------------------------------------------------------------

### 35.0 Ruang lingkup dan aturan mengikat

1. **Tidak ada blok yang berubah.** Gelanggang, tiang, tali, bekas tebasan, debu, gelombang qi: semuanya entitas sisi
   klien, quad, partikel atau perhitungan geometri. Tidak ada `setBlock`, tidak ada `LightningBolt`, tidak ada proyektil
   vanilla, tidak ada `Explosion`. Mode Kehancuran (DESIGN 22) **tidak** dipicu oleh mekanik duel apa pun.
2. **Server berwenang penuh** atas waktu, jendela tangkis, postur, hasil benturan, kerusakan, taruhan dan pemenang.
   Klien hanya memprediksi tampilan (pose, percikan, suara) dan dikoreksi server.
3. **Tak ada yang mati di duel resmi.** Semua duel resmi berjalan di atas `dev.kultivator.disciple.DuelRules` yang sudah
   ada (batas 10% nyawa, `GRACE_TICKS = 100` setelah selesai). Duel Pedang tidak pernah memanggil `setHealth(0)`.
4. **Vanilla dan Better Combat tidak pernah rusak.** Di luar duel resmi, tombol serang bekerja persis seperti vanilla
   (atau seperti Better Combat bila terpasang). Integrasi Better Combat 3.2.2, Player Animation Library 1.2.8 dan Combat
   Roll 3.0.1 **hanya lewat refleksi** (pola `client/epic/ShaderCompat`), tanpa dependensi kompilasi dan tanpa mixin ke
   kelas mod lain. Bila refleksi gagal: satu baris log, fitur integrasi mati, duel tetap jalan.
5. Efek digambar di klien, diskalakan `EffectQuality`, `EpicBudget`, `FxBudget` dan pengaturan "Partikel" vanilla; aman
   untuk Sodium dan Iris (tanpa post chain, tanpa shader sendiri, tidak menggambar di shadow pass Iris:
   `ShaderCompat.shadowPass()`).
6. Lang hanya di `lang-src/<fragmen>.en_us.json` + `.id_id.json` dengan kunci identik; tiap kunci di tepat satu fragmen
   (35.10). Kode server tidak pernah menyentuh kelas klien. Pertahankan akhir baris tiap file (LF).
7. Bangun hanya `compileJava`, `compileClientJava`, `jar` (`--no-daemon`), dijalankan sekali setelah semua modul;
   agen fitur tidak menjalankan Gradle.
8. Empat modul (S, V, A, U) dengan file yang tidak bertumpuk (35.9). Bagian lain DESIGN tidak diubah oleh bagian ini.

---------------------------------------------------------------------------------------------------------------------

### 35.1 Fakta dari kode (audit 2026-10-22)

**Duel yang sudah ada**
* `disciple/DuelRules` (publik): `begin(LivingEntity a, LivingEntity b, int maxTicks, Listener)`, `dueling(a, b)`,
  `inDuel(e)`, `partnerOf(e)`, `ticks(e)`, `end(either, winner, Reason)`, `cancel(either)`; `Reason { DEFEAT, TIMEOUT,
  LEFT_RING, CANCELLED }`; `END_FRACTION = 0.10F`; `GRACE_TICKS = 100`. Ia menjepit pukulan pasangan di 10% lewat
  `ServerLivingEntityEvents.ALLOW_DAMAGE` (didaftarkan dari `Disciples.init`), menolak duel di alam rahasia / saat `Alam.inRun`.
  `Targeting.isHostileTo`, `DiscipleEntity.mayAttack`, `isInvulnerableTo` dan `DiscipleTechniques.mayHit` menanyakan
  `DuelRules.dueling` lebih dulu. `Breakthrough`, `Ascension`, `ImmortalPerks`, `Abadi`, `Alam` sudah menolak aksi saat
  `DuelRules.inDuel` atau `Sparring.inDuel`.
* `sect/Sparring` (Latih Tanding pemain vs pemain): tantangan 60 s (`ANSWER_TICKS`), hitung mundur 60 tick, 120 s,
  hadiah XP `XP_FRACTION = 0.10` dan pemahaman Dao, `areSparring(a, b)`, `inDuel(p)`; perintah `/kultivasi sekte spar
  <pemain>|accept|decline` (`SectCommands`). Efeknya `FxType.SPARRING` di `client/fx/SectFx`.
* `sect/event/DuelArena` (paket privat): satu laga di prasasti aula, `RING_RADIUS 9`, `OUT_RADIUS 11`, `OUT_TICKS 60`,
  bonus pihak lemah `UNDERDOG_PER_REALM 0.15` maks `0.45`, dua boss bar vanilla.
* `Disciples.spawnUnowned(level, pos, DiscipleData, name, skin, slim, DiscipleEntity.Role.RIVAL, anchor)`: tubuh tanpa
  guru yang hanya melawan pasangan `DuelRules`-nya, tidak pernah disimpan. Pola data di `sect/event/Rivals.body(random,
  step)` (`DiscipleData.fresh`, `setRank(Rank.ofStep(..))`, kualitas Normal).
* `DiscipleEntity`: `setActivity(int, BlockPos)`, `activity()`, `ACTIVITY_SPECTATOR = 8`, `ACTIVITY_DUELLIST = 9`,
  `doHurtTarget` (faktor pukulan murid), `data()`, `soulId()`; `DiscipleAi.CombatGoal extends MeleeAttackGoal`.
  `VoiceDisciples.line(d, "attack"|"attack_alt"|"cheer"|"victory", cooldown)`, `VoiceDisciples.grunt(d,
  "attack"|"hurt"|"death")`, `Voice.say(entity, "grunt.male.attack", "", pitch)`.
* `musuh/RogueCultivatorEntity extends Monster` (`tier()`, `realm()`, `variant()`, `element()`), serangan jarak dekat
  vanilla lewat `doHurtTarget` + `RogueTargets.mayHit`. Penggambar `client/musuh/RogueRenderer` memakai `PlayerModel` dan
  `DiscipleRenderState` tetapi **belum** memanggil `PoseRenderData.extract`.
* `Techniques.addCastBlocker(Function<ServerPlayer, Component>)` (dibaca `TechniqueRunner.cast`), `Mastery.level(data,
  Technique.FLYING_SWORD.id())`, `TechniquePower.MAX_MASTERY`, `FlyingSword.granted(data)`, `FlyingSword.dismount(player,
  data, Reason.TOGGLE)`, `Cultivators.bestSword(player)`, `Qi.trySpend(player, n)`, `Comprehension.grant(player, Dao, n)`,
  `Progression.addXp(player, xp, XpSource.SECT)`, `Advancements.grant(player, name)`, `Halls.hallOf(owner)` →
  `HallView(pos, dim, level, renown, radius, active, name)`, `Halls.inTerritory(owner, level, pos)`,
  `sect.hall.Talents.talents(DiscipleData) & PowerIds.TALENT_SWORD_SENSE`.
* Senjata: `ItemTags.SWORDS`, `ItemTags.SPEARS`, `ItemTags.AXES`, `Items.MACE`, `Items.TRIDENT`; pedang tempaan
  `tempa/ArtifactItem.is(stack)` + `ArtifactItem.form(ArtifactItem.data(stack))` = `"pedang"` (**tidak** ada di tag
  `swords`).

**Pose dan render**
* `technique/spec/dsl/Pose` (enum bersama, **jangan diurutkan ulang**, boleh ditambah di akhir), `client/anim/PoseLibrary`
  (18 kanal: `HX HY LEAN TWIST RAX RAY RAZ LAX LAY LAZ RLX RLZ LLX LLZ LIFT RLY LLY TILT`; ruang model: y ke bawah, depan
  −z; `xRot < 0` mengangkat lengan ke depan; `easeOutBack` overshoot 1.08), `CastPoses.Snapshot(pose, val, w, fromVal,
  fromW, cross, twoHanded)`, `PoseRenderData.extract/pose/form` (kunci `kultivator:cast_pose`), `PoseApplier.apply(PlayerModel,
  AvatarRenderState)` dipanggil `PlayerModelMixin` di TAIL `setupAnim` untuk **setiap** `PlayerModel` (pemain, murid,
  perampok), `PoseApplier.lift/tilt`. `AvatarRenderState.id` = id entitas.
* Rantai transformasi tangan 26.3 (mcsrc): `LivingEntityRenderer` → `setupRotations` (`rotateDegrees(YP, 180 − bodyRot)`)
  → `scale(-1, -1, 1)` → `translate(0, -1.501, 0)`; `PlayerModel.translateToHand` = `root().translateAndRotate` lalu lengan
  (`slim` geser x ±0.5 px); `ModelPart.translateAndRotate` = `translate(x/16, y/16, z/16)` lalu
  `Quaternionf().rotationZYX(zRot, yRot, xRot)`; pivot lengan kanan `(-5, 2, 0)` px; `ItemInHandLayer`:
  `rotateDegrees(XP, -90)`, `rotateDegrees(YP, 180)`, `translate(±1/16, 2/16, -10/16)`; `models/item/handheld.json`
  orang ketiga kanan: rotasi `[0, -90, 55]`, translasi `[0, 4, 0.5]`, skala `0.85`. `DiscipleHeldItemLayer` (pedang 3D):
  `translate(-1/16, 10/16, -1/16)`, `rotateDegrees(XP, 90)`, `scale(0.8)`.
* Lapisan render tambahan: `LivingEntityRenderLayerRegistrationCallback.EVENT` (pola `AnimClient.init`), dipanggil untuk
  setiap `LivingEntityRenderer` termasuk milik mod.

**Sinematik dan efek (client/epic/**)**
* `EpicClock`: σ hanya presentasi; `hitStop(int 1-6)`, `slowmo(realTicks, prio)` (σ 0.35, ≤ 30 tick, jeda antar jendela
  `MIN_GAP = 120`), `bullet(slow, realTicks, prio)`; partikel film menua dengan σ (beku saat hit-stop). Entitas vanilla,
  partikel vanilla, `DeltaTracker` dan tick-rate server **tidak pernah** diperlambat.
* `CameraShake.add(intensity, ticks, at, radius[, climax])` (≤ 2.5°, dijepit 1.2°/frame, setelan Penuh/Ringan/Mati),
  `ImpactFrame.play(f1, f2, f3, full, alphaScale, streaks, seed)` (gerbang `EpicScreen.tryLuminance()` 500 ms,
  fotosensitif), `ScreenFlash.add(rgb, alpha, ticks)`, `TitleCard.full(title, sub, seal, sealRgb, ticks)`,
  `Letterbox.flick(ticks)`, `LensLayers.shot(mask, rgb, seed, light)` dengan `mask = 1 << FilmLens.X.ordinal()`,
  `LensLayers.light(point)`, `MusicCue.play(Cue, seconds, prio)`.
* `CinematicDirector.shoot(Scene, Shots.Shot, kind, prio, safeBeat)` (butuh `scene.own` + `FLAG_CAMERA_OK`; jeda kamera
  400 tick kecuali adegan yang sama), `Shots.twoShot(second, d0, d1, h, s)`, `Shots.ots(target, s)`, `Shots.orbit(r, h,
  sweep, s)`, `Shots.stillAt(center, r, h, side, s)`, `Shots.push(...)`; gerbang keras `ShotGuard` (musuh dalam 16 b,
  cedera asing, dll.). `Scene` dibuat paket-privat; fabrik publik yang ada hanya `EpicDirector.dampakScene` dan
  `testScene`. `EpicDirector.localFlags` menganggap **musuh (`Enemy`) dalam 24 b = tidak aman** (perampok adalah
  `Monster`).
* Partikel Kenney: `FilmParticles.SPARK, FLARE, STAR, SMOKE, FLAME, MAGIC, SLASH, TRACE, TWIRL, CIRCLE, MUZZLE, DIRT,
  SYMBOL`; `FilmParticleProviders.spawnAmbient(type, pos, vel, rgb, size, life, FxBudget.Priority)` (di luar film);
  `SPARK` digambar memanjang menurut kecepatan layar, **tanpa gravitasi, tanpa fisika** (hanya `DIRT` yang berfisika).
  `fx/qi/Qi.spark/glint/petal/groundRing/ring/smoke`, `QuadFx` (`RIBBON` cap 0/4/12, `RING` 4/12/24, `GLOW` 8/24/64,
  `STREAK`, `CRESCENT`), `RibbonQuad`, `epic/render/TrailRenderer`, `DisplayKey` untuk Display entitas sisi klien.
* Suara film (`sound/FilmSounds`, `epic/sound/FilmSound.Cue`): **mono** (boleh diposisikan) `riser_long riser_short
  hum_energy hum_vortex whoosh zap twinkle crunch clang`; **stereo** (hanya untuk diri sendiri, tanpa posisi) `sub_boom bell
  glass punch gong thud rock`; maks 3 isyarat film per tick klien.
* Suara vanilla 26.3 yang dipakai (tipe diverifikasi): `SoundEvent`: `ANVIL_LAND, ANVIL_HIT, ANVIL_PLACE, CHAIN_HIT,
  TRIDENT_HIT, PLAYER_ATTACK_SWEEP, PLAYER_ATTACK_STRONG, PLAYER_ATTACK_CRIT, PLAYER_ATTACK_KNOCKBACK,
  PLAYER_ATTACK_NODAMAGE, PLAYER_ATTACK_WEAK, GRINDSTONE_USE, SMITHING_TABLE_USE, AMETHYST_BLOCK_CHIME,
  AMETHYST_BLOCK_RESONATE, AMETHYST_CLUSTER_BREAK, BELL_BLOCK, BELL_RESONATE, MACE_SMASH_AIR, MACE_SMASH_GROUND,
  HEAVY_CORE_HIT, IRON_GOLEM_ATTACK, BREEZE_DEFLECT, BREEZE_WHIRL, VILLAGER_CELEBRATE, VINDICATOR_CELEBRATE,
  CHERRY_LEAVES_FALL, ELYTRA_FLYING`; `Holder`: `SHIELD_BLOCK, SHIELD_BREAK, ITEM_BREAK, SPEAR_HIT, SPEAR_ATTACK,
  ARMOR_EQUIP_NETHERITE, WIND_CHARGE_BURST, NOTE_BLOCK_BASEDRUM, NOTE_BLOCK_SNARE, NOTE_BLOCK_HAT` (pakai `.value()`).
* Suara: `voice.grunt.male.attack|hurt|death`, `voice.grunt.female.attack|hurt` (kiai Jepang VOICEVOX, `f1_kiai0`...),
  `voice.disciple.<m1..f3>.attack|attack_alt|cheer|victory`.

**Serangan vanilla 26.3**: klik kiri → `Minecraft.startAttack()` (privat, mengembalikan boolean) mengirim
`ServerboundAttackPacket` (server: `handleAttack` → `player.attack(target)`) atau `ServerboundPunchPacket` (server:
`handlePunch` → `swing` + `resetAttackStrengthTicker`). Klik kanan → `Minecraft.startUseItem()`. `Player.attack` memakai
`getAttackStrengthScale(0.5F)`, kritikal, sapuan (`doSweepAttack`) dan **tidak** mereset ticker sendiri.
`ServerPlayer.getLastClientInput()` → `Input(forward, backward, left, right, jump, shift, sprint)`;
`ServerCommonPacketListenerImpl.latency()` (ms, EMA keepalive). Kecepatan entitas ke klien: `entity.needsSync = true`
(pola `DiscipleTechniques`). `LivingEntity.knockback(power, xd, zd, source, damage)`. `EnchantmentHelper.modifyDamage(level,
stack, victim, source, damage)`, `EnchantmentHelper.doPostAttackEffects(level, victim, source)`,
`ItemStack.hurtAndBreak(int, LivingEntity, InteractionHand)`, `DamageSources.playerAttack(player)` / `mobAttack(mob)`.
Komponen `DataComponents.ATTACK_RANGE`, `PIERCING_WEAPON`, `KINETIC_WEAPON`, `WEAPON`, `BLOCKS_ATTACKS`.

**Mod di folder pemain** (`%APPDATA%/.minecraft/versions/SODIUM/mods`, dibaca dari jar dengan `javap`):
* **Better Combat 3.2.2** (`bettercombat`, butuh `player_animation_library >= 1.2.7`). API klien:
  `net.bettercombat.api.client.BetterCombatClientEvents.ATTACK_START` (`Publisher<PlayerAttackStart>`,
  `onPlayerAttackStart(LocalPlayer, AttackHand)`) dan `ATTACK_HIT` (`PlayerAttackHit.onPlayerAttackStart(LocalPlayer,
  AttackHand, List<Entity>, Entity)`); `Publisher.register(T)`; `AttackHand(attack, combo, isOffHand, attributes,
  itemStack)` + `upswingRate()`; `WeaponAttributes.Attack.animation()` (mis. `bettercombat:one_handed_slash_horizontal_right`),
  `upswing()`, `angle()`; `WeaponAttributes.category()`, `isTwoHanded()`; `MinecraftClient_BetterCombat
  .isWeaponSwingInProgress()`, `getUpswingTicks()`, `getSwingProgress()`, `getCurrentAttackHand()`; `CombatFlags`.
  Server BC: paket `C2S_AttackRequest` → BC memanggil `player.attack` per target (melewati `ALLOW_DAMAGE`). BC
  menggambar jejaknya sendiri (`TrailAppearance`, `ParticlePlacement`) dan memutar suara ayunannya sendiri
  (`bettercombat:sword_slash`). Atribut pedang BC: tiga serangan (horizontal kanan, horizontal kiri, tusuk), `upswing 0.5`.
* **Player Animation Library 1.2.8** (`player_animation_library`): `com.zigythebird.playeranim.api.PlayerAnimationFactory
  .ANIMATION_DATA_FACTORY.registerFactory(Identifier, int, PlayerAnimationFactory)`,
  `PlayerAnimationAccess.getPlayerAnimationLayer(Avatar, Identifier)`, `PlayerAnimationController.triggerAnimation(Identifier)`,
  `getBoneWorldPositionPoseStack(String, float, Vec3)`; animasi JSON di `assets/<ns>/player_animations/`. Hanya untuk
  `Avatar` (pemain), bukan murid/NPC.
* **Combat Roll 3.0.1** (`combat_roll`): server `net.combat_roll.api.event.ServerSideRollEvents.PLAYER_START_ROLLING`
  (`onPlayerStartedRolling(ServerPlayer, Vec3)`), `RollInvulnerable.setRollInvulnerableTicks(int)`; tombol R.
* Iris, Sodium, EMF/ETF, Not Enough Animations (dinonaktifkan), Dynamic FPS: tidak ada kait yang dipakai.

---------------------------------------------------------------------------------------------------------------------

### 35.2 Mode duel (permintaan butir 1)

#### 35.2.1 Pasangan yang didukung

| id `DuelIds.PAIR_*` | pasangan | siapa menantang | catatan |
|---|---|---|---|
| `PAIR_PVP = 0` | pemain vs pemain | pemain (layar tantangan, `/kultivasi duel tantang <pemain>`) | butuh aturan permainan `pvp` dan tim vanilla yang mengizinkan (dicek saat menantang, pesan jelas bila ditolak) |
| `PAIR_MURID = 1` | pemain vs murid sendiri | pemain (kartu murid / layar tantangan) | taruhan hanya Tanpa atau Kehormatan; murid tidak pernah kehilangan barang |
| `PAIR_NPC = 2` | pemain vs NPC kultivator | pemain (menatap NPC ≤ 12 b) atau NPC menawarkan | perampok `RogueCultivatorEntity` tier 1-3 di luar serbuan; kultivator pengembara Dunia (DESIGN 34) lewat `DuelistRegistry` (35.7.6) |
| `PAIR_TONTON = 3` | murid vs murid (ditonton) | pemain memilih dua murid sendiri | pemain menjadi penonton dengan kamera penonton; tak ada taruhan barang |
| `PAIR_LATIH = 4` | Latih Tanding lama (`Sparring`) | `/kultivasi sekte spar` seperti sekarang | mekanik benturan aktif otomatis bila kedua klien punya modul; aturan BEBAS, tanpa taruhan, tanpa intro film; hadiah dan alur `Sparring` tidak berubah |
| `PAIR_UJI = 5` | pemain vs Boneka Duel | `/kultivasi uji duel boneka ...` | tubuh RIVAL dari `Disciples.spawnUnowned` |

Mekanik benturan (35.3) juga bekerja **tanpa duel resmi** melawan mob (35.3.13, "lapisan umum").

#### 35.2.2 Alur tantangan (mesin keadaan `DuelSession.Phase`)

| fase | lama (tick) | apa yang terjadi | keluar ke |
|---|---|---|---|
| `INVITE` | ≤ 1200 (`DuelConfig.answerSeconds = 60`) | penantang mengirim `DuelChallenge`; sasaran mendapat `DuelChallengeOffer` (pop-up `ChallengeOfferToast` + baris chat dengan chip `[Terima]` `[Tolak]`); NPC/murid menjawab dalam 20-40 tick lewat `DuelistBrain.answer` | `GATHER` / batal |
| `GATHER` | ≤ 100 | server menghitung pusat gelanggang (titik tengah kedua pihak, digeser ke tanah datar terdekat lewat pemeriksaan blok **baca-saja**) dan dua **tanda** 6 b terpisah saling berhadapan; klien menggambar panah tinta ke tanda; NPC berjalan ke tanda | `INTRO` (keduanya ≤ 1.5 b dari tanda atau waktu habis) |
| `INTRO` | 100 (film) atau 40 (tanpa film) | keduanya **ditahan** (modifier `kultivator:duel_hold` −100% `MOVEMENT_SPEED`, AI ditahan `DuelistGoal.HOLD`), kerusakan antar mereka dibatalkan; sinematik intro 35.4.8 | `COUNTDOWN` |
| `COUNTDOWN` | 60 | 三 二 一 di HUD, tabuh `NOTE_BLOCK_BASEDRUM`; boleh mengambil sikap kuda-kuda (pose) tetapi belum boleh menyerang | `FIGHT` |
| `FIGHT` | ≤ `rules.timeLimitTicks` (bawaan 3600 = 180 s) | `DuelRules.begin(a, b, timeLimit, listener)` dipanggil di tick pertama; seluruh mekanik 35.3 | `FINISHER` / `ENDED` |
| `FINISHER` | 60 | keduanya ditahan (tanpa kerusakan: masa rahmat DuelRules), sinematik penutup 35.4.9 | `ENDED` |
| `ENDED` | 1 | taruhan diselesaikan, catatan, hadiah, `DuelResult` dikirim, semua modifier dilepas, murid penonton dikembalikan | — |

Batal otomatis (`DuelRules.Reason.CANCELLED`, taruhan dikembalikan utuh): keluar server, ganti dimensi, mati di luar
duel, mode kreatif/penonton, jarak > 48 b saat `GATHER`, ada musuh lain (`Enemy` bukan pasangan) dalam 24 b saat `GATHER`
atau `INTRO` (pesan "Tempat ini tidak aman untuk duel"), terobosan/tribulasi dimulai, `Alam.inRun`.

Hanya satu sesi per entitas (`DuelSessions.byEntity`), maks `DuelConfig.maxSessions = 16` per server. Penantang tidak bisa
menantang lagi selama 10 s setelah ditolak oleh sasaran yang sama (anti-spam).

#### 35.2.3 Gelanggang (berbasis entitas, tanpa perubahan blok)

* Data server `DuelRing(ServerLevel level, Vec3 center, float radius)`; jari-jari 6 / 9 / 12 b (bawaan 9, sama dengan
  `DuelArena.RING_RADIUS`). Garis keluar = `radius + 1.5`.
* **Keluar gelanggang**: di luar garis keluar selama 60 tick berturut-turut (`DuelConfig.ringOutTicks`, sama dengan
  `DuelArena.OUT_TICKS`) → `DuelRules.end(pelanggar, null, LEFT_RING)`. HUD menghitung mundur merah 3-2-1; pada detik
  pertama di luar, tali gelanggang klien berpendar merah.
* Server **tidak** memaksa posisi pemain (tidak ada teleport, tidak ada dorongan balik); NPC memakai navigasi mereka.
* Visual (V, sisi klien saja): 8 tiang `ItemDisplay` (model `kultivator:duel_post` dari A) dibuat dengan
  `EntityType.create(clientLevel, LOAD)` ber-id negatif seperti `EpicDisplays` dan dikunci lewat `DisplayKey`; tinggi
  tiap tiang dicari dengan `level.clip` ke bawah 4 b; tali merah di antara tiang (`DuelRopeRenderer`, quad melengkung
  dengan kendur 0.25 b, bergetar saat benturan besar); lingkaran tinta di tanah (decal 35.4.5) dan delapan kertas jimat
  berkibar (`Qi.petal` putih). Penonton dalam 64 b ikut melihat. Tak ada entitas server, jadi tak ada yang tersimpan di
  chunk.
* **Arena sekte**: bila penantang adalah pemilik aula dan pusat gelanggang ≤ 24 b dari `Halls.hallOf(owner).pos()`, pusat
  pindah ke prasasti dan hingga 12 murid sendiri yang menganggur (bukan Hu Fa, bukan bertugas lasting) dijadikan penonton
  (`setActivity(ACTIVITY_SPECTATOR, titik di radius+3)`, aktivitas lama disimpan di `DuelSession.restoreActivity` dan
  dikembalikan di `ENDED`). Murid penonton bersorak (35.6.3).

#### 35.2.4 Taruhan dan penitipan

| `StakeKind` | isi | aturan |
|---|---|---|
| `TANPA` | — | bawaan untuk murid, tonton, uji |
| `BATU_ROH` | batu roh `HartaItems.SPIRIT_STONE_LOW/MID/HIGH/SUPREME`, mutu + jumlah 1-64 | kedua pihak memasang mutu dan jumlah yang **sama**; NPC memasang dari profilnya (`DuelistProfile.stoneStake`) |
| `GULUNGAN` | satu `JadeSlips.JADE_SLIP` dengan komponen `JadeSlips.TECHNIQUE` | masing-masing memasang satu slip (boleh beda; sasaran melihat nama teknik penantang sebelum menerima); pemenang menerima slip lawan. Slip Dewa tidak ada (JadeSlips menolaknya) |
| `KEHORMATAN` | poin Kehormatan (35.2.6) | K-faktor ganda |

* **Penitipan** (`DuelEscrow`, `SavedData` global `kultivator:duel_escrow` lewat `server.getDataStorage()`): barang
  diambil dari inventori di awal `INTRO` (setelah kedua pihak terbukti memilikinya), disimpan sebagai `ItemStack`
  berkodek. Dikembalikan bila `CANCELLED`, diserahkan ke pemenang bila diputuskan. Penerima offline atau inventori penuh →
  antrean `pending` per UUID, diberikan saat masuk / tiap 100 tick bila ada ruang; setelah 7 hari permainan tanpa ruang,
  dijatuhkan di kaki pemilik (`ItemEntity`). Server mati di tengah duel → saat `SERVER_STARTED` semua penitipan aktif
  dikembalikan ke pemilik asal (tak pernah hilang, tak pernah ganda).
* Murid tidak pernah mempertaruhkan barang. NPC kalah menyerahkan taruhannya; NPC menang mengambil taruhan pemain (NPC
  mengantonginya; bila NPC kemudian dikalahkan biasa, barang itu ikut jatuh lewat `DuelistProfile.carried`).

#### 35.2.5 Aturan (`DuelRuleSet`, rekaman tak berubah)

`DuelRuleSet(RealmRule realm, JurusRule jurus, WinRule win, int radius, int timeLimitTicks, boolean introFilm,
StakeKind stake, int stakeGrade, int stakeCount, String slipSpec, boolean crowd)`.

**Selisih ranah** (`RealmRule`):

| aturan | arti | angka |
|---|---|---|
| `BEBAS` | status asli; bonus pihak lemah seperti `DuelArena` | kerusakan pihak lebih rendah ×(1 + 0.15 × selisih ranah), maks ×1.45 |
| `HANDICAP` | status asli; pihak lebih tinggi dibebani | per 1 ranah selisih: kerusakan postur yang ia berikan ×(1 − 0.08), min ×0.6; per 2 ranah: jendela tangkis biasa −1 tick (min 3), jendela sempurna tetap 2 tick |
| `TEKAN_RANAH` | "Tekan Ranah": keduanya bertarung seolah sama | kerusakan = bagian dari nyawa maks korban (35.3.5), mengabaikan zirah, tipe kerusakan `kultivator:duel` (`bypasses_armor`); postur dalam bagian dari postur maks korban; jendela waktu sama |

Saran UI: selisih ≥ 2 ranah → layar tantangan memilih `TEKAN_RANAH` lebih dulu; selain itu `BEBAS`.

**Jurus** (`JurusRule`): `PEDANG_SAJA` (bawaan; semua teknik diblokir lewat `Techniques.addCastBlocker` dengan pesan
"Duel pedang: hanya bilah dan qi pedang"; serangan qi 35.3.10 tetap boleh), `JURUS_BEBAS` (teknik boleh; kerusakan tetap
dijepit DuelRules). Selama `INTRO`, `COUNTDOWN`, `FINISHER` semua teknik diblokir.

**Kemenangan** (`WinRule`): `SEPULUH_PERSEN` (bawaan DuelRules: pukulan yang akan membawa ke bawah 10%) dan
`TEBASAN_PENENTU` (bawaan **nyala** bersamaan: Tebasan Penentu 35.3.9 langsung memenangkan duel). Juga: keluar
gelanggang (`LEFT_RING`), menyerah, waktu habis (`TIMEOUT`: bagian nyawa lebih tinggi menang; seri bila beda < 2% →
`DuelResult.reason = SERI`, taruhan kembali).

**Menyerah**: `/kultivasi duel menyerah`, tombol "Menyerah" di HUD duel (tahan 1 s), atau bersujud (jongkok 2 s tanpa
bergerak saat `FIGHT`). Server: `DuelRules.end(penyerah, lawan, DEFEAT)` dengan `DuelResult.reason = MENYERAH`. NPC menyerah
menurut gaya (35.7.2) bila bagian nyawanya < `profile.surrenderAt` dan tak ada taruhan barang.

**Campur tangan**: kerusakan dari pihak ketiga (pemain lain, peliharaan, murid orang lain) ke peserta selama `FIGHT`
dibatalkan dengan pesan kepada pelaku "Jangan ikut campur dalam duel kehormatan". Monster liar tidak dibatalkan; bila
monster liar melukai peserta, HUD mengusulkan "Tunda duel" (kedua pemain menekan → `CANCELLED`).

#### 35.2.6 Hadiah, Kehormatan, catatan

* `DuelRecords` (`SavedData` global `kultivator:duel_records`): per UUID (pemain dan `soulId` murid) `wins, losses,
  draws, perfects, deathblows, binds won, maxChain, honour (mulai 1000)`, 8 lawan terakhir dengan waktu permainan.
* Kehormatan gaya Elo: `E = 1 / (1 + 10^((Rb − Ra) / 400))`, `Ra' = Ra + K × (S − E)`, `K = 24` (×2 dengan taruhan
  Kehormatan); NPC memakai `profile.honour`; murid tidak mengubah Kehormatan guru. Pasangan yang sama hanya dihitung sekali
  per 10 menit permainan (seperti `Sparring.REWARD_COOLDOWN_TICKS`).
* Gelar (tampil di kartu judul intro dan layar duel): < 900 Pedang Berkarat, 900-1099 Pendekar Pedang, 1100-1299
  Pedang Ternama, 1300-1499 Pedang Tanpa Tanding, ≥ 1500 Santo Pedang.
* Hadiah kemenangan (sekali per pasangan per 10 menit, hanya untuk pertarungan nyata: ≥ 15 s dan lawan kehilangan ≥ 40%
  nyawa atau postur jebol sekali): XP `rank.xpRequired() × 0.08` lewat `Progression.addXp(winner, xp, XpSource.SECT)`,
  `Comprehension.grant(winner, Dao.SWORD, 0.6 + 0.2 × perfects)` maks 2.0. Kalah dengan ≥ 3 tangkisan sempurna:
  `Comprehension.grant(loser, Dao.SWORD, 0.3)` ("belajar dari kekalahan").

#### 35.2.7 Hubungan dengan sistem lama

* `Sparring` tidak diubah. `Fighters` memperlakukan pasangan `Sparring.areSparring(a, b)` sebagai konteks `PAIR_LATIH`
  (benturan, postur, percikan aktif; intro dan taruhan tidak).
* `sect/event/DuelArena` (turnamen, Perang Seratus Dinasti, Alam Hampa) tidak diubah; karena ia memakai `DuelRules`, laga
  turnamen otomatis mendapat lapisan benturan (`DuelContext.TURNAMEN`, tanpa intro, tanpa taruhan; HUD postur tetap).
* Tanding dengan Guru (`disciple/path/Surpass`) sama: `DuelContext.GURU`.

---------------------------------------------------------------------------------------------------------------------

### 35.3 Mekanik inti benturan (permintaan butir 2)

#### 35.3.1 Kelas senjata (`dev.kultivator.duel.clash.WeaponClass`, bersama)

Penentu: `WeaponClass.of(LivingEntity)` membaca tangan utama: `ArtifactItem.is` + bentuk `pedang` → `PEDANG`;
`kultivator:pedang_latih` → `PEDANG`; `ItemTags.SWORDS` → `PEDANG` (atau `PEDANG_BERAT` bila klien melaporkan BC
`isTwoHanded()` / kategori `claymore`, `katana` → tetap `PEDANG`); `ItemTags.SPEARS`, `Items.TRIDENT` → `TOMBAK`;
`ItemTags.AXES` → `KAPAK`; `Items.MACE` → `GADA`; selain itu `TANGAN_KOSONG`. Mob vanilla memakai senjata yang
dipegangnya (zombi berpedang, piglin, wither skeleton, vindicator berkapak).

| kelas | tebas ringan (windup) | berat: isi min / penuh, lalu ayun | jangkauan | postur ringan / berat | bisa menangkis bilah | warna percik dasar |
|---|---|---|---|---|---|---|
| `PEDANG` | 6 | 6 / 16, +4 | 3.0 | 14 / 30 | ya | `0xFFE7B0` |
| `PEDANG_BERAT` | 8 | 8 / 18, +5 | 3.3 | 20 / 42 | ya | `0xFFD08A` |
| `KAPAK` | 8 | 8 / 16, +5 | 2.9 | 18 / 40 (berat mengabaikan 30% blok) | ya (jendela −1) | `0xFFC070` |
| `TOMBAK` | 7 (tusuk) | 6 / 16, +4 | 3.8 atau `ATTACK_RANGE` | 12 / 28 | ya (jendela −1) | `0xFFF0C8` |
| `GADA` | 9 | 10 / 20, +5 | 2.8 | 24 / 50 (berat = pemecah pertahanan) | tidak (hanya blok) | `0xFFB060` |
| `TANGAN_KOSONG` | 4 | 4 / 10, +3 | 2.5 | 6 / 14 | hanya kepalan | `0xFFFFFF` (tanpa percik logam, hanya debu qi) |

Ranah mempercepat: windup ringan −1 tick pada Jiwa Baru Lahir ke atas, −2 pada Kenaikan Abadi ke atas, minimum 4 (berlaku
sama untuk pemain, murid dan NPC; `HANDICAP` tidak menghapusnya). Pemulihan: ringan 5 tick, berat 8, meleset +3.
Rangkaian: setelah tebas ringan, tebas ringan berikutnya boleh dimulai 3 tick setelah impak (maks 3 beruntun, arah
bawaan berganti `KANAN → KIRI → ATAS_KANAN`).

#### 35.3.2 Masukan pemain

Setelan klien `duel_input` (`InputMode`): `OTOMATIS` (bawaan), `KLASIK`, `IKUTI_BC`, `VANILLA`.
`OTOMATIS` = `IKUTI_BC` bila Better Combat terpasang, selain itu `KLASIK` di dalam duel resmi dan `VANILLA` di luar duel.

| aksi | `KLASIK` (duel resmi) | `IKUTI_BC` | `VANILLA` (di luar duel) |
|---|---|---|---|
| tebas ringan | ketuk klik kiri; klien membatalkan `startAttack` (mixin U) dan mengirim `DuelInput SWING` | BC mengayun sendiri; `ATTACK_START` → `DuelInput BC_SWING` | serangan vanilla biasa (tak diubah) |
| arah | gerak tetikus 150 ms terakhir (35.3.3) | dari nama animasi BC (35.3.14) | — |
| tebas berat | tahan klik kiri ≥ 6 tick lalu lepas | tidak ada (BC tak punya tahan); berat = serangan ke-3 kombo BC atau BC `two_handed` | — |
| tusuk | jongkok + klik kiri | animasi `*_stab` BC | — |
| tangkis | ketuk klik kanan (memegang kelas bilah, tangan kiri kosong atau tanpa kegunaan) atau tombol `parry` | tombol `parry` (klik kanan dipakai BC untuk tangan kiri bila dua senjata; bila tangan kiri kosong klik kanan tetap tangkis) | klik kanan sama dengan KLASIK **hanya bila** crosshair tidak menunjuk blok dalam jangkauan (pintu, peti tetap terbuka) dan setelan "Tangkis di luar duel" nyala |
| jaga (blok) | tahan klik kanan / `parry` ≥ 5 tick | sama | sama (gerak −30% saat jaga) |
| tipuan | `parry` saat windup sendiri sebelum impak−3 | tombol tipuan BC (feint, tanpa ikatan bawaan) diteruskan bila terdeteksi; atau `parry` | — |
| dorong saat kunci bilah | klik kiri berulang (maks 10/s dihitung) | sama | sama |
| lepas tiba-tiba (kunci) | `parry` | sama | sama |
| menghindar | Combat Roll (R) bila ada; bila tidak, `jongkok` diketuk sambil bergerak = Langkah Bayangan (klien memberi dorongan 0.55 b/tick sendiri) | sama | sama |
| Tebasan Penentu | serang lawan yang terhuyung (tanda 決) | sama | sama |
| menyerah | tahan tombol `parry` + jongkok 2 s, atau tombol HUD | sama | — |

Tombol baru (kategori "Kultivator", dekat WASD sesuai DESIGN 33.4): `key.kultivator.parry` bawaan **tidak diikat**
(klik kanan sudah menjadi tangkis; untuk pemain BC dua senjata disarankan tombol samping tetikus atau **C**, disebut
dalam Kitab Panduan), `key.kultivator.duel_camera` bawaan tidak diikat (siklus kamera duel). Tidak memakai R (Combat
Roll, Iris), G, V, Z, B, H, M.

Masukan dikirim sebagai `DuelInput` (35.8.1) paling banyak 24 pesan per detik; tangkis tidak pernah membatalkan masukan
vanilla lain kecuali `startUseItem` saat syarat di atas terpenuhi.

#### 35.3.3 Arah tebasan (`SwingDir`, bersama) dan jenis (`SwingKind`)

`SwingDir`: `ATAS` (dari atas), `ATAS_KANAN`, `KANAN` (dari kanan), `BAWAH_KANAN`, `BAWAH` (naik dari bawah),
`BAWAH_KIRI`, `KIRI`, `ATAS_KIRI`, `TUSUK`, `PUTAR`. "Kanan" = sisi kanan **penyerang**.

Pemilihan arah KLASIK (klien, U): jumlah `Δx, Δy` tetikus 150 ms terakhir sebelum klik (dari `MouseHandler`, setelah
sensitivitas); `|Δ| < 6 px` → arah rangkaian bawaan; selain itu sudut `atan2(-Δy, Δx)` dibagi 8 sektor 45° (atas layar =
`ATAS`, gerak ke kiri = tebasan **dari kanan** = `KANAN`, karena bilah bergerak ke kiri). Jongkok → `TUSUK`. Tiga tebas
ringan beruntun ke arah sama → yang ketiga menjadi `PUTAR` bila memegang `PEDANG_BERAT`/`KAPAK`.

`SwingKind`: `RINGAN`, `BERAT`, `TUSUK`, `QI` (berat penuh + qi), `BALIK` (serangan balik saat Celah), `PENENTU`,
`TERBANG` (pedang pendamping 35.3.11), `BC` (dari Better Combat, kekuatan dibaca dari kombo).

`SwingDir.opposes(SwingDir other)`: true bila arah keduanya bersilangan saat berhadapan: `KANAN↔KANAN`, `KIRI↔KIRI`
(dari sisi kanan masing-masing berarti bilah bertemu di tengah), `ATAS↔ATAS`, `ATAS_KANAN↔ATAS_KANAN`,
`ATAS_KIRI↔ATAS_KIRI`, `BAWAH↔ATAS` dan sebaliknya; `TUSUK` hanya `opposes` `TUSUK`. Dipakai untuk kunci bilah (35.3.7).

#### 35.3.4 Model waktu server

* Jam: `ClashClock.now()` = `MinecraftServer.getTickCount()` (20 Hz, 50 ms). Semua jendela dalam tick.
* **Kompensasi latensi** per masukan: `comp = clamp(round(latency_ms / 50), 0, DuelConfig.maxRewindTicks = 4)` dengan
  `latency` dari `connection.latency()` (keepalive EMA, tak bisa dipalsukan tanpa benar-benar lag). `latency` adalah RTT,
  jadi menekan tepat saat impak **terlihat** di layar bertahan dinilai benar (pertahanan diuntungkan, dibatasi 200 ms).
  Waktu efektif masukan `tEff = tiba − comp`, tak pernah lebih awal dari masukan sebelumnya pemain itu. NPC: `comp = 0`.
* Ayunan: `start = tiba − min(comp, 2)` (penyerang hanya dapat 2 tick), `impact = start + windup`. Hasil diputuskan pada
  tick `resolveAt = impact + compBertahan + 1` (tunggu tangkisan yang masih di jalan). Satu pemain: komp 0 → hasil di
  impak + 1. Klien bertahan memprediksi hasil sendiri (35.8.2) sehingga terasa instan.
* Jendela (relatif `impact − tEff` tangkisan):

| nama | konstanta `ClashTiming` | nilai | arti |
|---|---|---|---|
| tangkis | `PARRY_WINDOW` | 0-5 | `DEFLECT` |
| sempurna | `PERFECT_WINDOW` | 0-1 (2 tick = 100 ms) | `PERFECT` |
| tangkis acak | `SPAM_LOCK` | 8 | tekanan tangkis yang gagal (tak ada serangan dalam jendelanya) membuat 8 tick berikutnya hanya punya jendela 0-2 dan tanpa sempurna |
| benturan | `CLASH_WINDOW` | ≤ 2 | dua impak dalam 2 tick, saling menghadap ≤ 60°, jarak ≤ jangkauan terpendek + 0.6 |
| hadap jaga/tangkis | `GUARD_ARC` | ≤ 100° | arah pandang bertahan ke penyerang (horizontal); dari belakang tak bisa ditangkis |
| tipuan | `FEINT_LAST` | sebelum impak−3 | batas membatalkan ayunan sendiri |
| injak pedang | `MIKIRI_WINDOW` | 0-4 | melawan Tusukan Maut: tangkis + `Input.forward` |
| celah | `CELAH_TICKS` | 10 | setelah sempurna / menang kunci / postur lawan jebol |
| terhuyung | `STAGGER_TICKS` | 30 | postur jebol |
| hentak balik | `RECOIL_LIGHT/HEAVY` | 6 / 10 | penyerang yang ditangkis sempurna tak bisa bertindak |
| rantai | `CHAIN_GAP` | 30 | pertukaran berikutnya dalam 30 tick menambah rantai |

#### 35.3.5 Resolusi pertukaran (`Exchange.resolve`)

Untuk tiap ayunan X → Y pada `resolveAt`, urutan pemeriksaan (yang pertama cocok menang):

1. **Benturan**: Y punya ayunan sendiri ke X dengan impak dalam `CLASH_WINDOW` → keduanya diputus bersama sekali
   (`Exchange.pair`). Ringan-ringan → `CLASH` (keduanya hentak 4 tick, postur +8). Berat-ringan → `CLASH_MENANG`: sisi
   ringan postur +0.6 × berat penyerang dan hentak 8, sisi berat +4, tak ada kerusakan. Berat-berat, atau ringan-ringan
   dengan `opposes` dan kekuatan sama → `BIND` (35.3.7). Tusuk vs tusuk berhadapan lurus (≤ 15°) → `CLASH` khusus
   "ujung bertemu ujung" (percikan kecil, suara nyaring).
2. **Hindar**: Y punya detik kebal Langkah Bayangan / Combat Roll (6 tick) → `EVADE` (tanpa percik, desau). Sapuan Rendah
   dan Y di udara ≥ 0.4 b di atas tanah → `EVADE`.
3. **Tangkis**: tangkisan Y dengan `impact − tEff` dalam jendela dan Y menghadap → `PERFECT` atau `DEFLECT` (aturan
   berbahaya di 35.3.8). `TANGAN_KOSONG` tak bisa menangkis bilah: diperlakukan sebagai jaga.
4. **Jaga**: Y menahan jaga dan menghadap → `BLOCK`; bila postur Y akan penuh → `GUARD_BREAK`.
5. **Kena**: uji busur (35.5.3, `HitTest.sweep`) mengenai kotak tubuh Y → `HIT`; selain itu `WHIFF`.

Postur dan kerusakan per hasil (L = postur ringan kelas penyerang, H = berat; untuk korban dengan postur maks 100):

| hasil | postur bertahan | postur penyerang | kerusakan nyawa | lainnya |
|---|---|---|---|---|
| `HIT` | +0.5 × L | 0 | penuh | hentak korban 4 tick |
| `BLOCK` | +L (berat: +H) | 0 | 0 (BEBAS: 10% sebagai "kikis" bila postur > 70%) | gerak bertahan −30% 6 tick |
| `DEFLECT` | +0.4 × L | +0.6 × L | 0 | percik terarah |
| `PERFECT` | 0 | +1.4 × L (berat ×1.4 × H) | 0 | penyerang hentak 6/10 tick, bertahan dapat Celah |
| `CLASH` | +8 / +8 | | 0 | keduanya hentak 4 |
| `GUARD_BREAK` | postur penuh → terhuyung | 0 | 0 | 35.3.8 |
| `EVADE` | 0 | 0 | 0 | — |

Pengali postur: pemahaman Dao Pedang ≥ 50 ×1.1; ayunan `QI` ×1.25; `BALIK` ×1.3; `HANDICAP` sesuai tabel 35.2.5; Tubuh
Dao (bertahan) ×0.9; `TEKAN_RANAH`: nilai di atas dikali `postureMaxKorban / 100` (bagian sama).

Kerusakan nyawa `HIT`:
* `BEBAS`/`HANDICAP`, pemain: `ClashDamage.apply` menghitung `base = attr ATTACK_DAMAGE × kindMul` (ringan 1.0, berat
  1.6, tusuk 1.2, qi ×1.25, balik ×1.3, penentu 2.5), `EnchantmentHelper.modifyDamage(level, weapon, target, source,
  base)`, kritikal ×1.5 bila pemain jatuh (aturan vanilla), lalu `target.hurtServer(level,
  level.damageSources().playerAttack(player), amount)`, `EnchantmentHelper.doPostAttackEffects`, `weapon.hurtAndBreak(1,
  player, InteractionHand.MAIN_HAND)`, `knockback(0.35, ...)`. **Tidak** memakai `Player.attack` agar sapuan vanilla tak
  mengenai penonton. Mob/murid/NPC: `mob.doHurtTarget(level, target)` dengan modifier transien
  `kultivator:duel_kind` (`ADD_MULTIPLIED_TOTAL kindMul − 1`) untuk satu pukulan (pola `DiscipleEntity.MELEE_FACTOR_ID`).
* `TEKAN_RANAH`: `target.hurtServer(level, duelSource(attacker), share × target.getMaxHealth())`, `share`: ringan 0.07,
  berat 0.12, tusuk 0.09, qi 0.10 (+5% dari pengali qi), balik 0.10, penentu 0.30, gelombang qi 0.08.
* Semua kerusakan resolusi ditandai `ClashDamage.RESOLVING` (ThreadLocal) agar lolos dari pembatalan 35.3.14.

#### 35.3.6 Postur ("Postur", gaya Sekiro)

* Maks `Posture.max(entity) = 100 + 8 × realm + 2 × stage` (+15% Tubuh Dao; Kenaikan Abadi +3 per tahap). NPC dari
  peringkatnya; mob 35.3.13.
* Bertambah oleh tabel 35.3.5; berkurang (pulih) setelah 30 tick tanpa tambahan: `0.25 + 0.75 × bagianNyawa` per tick ×
  1.5 saat menahan jaga × 0 saat terhuyung. Nyawa rendah = pulih lambat (seperti Sekiro).
* Penuh → **Postur Jebol**: `STAGGER_TICKS = 30` (tak bisa bertindak, gerak −90%, pose `TERHUYUNG`), tanda 決 di atas
  kepala untuk penyerang dalam 6 b; setelah terhuyung postur kembali ke 50%.
* Disimpan hanya di memori (`Fighter.posture`), disinkronkan `DuelFighterSync` (×10 dalam short).

#### 35.3.7 Kunci Bilah (bind)

* Mulai: hasil `BIND` (35.3.5) dan jarak antara kedua pihak ≤ 2.2 b (lebih jauh → `CLASH`). Keduanya ditahan
  (`kultivator:duel_hold`), arah pandang NPC dikunci ke lawan, pemain bebas menoleh tetapi pose terkunci.
* `Bind.pressure` −100..100 (positif = A unggul). Tiap tick: `pressure += 2 × (F_A − F_B)`, `F = dorongan_per_tick ×
  (1 + 0.04 × realm) × gayaMul × (0.5 + sisaPosturBagian)`; dorongan pemain = jumlah `BIND_PUSH` (≤ 10/s dihitung,
  lebih dari itu dibuang); NPC = 35.7.
* **Lepas tiba-tiba**: menekan `parry` saat lawan mendorong ≥ 3 kali dalam 3 tick terakhir → lawan tersandung (postur
  +35, hentak 12), yang melepas mendapat Celah. Salah waktu (lawan tidak sedang mendorong) → yang melepas kalah kunci.
* Selesai: `|pressure| ≥ 100` → pemenang; yang kalah postur +30, hentak 12 tick, didorong 1.2 b (`knockback(0.6)`,
  `needsSync`); pemenang mendapat Celah. 40 tick tanpa pemenang → `BIND_LEPAS`: keduanya terpental 1.5 b dan postur +10.
* Disinkronkan `DuelBindSync` tiap 2 tick (tekanan, sisa tick) untuk meteran yin-yang HUD dan getaran pose.

#### 35.3.8 Tipuan, pemecah pertahanan, serangan berbahaya, Celah, rantai

* **Tipuan**: `parry` saat windup sendiri sebelum `impact − 3` → ayunan dibatalkan (`DuelSwing` bendera `FEINT_CANCEL`),
  postur sendiri +6, ayunan berikutnya windup −1 tick bila dimulai dalam 6 tick. Maks satu tipuan per 20 tick; tidak untuk
  `PENENTU`, `BALIK`, berbahaya. Lawan hanya melihat windup biasa lalu pembatalan (tak ada bendera lebih awal).
* **Serangan berbahaya** (bendera `PERILOUS_*`, ditandai 危 "Bahaya" merah di atas kepala sejak awal windup, minimal 8
  tick sebelum impak):
  * `GUNUNG` (berat penuh `ATAS`): tak bisa dijaga (jaga → `GUARD_BREAK`), bisa `DEFLECT`/`PERFECT`.
  * `MAUT` (berat penuh `TUSUK`): jaga dan `DEFLECT` biasa tembus (`HIT`); hanya `PERFECT` atau **Injak Pedang**
    (tangkis dalam `MIKIRI_WINDOW` sambil `Input.forward`): penyerang postur +45, terhuyung bila penuh, pose `INJAK`.
  * `SAPU` (berat penuh `BAWAH`): tak bisa dijaga atau ditangkis; lompat atau menghindar. Lompat lalu serang di udara
    dalam 10 tick → ayunan `ATAS` mendapat postur ×1.3 ("Tebasan Langit").
* **Pemecah pertahanan**: `BLOCK` yang membuat postur penuh, `GUNUNG` yang dijaga, berat `GADA` yang dijaga → `GUARD_BREAK`
  = postur jebol + jaga dilepas paksa 20 tick.
* **Celah** (`CELAH_TICKS = 10`): tebas ringan berikutnya menjadi `BALIK` (windup 3, kerusakan ×1.3, postur ×1.3);
  lawan hanya bisa menghentikannya dengan `PERFECT`.
* **Rantai Pertukaran**: hasil `DEFLECT`, `PERFECT`, `CLASH`, `BIND*` dalam `CHAIN_GAP` dari pertukaran sebelumnya menambah
  `Fighter.chain` (keduanya); `HIT`/`WHIFF` lebih dari 30 tick memutus. HUD menulis 一 二 三 ... 十. Rantai 4 → momen
  "Tarian Pedang" (35.4.6), rantai 6 → sorak penonton, rantai 10 → pencapaian `duel_chain_10`.

#### 35.3.9 Tebasan Penentu

Menyerang lawan yang terhuyung (postur jebol) dalam 30 tick: ayunan menjadi `PENENTU` (windup 4, tak bisa ditangkis atau
dijaga, hanya bisa dihindari bila terhuyung habis lebih dulu). Duel resmi dengan `TEBASAN_PENENTU`: kemenangan langsung
(`DuelRules.end(korban, penyerang, DEFEAT)`, nyawa korban diturunkan ke max(10%, nyawa − 30%)). Tanpa aturan itu:
kerusakan 2.5× (BEBAS) atau 30% (TEKAN). Selalu memicu sinematik penutup bila duel berakhir.

#### 35.3.10 Serangan qi, gelombang qi pedang, warna Dao

* Berat penuh dengan qi ≥ `6 + 3 × realm` (pemain `Qi.trySpend`; murid/NPC dari qi datanya) → `SwingKind.QI`: bilah
  berpendar warna `Dao.color()` (tanpa Dao: `Element.color()` akar; NPC: `profile.color`), kerusakan dan postur ×1.25.
  Warna dikirim di `DuelSwing.qiRgb`.
* Ayunan `QI` arah `ATAS`, `KANAN`, `KIRI`, `ATAS_*` sambil berlari → **Gelombang Qi Pedang** (`QiWave`): bulan sabit
  maya (tanpa entitas) bergerak 1.0 b/tick sejauh `10 + realm` b, lebar 2.4 b; tiap tick menguji makhluk di jalurnya
  (pasangan duel lewat `DuelRules.dueling`, di luar duel lewat `Targeting.isHostileTo`); kerusakan `0.6 × ATTACK_DAMAGE +
  realm` (BEBAS) atau 0.08 (TEKAN), postur 24; tidak menembus blok padat (dihentikan `level.clip` per tick, baca saja).
* Melawan gelombang: `PERFECT` → **Pantulan Qi** (gelombang berbalik ke pengirim dengan daya 0.7, warna bertahan);
  `DEFLECT` → pecah jadi percik; jaga → `BLOCK` postur +25; menghindar → lewat.
* Pemahaman Dao Pedang ≥ 60 menambah lebar gelombang +0.6 b (bukan jendela waktu).

#### 35.3.11 Pedang terbang pendamping

* Saat `FIGHT` dimulai, pedang terbang yang sedang dinaiki diturunkan (`FlyingSword.dismount(..., Reason.TOGGLE)`,
  pesan "Duel di tanah: pedangmu menjadi pendamping"); terbang dicegah selama duel (cek tiap tick, turun lagi).
* `Mastery.level(data, Technique.FLYING_SWORD.id()) ≥ 3` → **Pedang Pendamping** (`CompanionSword`): klien menggambar
  `Cultivators.flightSword(player)` mengorbit di bahu (V, `ItemDisplay` lewat `DisplayKey`). Kemampuan server: sekali per
  200 tick menangkis otomatis satu gelombang qi atau proyektil yang mengarah ke pemiliknya (`DuelClash.outcome =
  COMPANION_DEFLECT`); saat lawan terhuyung, pedang pendamping melesat menusuk (`SwingKind.TERBANG`, kerusakan 0.5 ×,
  bisa ditangkis lawan yang sudah pulih: percik di bilah pendamping).
* Murid dengan teknik pedang terbang di daftar ajarnya mendapat pendamping yang sama. Ilham: hujan pedang terbang Meng Hao
  di laga final Sekte Luar (35.8.6).

#### 35.3.12 Teknik selama duel

`JURUS_BEBAS`: teknik biasa berlaku; kerusakan teknik dari pasangan bisa **ditangkis** bila teknik itu berbentuk satu
serangan terarah (spec `TechniqueType.SERANG` dengan bentuk pertama `STRIKE`/`BEAM`/`TRAVEL` – S membaca dari
`TechniqueRegistry.byId` saat `ALLOW_DAMAGE` dengan sumber tipe `kultivator:technique`): `PERFECT` = batal penuh,
`DEFLECT` = ×0.5, jaga = ×0.7 + postur. Domain, aura, area, ikatan tidak bisa ditangkis. Teknik pedang (`Dao.SWORD`)
yang mengenai menambah postur korban +12.

#### 35.3.13 Lapisan umum: melawan mob, elit, purba, bos

Aktif di luar duel resmi bila `DuelConfig.mobParry = true` (bawaan) dan klien punya modul:
* Tangkisan dan jaga pemain berlaku untuk pukulan jarak dekat mob (`source.getEntity() == source.getDirectEntity()`,
  tipe `MOB_ATTACK`): mob tak punya windup yang terlihat, jadi jendelanya **reaktif**: tangkisan dalam 6 tick sebelum
  pukulan tiba = `DEFLECT` (kerusakan 0), dalam 2 tick = `PERFECT` (mob terdorong 0.4, postur mob +1.4 L, hentak:
  navigasi berhenti 10 tick). Jaga = kerusakan × `DuelConfig.guardDamageFactor = 0.4` + postur pemain.
* **Mob bersenjata bilah** (memegang kelas `PEDANG`/`KAPAK`/`TOMBAK`): bila `doHurtTarget` mob dan pukulan pemain ke mob
  itu (vanilla atau BC; di luar duel tidak ada mode KLASIK) jatuh dalam 2 tick satu sama lain dan keduanya saling menghadap
  → `CLASH` dengan percik penuh dan kedua kerusakan dibatalkan. Menyenangkan melawan zombi berpedang dan wither skeleton.
* **Postur mob** (`MobGuard`): hanya untuk elit (`EliteData`), perampok, purba (`PrimordialBeastEntity`,
  `HeavenEnvoyEntity`), murid saingan dan NPC Dunia: maks `20 + 0.6 × maxHealth` (maks 400; elit tier ×1.5). Bos dengan
  tag `kultivator:technique_bosses` dan Wither, Ender Dragon, Warden: postur tampil tetapi jebol hanya membuat terhuyung
  10 tick dan Tebasan Penentu ×1.5 dibatasi 15% nyawa maks; tak pernah memotong fase bos.
* Tebasan Penentu melawan mob: ×2.5 kerusakan senjata (perampok, elit) — tidak pernah instan membunuh bos.
* Elit dan purba yang punya telegraf (`EliteSkills` aktif tier 2, serangan purba) mengirim tanda 危 lewat
  `DuelHooks.telegraph(entity, kind, impactTick)` bila modul pemiliknya kelak memanggilnya (API disediakan, tidak
  diwajibkan; tanpa itu tetap reaktif).

#### 35.3.14 Hidup berdampingan dengan vanilla, Better Combat, Combat Roll

* `ALLOW_DAMAGE` Duel didaftarkan di fase `kultivator:duel_clash` yang diurutkan **sebelum** `Event.DEFAULT_PHASE`
  (`ServerLivingEntityEvents.ALLOW_DAMAGE.addPhaseOrdering(DUEL_PHASE, Event.DEFAULT_PHASE)`), jadi pembatalan tangkis
  terjadi sebelum penjepit `DuelRules`.
* Mode KLASIK: klien membatalkan `startAttack` hanya untuk pemain yang sedang `FIGHT` dan memegang kelas bilah; server
  tetap waspada: pukulan vanilla langsung (tipe `PLAYER_ATTACK`, tanpa `RESOLVING`) dari pasangan duel resmi dibatalkan
  bila klien pelaku terdaftar KLASIK (cegah makro), dan diterima seperti biasa bila klien `IKUTI_BC`/`VANILLA` (lihat
  bawah).
* **Better Combat**: klien tidak membatalkan apa pun. `BetterCombatBridge` (U, refleksi) mendaftar ke `ATTACK_START`:
  arah dari tabel nama animasi, windup = `round(AttackHand.upswingRate() × player.getCurrentItemAttackStrengthDelay())`
  dijepit ke ±2 tick dari tabel kelas, `combo.current()==combo.total()` → berat. Kirim `DuelInput BC_SWING`. Pukulan BC
  yang tiba di server (lewat `player.attack`) dicocokkan `Exchange.matchBc(attacker, target, now)` dengan ayunan tertunda
  (impak ±3 tick): hasil `DEFLECT/PERFECT/CLASH/BIND/BLOCK/EVADE` → pukulan BC dibatalkan (`ALLOW_DAMAGE` false); `HIT` →
  pukulan BC diteruskan apa adanya (kerusakan BC, bukan milik kita) dan hanya postur ditambahkan. Pukulan BC tanpa ayunan
  tertunda (klien tanpa modul) → diperlakukan sebagai pukulan vanilla dengan pemeriksaan tangkis reaktif.
  Tabel arah BC (U memverifikasi tiap animasi di dalam game dengan `/kultivasi uji duel bc`):

| animasi BC | `SwingDir` |
|---|---|
| `one_handed_slash_horizontal_right`, `two_handed_slash_horizontal_right`, `one_handed_swipe_horizontal_right`, `one_handed_slash_switch_blade_right` | `KANAN` |
| `one_handed_slash_horizontal_left`, `two_handed_slash_horizontal_left`, `one_handed_slash_switch_blade_left` | `KIRI` |
| `two_handed_slash_vertical_right`, `dual_handed_slash_cross` | `ATAS_KANAN` |
| `two_handed_slash_vertical_left` | `ATAS_KIRI` |
| `one_handed_slam`, `one_handed_slam_instant`, `two_handed_slam`, `two_handed_slam_heavy` | `ATAS` |
| `one_handed_uppercut_right`, `dual_handed_slash_uncross` | `BAWAH_KANAN` |
| `one_handed_stab`, `one_handed_stab_mounted`, `two_handed_stab_left`, `two_handed_stab_right`, `dual_handed_stab`, `one_handed_punch` | `TUSUK` |
| `two_handed_spin` | `PUTAR` |
| tak dikenal | arah rangkaian bawaan |

  BC tetap memutar suara dan jejaknya sendiri; kita tidak menggambar jejak ganda (35.4.4) dan tidak memutar desau bila
  bendera `BC` ada. Pose BC (PAL) menang untuk lengan (35.5.6).
* **Combat Roll**: `CombatRollCompat` (U, server, refleksi) mendaftar `PLAYER_START_ROLLING` → `DuelHooks.onRoll(player,
  dir)` → `Fighter.dodgeUntil = now + 6`. Tanpa Combat Roll: Langkah Bayangan (35.3.2), klien mengirim `DuelInput DODGE`.
* Perisai vanilla (`BLOCKS_ATTACKS`) tetap bekerja seperti vanilla; Duel hanya menambahkan postur pada pemegang perisai.

---------------------------------------------------------------------------------------------------------------------

### 35.4 Visual (permintaan butir 3)

Semua di paket `dev.kultivator.client.duel.fx` (V) kecuali disebut lain. Masukan: peristiwa dari `DuelBus` (U, 35.9).
Semua jumlah di bawah untuk **Normal**; Minimal ×0.25 (min 4 percik inti), Maksimal ×2 (tabel 35.4.12).

#### 35.4.1 Titik kontak (`ContactSolver`)

1. Server mengirim titik jangkar di `DuelClash.x/y/z` dari geometri busur bersama (`BladeArcs`, 35.5.3) pada tick impak.
2. Klien memperhalus dengan bilah yang benar-benar digambar: `BladeTracker.segment(entityId, partial)` memberi pangkal
   (H) dan ujung (P) dunia tiap bilah pada frame terakhir (dari `BladeProbeLayer`, 35.5.4). Titik terdekat antar dua
   segmen (rumus segmen-segmen klasik: parameter `s, t` dijepit ke [0,1]); bila jarak terdekat < 0.35 b → kontak =
   titik tengah pasangan terdekat; bila tidak, titik di bilah bertahan yang paling dekat ke ujung penyerang.
3. Bila hasil klien berjarak > 0.6 b dari jangkar server (bilah tak terlihat, entitas di luar jarak render, model
   pihak ketiga) → pakai jangkar server.
4. Normal kontak `n = normalize(dirA × dirB)` (silang arah dua bilah; bila hampir sejajar pakai arah ayunan penyerang ×
   atas). Kecepatan bilah penyerang `vA` = selisih posisi ujung antar frame / dt (presentasi σ).
5. Selama kunci bilah, titik kontak dihitung ulang tiap frame dan bergeser bolak-balik sepanjang bilah mengikuti
   `pressure` (gesekan "baja menggerus baja").

#### 35.4.2 Percikan (`SparkBurst`) dan partikel baru

* Partikel baru (V, didaftarkan sisi utama di `dev.kultivator.fx.DuelParticles`): `kultivator:duel_spark` (garis api
  memanjang menurut kecepatan, **gravitasi 0.6**, `hasPhysics = true`, memantul sekali di tanah dengan redaman 0.35,
  menua oleh σ `EpicClock`, warna mendingin putih-panas `0xFFF8E0` → kuning `0xFFC850` → jingga `0xFF7A20` → merah gelap
  `0x802010` sepanjang umur), `kultivator:duel_ember` (bara kecil yang melayang turun pelan, berkedip). Sprite memakai
  ulang `film_spark_0..3` dan `film_flare_0..3` (berkas `particles/duel_spark.json`, `duel_ember.json`).
* Kecepatan percik: `v = reflect(normalize(vA), n) × kecepatan + kerucut acak 35°`, kecepatan 0.25-0.6 b/tick, umur 6-14
  tick; 20% percik berwarna qi penyerang (campur 30%) bila ayunan `QI`.

| hasil | duel_spark | duel_ember | lain-lain |
|---|---|---|---|
| `CLASH` | 24 | 6 | `Qi.glint` 6, kilat logam 0.6 b 3 tick, `FilmParticles.FLARE` 1 |
| `DEFLECT` | 16 terarah (semprot ke arah penyerang) | 4 | kilat 0.5 b |
| `PERFECT` | 40 + cincin 12 `FilmParticles.STAR` emas `0xFFD86A` | 10 | kilat 1.2 b, cincin gelombang vertikal, 35.4.6 hit-stop |
| `BIND` mulai | 30 | 8 | titik panas `QuadFx` GLOW jingga berdenyut di kontak |
| `BIND` berjalan | 3 per tick (gesek, kecil, cepat) | 1 per 2 tick | titik panas bergeser |
| `BIND_MENANG`/`BIND_LEPAS` | 36 | 10 | gelombang kejut, debu |
| `GUARD_BREAK`/`POSTUR_JEBOL` | 50 | 16 | 20 `FilmParticles.MUZZLE`/`SMOKE`, gelombang kejut + decal + bingkai dampak (35.4.7) |
| `HIT` | 0 logam; 8 serpih qi `Qi.spark` warna penyerang + 6 `ParticleTypes.CRIT` | 0 | tanpa darah |
| `BLOCK` | 10 tumpul | 2 | kilat 0.4 b |
| `QI_DEFLECT` / Pantulan | 30 berwarna dua Dao | 8 | sabit membelah jadi dua `QuadFx` CRESCENT kecil |
| `COMPANION_DEFLECT` | 14 | 4 | jejak pedang pendamping |
| `TANGAN_KOSONG` kena kepalan | 0 | 0 | 8 `ParticleTypes.POOF` |

Percik memakai `FxBudget.take(cost, Priority.CORE)` untuk 8 pertama, sisanya `DETAIL`; sisa ditolak diam-diam.

#### 35.4.3 Kilat logam

`MetalFlash`: satu quad bintang 4-cabang berputar 20°/tick di titik kontak, putih → warna percik, ukuran per hasil, umur
3 tick (5 untuk `PERFECT`), aditif penuh terang (`EpicRenderTypes` glow). `PERFECT` milik sendiri: `ScreenFlash.add(0xFFE7A0,
0.12, 3)` (lewat gerbang luminans; tidak bila "Kilatan layar" mati). Maksimal + Lapisan lensa nyala:
`LensLayers.light(kontak)` dengan `1 << FilmLens.FLARE.ordinal() | 1 << FilmLens.ANAMORPHIC.ordinal()` selama 6 tick.

#### 35.4.4 Jejak bilah (pita)

* `BladeTrail` per bilah: riwayat 12 sampel (H, P) per frame dari `BladeTracker` selama windup akhir sampai pemulihan
  awal; pita = strip quad antar sampel berurutan (pangkal ke ujung), alfa 0 di pangkal → 0.85 dekat ujung, memudar menurut
  umur sampel (≤ 6 tick), kepala terang. Tekstur `textures/misc/duel/trail_gradient.png` (V).
* Warna: baja `0xE8F0FF` α 0.35 (ayunan biasa), warna qi α 0.8 + inti putih (ayunan `QI`), merah `0xFF3030` (berbahaya),
  emas (`BALIK`, `PENENTU`). Tebal 0.9 × panjang bilah.
* Kapan: selalu di duel resmi; di luar duel hanya untuk ayunan `QI` dan bila setelan "Jejak bilah" = Selalu. Bila BC
  menggambar jejaknya sendiri untuk ayunan itu (bendera `BC`), V hanya menggambar pita qi (bila `QI`), tidak pita baja.
* Digambar dari `LevelRenderEvents.COLLECT_SUBMITS` (pola `QuadFx`), relatif kamera, tidak di shadow pass; jenis
  `RIBBON` sendiri: `DuelTrailRenderer` dengan cap per mutu 2/6/12 pita hidup.

#### 35.4.5 Gelombang kejut, debu, bekas tebasan (decal)

* **Gelombang kejut** (`ShockRing`): cincin datar di tanah (`QuadFx` RING, warna percik, 0.5 → 4.5 b dalam 8 tick) + cincin
  vertikal tegak lurus garis duel di titik kontak (0.3 → 2.2 b dalam 6 tick) untuk `PERFECT` berat, `BIND_MENANG`,
  `GUARD_BREAK`, `POSTUR_JEBOL`, `PENENTU`. Tali gelanggang bergetar (amplitudo 0.15 b, 10 tick).
* **Debu**: `ParticleTypes.BLOCK` dengan `BlockParticleOption` blok tanah di bawah (baca saja) 12 butir menyebar
  horizontal + 4 `ParticleTypes.DUST_PLUME` saat kunci bilah selesai, langkah berat, dan pendaratan Tebasan Langit;
  `ParticleTypes.CLOUD` 4 bila tanah tak punya partikel.
* **Bekas tebasan** (`ScorchDecal`): quad datar di permukaan atas blok padat (`state.isFaceSturdy(level, pos, Direction.UP)`,
  dicari `level.clip` ke bawah 3 b), dinaikkan 0.015 b, diputar mengikuti arah ayunan, tekstur
  `textures/misc/duel/scorch_slash.png` (goresan tinta-hangus) dan `scorch_ring.png` (lingkaran gelanggang). Berpendar
  jingga 20 tick lalu menjadi hangus gelap, hilang perlahan di 60 tick terakhir dari umur 300. Dibuat oleh: gelombang qi
  (sepanjang lintasan tiap 1.5 b), `GUARD_BREAK`, `PENENTU`, Sapuan Rendah. Cap 6/12/24. Tidak pernah menyentuh blok;
  ditolak di atas cairan, kaca, daun.

#### 35.4.6 Hit-stop dan gerak lambat

* **Hit-stop** (semua `EpicClock.hitStop(n)`, tak ada jeda minimum): `PERFECT` 3 tick, `CLASH` berat 2, `GUARD_BREAK` 4,
  `POSTUR_JEBOL` 4, `PENENTU` 5, `BIND_MENANG` 2. Pose duel, percik, pita dan kamera duel membaca σ, jadi semuanya
  membeku bersama (posisi entitas vanilla tidak; dalam kunci bilah mereka memang diam). Hanya untuk penonton dalam 24 b dan
  pelaku; "Kurangi gerakan" / "Gerak lambat" mati → tidak ada.
* **Gerak lambat** (`EpicClock.slowmo(realTicks, prio)`, tunduk `MIN_GAP = 120`): Tarian Pedang (rantai 4) 20 tick,
  `GUARD_BREAK` 12, sempurna beruntun ×3 16. Prioritas `DuelFx.SLOW_PRIO = 40` (di bawah tribulasi dan ultimate).
* **Bullet time** hanya di sinematik penutup (35.4.9).
* Server tidak pernah melambat; "gerak lambat" adalah presentasi. Penahanan nyata hanya di kunci bilah, hentak dan
  `FINISHER` (aturan permainan, bukan efek).

#### 35.4.7 Guncangan kamera, bingkai dampak, lensa

| peristiwa | `CameraShake.add(intensity, ticks, at, radius)` | bingkai dampak | lain |
|---|---|---|---|
| `CLASH` | 0.12, 4, kontak, 8 | — | — |
| `PERFECT` | 0.22, 5 | — | flash 35.4.3 |
| `BIND` berjalan | 0.05, terus-menerus (diperbarui tiap 4 tick) | — | dengung rendah |
| `GUARD_BREAK`/`POSTUR_JEBOL` | 0.45, 8, `climax = false` | Lembut: satu rona warna penyerang; Penuh: `ImpactFrame.play(INV, warna, warna, true, 1.0, true, seed)` | gerak lambat 12 |
| `PENENTU` / pukulan akhir | 0.6, 10, `climax = true` | Penuh: `INV, BLACK, warna`; Lembut: rona putih | sinematik penutup |
| `HIT` kena diri | 0.18, 4 | — | kemerahan vanilla |

Bingkai dampak hanya bila `EpicSettings` bingkai dampak tidak Mati dan `EpicScreen.tryLuminance()` mengizinkan; α ×0.5
untuk penonton.

#### 35.4.8 Intro sinematik (`DuelIntroFilm`)

Prasyarat: fase `INTRO` dari server, setelan kamera tidak Mati, setelan "Intro duel" nyala. Adegan dibuat lewat fabrik
baru `EpicDirector.duelScene(int aId, int bId, Vec3 center, int seed, int life)` (V menambahkannya; momen
`EpicIds.EXTRA`, `own = true` untuk peserta **dan** penonton dalam 32 b, bendera `FLAG_CAMERA_OK | FLAG_SAFE |
FLAG_RESULT_SAFE` dihitung dari pemeriksaan lokal yang **mengabaikan pasangan duel**). `ShotGuard.ignoreHostile` (predikat
statis baru, V) mengecualikan id lawan agar perampok yang ditahan tidak membatalkan shot. Server sudah menjamin tak ada
musuh lain dalam 24 b (35.2.2).

| tick | shot (`CinematicDirector.shoot`, kind `KIND_EXTRA`) | layar & suara | dunia (klien) |
|---|---|---|---|
| 0-30 | `Shots.stillAt(center, 9, 2.2, 90°, 1.5)` lebar dari samping garis duel | `Letterbox` nyala, `NOTE_BLOCK_BASEDRUM` di 0 dan 10, angin `ELYTRA_FLYING` vol 0.25 pitch 0.6 | 20 garis angin `QuadFx` STREAK melintas, daun gugur: `ParticleTypes.TINTED_LEAVES` dengan warna dedaunan bioma (`BiomeColors.getAverageFoliageColor`), di bioma ceri `CHERRY_LEAVES`, salju di bioma dingin; kertas jimat gelanggang berkibar |
| 30-55 | `Shots.ots(posisi B, 1.25)` dari bahu A ke B | `TitleCard.full(Component "Duel Pedang", sub "A · ranah · gelar — B · ranah · gelar", "決", 0xC0302A, 70)` di tick 40 | B ke pose `SIKAP_PEMBUKA` (kuda-kuda), daun lewat di depan kamera |
| 55-80 | `Shots.ots(posisi A, 1.25)` dari bahu B | taiko ketiga | A ke pose `SIKAP_PEMBUKA` |
| 80-100 | `Shots.twoShot(B, 6.0, 4.5, 1.2, 1.0)` dorong masuk | `film.riser_short` (diri sendiri) | tick 92: keduanya `HUNUS` (menghunus), kilau di ujung bilah (`MetalFlash` 0.3 + `LensLayers.light(ujung)` bila nyala); tick 100: `film.gong` (stereo, diri) atau `BELL_BLOCK` pitch 0.6 (posisi, penonton) |
| 100-110 | keluar (blend 10) | `Letterbox` turun | — |
| `COUNTDOWN` | kamera pemain | HUD 三 二 一 始 besar (U), tabuh tiap detik | daun berhenti, angin tinggal 25% |

Tanpa kamera: hanya kartu judul, daun, angin, suara. Intro 40 tick (tanpa film): kartu judul + taiko + gong.

#### 35.4.9 Sinematik penutup (`DuelFinisherFilm`)

Pemicu: `DuelClash.outcome` dengan bendera `DECISIVE` (pukulan yang memutus duel: penentu, pukulan 10%, keluar gelanggang
tidak memicu). Server masuk `FINISHER` 60 tick.

| tick | kamera | waktu | dunia | layar & suara |
|---|---|---|---|---|
| 0 | tahan | `EpicClock.hitStop(5)` | percik `PENENTU` beku di udara, pita emas | bingkai dampak (35.4.7), `film.sub_boom` tertunda 3 tick (diam dulu: pola `SilentImpact`) |
| 3-27 | `Shots.orbit(4.5, 1.4, 70°, 2.0)` mengitari titik tengah | `BulletTime.start(0.06, 24)` | goresan tinta lebar melintasi tubuh yang kalah (`QuadFx` STREAK hitam-merah), kelopak/daun berputar mengelilingi keduanya | `film.whoosh` pitch 0.5 |
| 24-40 | `Shots.push(5, 3.2, 1.3, 0.8)` ke pemenang | ramp σ balik | yang kalah `KALAH_BERLUTUT`, pemenang `SARUNG` (mengibas darah-qi lalu menyarungkan) dengan klik `film.clang` pitch 1.8 vol 0.3 | — |
| 40-60 | tahan, keluar di 60 | normal | daun gugur pelan | `TitleCard.full("Pemenang", "X — alasan", "勝", warna pemenang, 70)`; musik `MusicCue.M_TRIUMPH` 12 s bila musik nyala; penonton `VILLAGER_CELEBRATE`/sorak murid |

Bila kamera Mati: tanpa shot; hit-stop, percik, pose, kartu tetap. Kalah dengan menyerah: tanpa bingkai dampak, kamera
langsung ke dorongan, kartu "Mengaku kalah".

#### 35.4.10 Kamera duel orang ketiga dan kamera penonton

* `DuelCamera` (V) dengan mixin baru `client/mixin/duel/DuelCameraMixin` pada `Camera.update` di titik yang sama dengan
  `CameraCinematicMixin` (`INVOKE alignWithEntity, shift AFTER`), `priority = 1100` (setelah sinematik), tidak aktif
  saat `CinematicDirector.active()`.
* Mode (`duel_camera`): `MATI` (bawaan), `BAHU`: posisi = mata − pandang × 3.2 + kanan × 0.9 + atas × 0.35, yaw kamera =
  yaw pemain + 25% ke arah lawan (agar keduanya dalam bingkai, maks 30°), `level.clip` agar tidak menembus dinding (pola
  `Shots`), FOV ×1.05. **Rotasi dan bidikan pemain tidak berubah** (ray serang tetap dari mata, seperti F5 vanilla); hanya
  posisi kamera. `PENONTON` (hanya bukan peserta, atau duel murid): orbit pelan di sekitar titik tengah, gerak tetikus
  memutar orbit, gulir mendekat/menjauh, WASD keluar dari mode.
* "Kunci lawan" (opsional, bawaan mati, **hanya melawan NPC/boneka**, tak pernah PvP): klien memutar yaw pemain menuju
  lawan maks 90°/s saat tombol `parry` ditahan.

#### 35.4.11 Gelanggang dan petunjuk

Tiang, tali, lingkaran tinta (35.2.3); panah tinta ke tanda saat `GATHER`; garis keluar merah berdenyut saat seorang
peserta di luar; tanda 危 merah dan 決 merah sebagai `TextDisplay` sisi klien di atas kepala (atau HUD, 35.9 U).

#### 35.4.12 Anggaran per mutu dan keamanan Sodium/Iris

| batas | Minimal | Normal | Maksimal |
|---|---|---|---|
| percik per peristiwa | 6 | 40 | 80 |
| percik per tick (semua duel) | 30 | 160 | 400 |
| bara | 0 | 10 | 24 |
| pita bilah hidup | 2 (hanya qi) | 6 | 12 |
| decal | 6 | 12 | 24 |
| gelombang kejut | 1 | 3 | 6 |
| tiang gelanggang | 4 | 8 | 8 + 8 jimat |
| daun intro per tick | 2 | 8 | 20 |
| kilat lensa | tidak | tidak | ya |

Dimensi berat: semua quad lewat `LevelRenderEvents.COLLECT_SUBMITS` relatif kamera dengan jenis render yang sudah dipakai
`QuadFx`/`EpicRenderTypes` (aman di Sodium; di bawah paket shader Iris memakai profil non-emisif yang sama dengan
`EpicRenderTypes.BOLT_EMISSIVE_UNDER_PACK = false`), dilewati saat `ShaderCompat.shadowPass()`; tidak ada post chain,
tidak ada shader baru. `EpicBudget.takeQuads` dihormati; bila FPS < `EpicBudget.MIN_FPS` turun satu tingkat. Efek pemain
lain × `EffectQuality.othersScale()`.

---------------------------------------------------------------------------------------------------------------------

### 35.5 Animasi (permintaan butir 4)

#### 35.5.1 Arsitektur

* `technique/spec/dsl/Pose`: A **menambahkan satu konstanta di akhir**: `DUEL(Swing.NONE, 0)`, dipakai sebagai jenis
  nominal `CastPoses.Snapshot.pose` untuk pose duel (tidak duduk, tidak dash; `PoseLibrary.def(DUEL) == null` jadi
  tabel lama tak berubah).
* Data pose bersama (sisi utama, tanpa kelas klien) agar server memakai geometri yang sama untuk uji busur:
  `dev.kultivator.duel.blade.DuelPose` (enum), `DuelPoseTable` (kunci per pose: windup, strike, follow, hold, nilai 18
  kanal dalam urutan `PoseLibrary`), `BladeShape` (pegangan/panjang per `WeaponClass`), `BladeKinematics` (FK murni
  `org.joml`), `BladeArcs.sample(...)`.
* Klien (`dev.kultivator.client.duel.anim`): `DuelPoses` (registri pose aktif per id entitas, setara `CastPoses` tetapi
  untuk duel: masukan `DuelSwing`, `DuelFighterSync`, `DuelClash`, `DuelBindSync`; waktu = umur entitas + presentasi σ),
  `DuelPoseLibrary` (sampling dengan easing `PoseLibrary.easeOutCubic/easeOutBack`), `BladeProbeLayer`, `BladeTracker`,
  `FirstPersonDuel`, `PalBridge`.
* `PoseRenderData.extract` (edit A): `DuelPoses.snapshot(entity, age)` ditanya **lebih dulu**; bila ada, ia dipakai,
  kecuali pose cast teknik sedang aktif dengan bobot > 0.5 (teknik menang, duel hanya kaki). Satu snapshot, jadi
  `PoseApplier.apply` dan `lift/tilt` tidak perlu diubah.

#### 35.5.2 Set pose

Kunci ditulis dalam bahasa `PoseLibrary` (radian, model space). "w" = tick menuju kunci windup (easeOutCubic), "s" =
tick menuju kunci strike (easeOutBack), "f" = follow-through. Semua dicerminkan untuk tangan kiri utama.
Tiap tebasan juga punya **kunci bilah** (`bladeRoll`, putaran pergelangan dalam radian di sekitar sumbu lengan, kanal
tambahan `WRIST = 18`, `CHANNELS_DUEL = 19`) yang dipakai FK bilah dan `BladeProbeLayer` (memutar matriks tangan sebelum
item), sehingga sisi tajam benar-benar menghadap arah tebasan.

| `DuelPose` | dipakai untuk | windup → strike (ringkas) | w/s/f |
|---|---|---|---|
| `SIKAP_PEMBUKA` | intro, berdiri di gelanggang | RAX −0.9 RAZ 0.2 WRIST 0.6, LAX −0.5 LAZ −0.3, kaki kanan maju RLX −0.25, LEAN 0.05 | tahan, napas `MOD_BREATH_X` 0.03 |
| `KUDA_TINGGI` (jaga atas) | jaga saat lawan mengangkat berat | RAX −2.4 RAZ −0.2 WRIST 1.4, LAX −2.1 | 3 |
| `KUDA_TENGAH` (jaga bawaan) | menahan jaga | RAX −1.2 RAY −0.5 WRIST 0.9, LAX −1.0 LAY 0.5 (dua tangan di gagang bila `PEDANG_BERAT`) | 3 |
| `KUDA_RENDAH` | melawan sapuan, NPC bertahan | RAX −0.5 WRIST −0.4, LEAN 0.2, LIFT −0.12 | 4 |
| `TEBAS_ATAS` | `ATAS` | w: RAX −2.9 RAZ 0.1 HX −0.2 LEAN −0.1 → s: RAX −0.3 LEAN 0.3 RLX −0.35 | 4/2/3 (ringan 6) |
| `TEBAS_ATAS_KANAN` | `ATAS_KANAN` | w: RAX −2.6 RAZ 0.7 TWIST −0.4 → s: RAX −0.4 RAZ −0.6 TWIST 0.45 | 4/2/3 |
| `TEBAS_KANAN` | `KANAN` | w: RAX −1.5 RAY 0.9 TWIST −0.55 WRIST 1.57 → s: RAX −1.4 RAY −1.0 TWIST 0.6 | 4/2/3 |
| `TEBAS_BAWAH_KANAN` | `BAWAH_KANAN` | w: RAX 0.3 RAZ 0.5 TWIST −0.35 LIFT −0.08 → s: RAX −2.4 RAZ −0.3 TWIST 0.4 | 4/2/3 |
| `TEBAS_BAWAH` | `BAWAH` (naik) | w: RAX 0.4 LEAN 0.25 LIFT −0.15 → s: RAX −2.8 LEAN −0.1 HX −0.3 | 5/2/3 |
| `TEBAS_BAWAH_KIRI`, `TEBAS_KIRI`, `TEBAS_ATAS_KIRI` | cermin sisi | seperti kanan dengan RAY/RAZ/TWIST bertanda terbalik dan WRIST −π/2 untuk punggung tangan | 4/2/3 |
| `TUSUK` | `TUSUK`, Tusukan Maut | w: RAX −1.2 RAY 0.3 TWIST −0.45 RLX 0.3 → s: RAX −1.6 TWIST 0.35 LEAN 0.15 RLX −0.6 (lunge) | 4/2/2 |
| `PUTAR` | `PUTAR` | dua kunci strike: TWIST −1.2 lalu TWIST +2.6 sambil RAX −1.4 RAY −1.0 (tubuh berputar, kanal `TWIST` penuh) | 3/4/3 |
| `ANGKAT_BERAT` | isi berat (tahan) | RAX −3.0 RAZ −0.3 LAX −2.8, LEAN −0.15, gemetar `MOD_CHARGE_SHAKE` bertambah sampai penuh; qi: sama + kilau | tahan |
| `TANGKIS` | `DEFLECT`/`PERFECT` | dari pose sekarang: RAX −1.7 RAY ke arah datang −0.6..0.6 WRIST 0.0, hentakan pergelangan cepat 1 tick | 1/1/3 |
| `TANGKIS_SEMPURNA` | `PERFECT` | `TANGKIS` + LEAN −0.05 + bilah dihentak keluar (RAZ +0.4 di f) | 1/1/4 |
| `INJAK` | Injak Pedang | RLX −1.2 lalu RLX 0.2 LIFT 0.1 (menginjak bilah lawan), RAX −1.0 | 2/2/4 |
| `KUNCI_BILAH` | kunci bilah | RAX −1.5 RAY −0.2 LAX −1.4 LAY 0.4 (dua tangan menekan gagang), LEAN 0.3, RLX −0.5 LLX 0.35; getaran `MOD_TREMBLE` amplitudo = |pressure|/100 × 0.06; LEAN bergerak ±0.15 mengikuti `pressure` | tahan |
| `HENTAK` | hentak balik ringan/berat | RAX −0.6 RAZ 0.6 LEAN −0.25 HX −0.2, mundur LLX 0.4 | 1/2/6 |
| `TERPENTAL` | kalah kunci / gelombang kejut | LEAN −0.4 TILT −0.15 lengan terbuka RAZ 0.9 LAZ −0.9 | 1/3/8 |
| `TERHUYUNG` | postur jebol | LEAN 0.45, kepala tunduk HX 0.5, bilah turun RAX −0.2, lutut LIFT −0.1, gemetar | tahan 30 |
| `TERKENA` | `HIT` | TWIST ke sisi kena ±0.35, LEAN −0.15 | 1/2/4 |
| `MENGHINDAR` | Langkah Bayangan / roll tanpa Combat Roll | TILT 0.25 ke arah gerak, kaki lebar | 1/2/4 |
| `HUNUS` | intro | tangan kiri di sarung (LAX −0.4 LAY 0.6), kanan menarik melintang (RAX −1.2 RAY 0.8 → RAX −1.0 RAY −0.4 WRIST 1.2) | 6/4/6 |
| `SARUNG` | menang | kibas (RAX −1.0 RAZ 0.8 cepat) lalu sarungkan ke pinggang kiri, item disembunyikan setelah tick 12 | 4/4/10 |
| `KALAH_BERLUTUT` | kalah | `KNEEL_STRIKE`-mirip: LIFT −0.45 RLX −1.4 LLX 0.3, bilah tertancap di depan (RAX −0.6, WRIST 3.0), HX 0.4 | 6/6/tahan |
| `HORMAT` | sebelum/sesudah duel (`/kultivasi duel hormat`) | salam kepalan-telapak: RAX −1.3 RAY −0.6, LAX −1.3 LAY 0.6, LEAN 0.2 | 4/4/10 |

Jumlah: 27 pose (termasuk tiga cermin). A boleh menyetel angka, bukan menghapus pose.

#### 35.5.3 Data sudut bilah dan kinematika (bersama)

* `BladeShape` per `WeaponClass`: titik pegangan `grip` dan ujung `tip` dalam ruang item [-0.5, 0.5]³ (pedang vanilla:
  pangkal bilah piksel (5, 11) → (−0.19, −0.19, 0), ujung piksel (15, 1) → (0.44, 0.44, 0); `PEDANG_BERAT` ujung ×1.25;
  `TOMBAK` ujung (0.5, 0.5) dengan panjang ×1.6; `GADA` hanya kepala bulat radius 0.18 di ujung), lebar sisi tajam.
  Disetel A dengan membandingkan garis debug `uji duel bilah` terhadap model yang digambar.
* `BladeKinematics.hand(float[] channels, boolean slim, boolean left, Matrix4f out)`: rantai persis 35.1 (rotasi tubuh
  180 − bodyYaw, `scale(-1,-1,1)`, `translate(0,-1.501,0)`, root, pivot lengan `(∓5, 2, 0)/16` (+0.5 px slim), rotasi
  `rotationZYX(RAZ, RAY, RAX)`, `LEAN/TWIST` seperti `PoseApplier` memindahkan pivot bahu), lalu transform item:
  `rotX(-90°) rotY(180°) translate(±1, 2, -10)/16`, `WRIST` di sekitar sumbu Y tangan, tampilan `handheld`
  (`[0,-90,55]`, `[0,4,0.5]/16`, `0.85`), dan `translate(-0.5,-0.5,-0.5)`. Pegangan pedang 3D murid: transform
  `DiscipleHeldItemLayer`.
* `BladeArcs.sample(DuelPose pose, WeaponClass w, float t01, Vec3 feet, float bodyYaw, float scale)` →
  `BladeSegment(Vec3 grip, Vec3 tip)`. Server: `HitTest.sweep` mengambil 5 sampel antara impak −1 dan impak +1, menguji
  segmen terhadap AABB korban diperbesar 0.1 (segmen-AABB slab), dan `Exchange` memakai titik terdekat dua segmen saat impak
  sebagai jangkar kontak. Murah: < 2 µs per sampel, maks 64 ayunan aktif.

#### 35.5.4 `BladeProbeLayer` dan `BladeTracker` (klien)

* Lapisan render yang **tidak menggambar apa pun**: didaftarkan lewat `LivingEntityRenderLayerRegistrationCallback` untuk
  setiap renderer yang modelnya `PlayerModel` (pemain, murid, perampok, NPC Dunia ber-`PlayerModel`). Di `submit`, ia
  mengulang `translateToHand` + transform item persis seperti lapisan item yang sebenarnya (vanilla `ItemInHandLayer` atau
  cabang pedang 3D `DiscipleHeldItemLayer`, dibaca lewat helper statis baru `DiscipleHeldItemLayer.swordTransform(PoseStack)`)
  dan menyimpan `grip`/`tip` (matriks terakhir × titik `BladeShape`) + posisi kamera → koordinat dunia di
  `BladeTracker` per `state.id`. Karena dijalankan setelah `setupAnim` dan semua mixin (termasuk PAL/BC), bilah yang tercatat
  = bilah yang terlihat.
* Hanya mencatat entitas yang memegang kelas bilah dan berada dalam 32 b; entri kedaluwarsa setelah 10 frame tanpa
  pembaruan. Entitas tak terlihat (di belakang kamera, dipangkas frustum) → `BladeTracker` jatuh ke `BladeArcs` klien.

#### 35.5.5 Pemain, murid, NPC; orang pertama

* Pemain: `AvatarRendererMixin` → `PoseRenderData.extract` (sudah ada). Murid: `DiscipleRenderer.extractRenderState`
  (sudah ada). Perampok: A menambahkan `PoseRenderData.extract(entity, state, partialTicks)` di akhir
  `RogueRenderer.extractRenderState`. NPC Dunia: modul Dunia memanggil hal yang sama di renderernya (35.7.6).
* Orang pertama (`FirstPersonDuel`, lewat `FirstPersonCast` yang diedit A): tangan kanan mengikuti versi terkompresi pose
  (windup ditarik ke bahu, strike menyapu layar sesuai arah, tangkis menyilang di depan layar, kunci bilah menahan bilah
  melintang dengan getaran). Tanpa itu, tebasan dari orang pertama hanya ayunan vanilla.
* `PoseApplier` sudah membiarkan kaki mengikuti jalan saat berjalan; pose duel menandai `LEAN/TWIST/LIFT` saja untuk tubuh.

#### 35.5.6 Player Animation Library dan Better Combat

* **Aturan menyerah lengan**: bila ayunan berbendera `BC` (dari `DuelSwing`) atau pemain lokal
  `MinecraftClient_BetterCombat.isWeaponSwingInProgress()` (refleksi), `DuelPoses` mengisi bobot 0 untuk kanal lengan
  (`RAX..LAZ`, `WRIST`) dan hanya memberi tubuh/kepala; pose jaga duel dimatikan bila BC punya pose diam (`pose_two_handed_*`)
  untuk senjata itu. Bilah tetap dicatat oleh `BladeProbeLayer`, jadi percikan tetap jatuh di bilah animasi BC.
* `PalBridge` (opsional, setelan `duel_pal` bawaan **mati**): bila PAL terpasang, mendaftarkan pabrik animasi
  `kultivator:duel` lewat refleksi + `java.lang.reflect.Proxy` untuk `PlayerAnimationFactory` dan memicu JSON
  `assets/kultivator/player_animations/duel_*.json` (diekspor dari `DuelPoseTable` oleh skrip `tools/duel_pal_export.py`,
  A) untuk pemain saja. Kegagalan refleksi apa pun → log sekali, kembali ke pose sendiri. Bukan syarat selesai modul.

#### 35.5.7 Model dan tekstur (A)

* Item `kultivator:pedang_latih` (pedang kayu latihan, `ItemTags.SWORDS` lewat `data/minecraft/tags/item/swords.json`
  `replace: false`, kerusakan 4, kecepatan 1.6; resep 2 papan + 1 tongkat diagonal; dipegang Boneka Duel).
* Item model `kultivator:duel_post` (tiang gelanggang kayu merah dengan lentera kertas, model item 3D JSON) — hanya untuk
  `ItemDisplay` klien, tidak ada di tab kreatif.
* Tekstur: `textures/item/pedang_latih.png`, `textures/item/duel_post.png` (16×16 / 32×32, gaya mod). Tekstur efek milik V
  (35.4).

---------------------------------------------------------------------------------------------------------------------

### 35.6 Suara (permintaan butir 5)

#### 35.6.1 Peristiwa baru `duel.*` (U, `sounds.json` + `sound/DuelSounds`)

Tidak ada berkas audio baru (tanpa unduhan): peristiwa baru membungkus ogg Kenney yang sudah ada dengan pitch/volume
berbeda; sisanya suara vanilla. Semua isyarat dunia diputar **di klien** dari `DuelClash`/`DuelSwing` yang sama untuk semua
klien dalam jangkauan (pola C30.4 `FilmSound`, agar suara tepat di frame percikan); suara mono diposisikan di titik kontak
dengan `level.playLocalSound(x, y, z, event, SoundSource.PLAYERS, vol, pitch, false)`; stereo hanya untuk diri sendiri.
Maks 4 isyarat duel per tick klien (`DuelSoundPlayer.MAX_PER_TICK`).

| peristiwa | lapisan | catatan |
|---|---|---|
| `duel.clash` (baru) | `film/clang_0..2` pitch 0.95-1.15 + `ANVIL_LAND` vol 0.25 pitch 1.7 | benturan ringan |
| `duel.clash_heavy` (baru) | `film/clang_*` pitch 0.7-0.8 + `HEAVY_CORE_HIT` 0.6 + `ANVIL_LAND` 0.35 pitch 1.1 | berat, kunci mulai |
| `duel.scrape` (baru, berulang) | `GRINDSTONE_USE` pitch 1.6-1.9 tiap 3 tick + `CHAIN_HIT` pitch 0.8 vol 0.2 tiap 5 tick | selama kunci bilah, pitch naik dengan |pressure| |
| `duel.parry` | `film/clang_*` pitch 1.35 + `TRIDENT_HIT` 0.5 pitch 1.4 | `DEFLECT` |
| `duel.parry_perfect` | penonton: `film/clang_*` pitch 1.6 + `AMETHYST_CLUSTER_BREAK` pitch 1.4 + `BELL_RESONATE` vol 0.3 pitch 1.8; diri: + `film.bell` (stereo) vol 0.5 pitch 1.5 | dering panjang khas |
| `duel.block` | `SHIELD_BLOCK` pitch 1.3 + `film/thud` (diri) | jaga |
| `duel.guard_break` | `SHIELD_BREAK` pitch 0.8 + `ANVIL_HIT` pitch 0.6 + `film.sub_boom` (diri) | |
| `duel.posture_break` | `film/crunch_*` pitch 0.7 + `IRON_GOLEM_ATTACK` pitch 0.6 | |
| `duel.deathblow` | diam 3 tick lalu `film.sub_boom` (diri) / `WARDEN_ATTACK_IMPACT` vol 0.4 pitch 1.2 (penonton) + `PLAYER_ATTACK_STRONG` | |
| `duel.feint` | `film/whoosh_*` pitch 1.8 vol 0.3 | |
| `duel.qi_wave` | `film/zap_*` pitch 0.8 + `BREEZE_WHIRL` 0.4 | lepas gelombang |
| `duel.qi_reflect` | `BREEZE_DEFLECT` + `film/zap_*` pitch 1.3 | Pantulan Qi |
| `duel.hit` | `PLAYER_ATTACK_STRONG` / `PLAYER_ATTACK_CRIT` (kritikal) | kena tubuh |
| `duel.intro_wind` | `ELYTRA_FLYING` vol 0.25 pitch 0.6 + `CHERRY_LEAVES_FALL` | intro |
| `duel.gong` | diri `film.gong`; penonton `BELL_BLOCK` pitch 0.6 | mulai |
| `duel.sheathe` | `film/clang_*` pitch 1.8 vol 0.3 + `ARMOR_EQUIP_NETHERITE` 0.4 | menyarungkan |

**Desau per kelas senjata** (awal strike, hanya bila tidak `BC`): `PEDANG` `PLAYER_ATTACK_SWEEP` pitch 1.3 + `film/whoosh_*`
pitch 1.4 vol 0.35; `PEDANG_BERAT` `film/whoosh_*` pitch 0.9 vol 0.5; `KAPAK` `PLAYER_ATTACK_SWEEP` pitch 0.85;
`TOMBAK` `SPEAR_ATTACK` (Holder) pitch 1.0; `GADA` `MACE_SMASH_AIR` pitch 1.1; `TANGAN_KOSONG` `PLAYER_ATTACK_WEAK`;
ayunan `QI` + `film/hum_energy` singkat vol 0.25; berat penuh + `film/riser_short` (diri) saat isi.

#### 35.6.2 Kiai dan suara tokoh

* Pemain: setelan `duel_kiai` (Mati bawaan / Pria / Wanita) dikirim `DuelPrefs`; server (S lewat `DuelVoices.kiai`, U)
  memutar `Voice.say(player, "grunt.male.attack"|"grunt.female.attack", "", 0.96..1.04)` pada: tebas berat dilepas,
  Tebasan Penentu, menang kunci bilah, Injak Pedang; `grunt.*.hurt` saat postur jebol. Jeda `Voice.ready(uuid, "duel_kiai",
  30)`.
* Murid: `VoiceDisciples.grunt(d, "attack")` pada berat/penentu, `VoiceDisciples.line(d, "attack"|"attack_alt", 200)`
  saat membuka duel, `"victory"` saat menang, `"injured"` saat kalah; NPC Dunia/perampok: `Voice.say(npc,
  profile.voiceKiai, "", pitch)` dan `rogue.ambush`/`rogue.defeat` yang ada untuk perampok.
* Penekanan pita suara: suara kiai tidak memakai `SoundSource.MASTER`; mengikuti setelan "Suara bicara".

#### 35.6.3 Penonton di arena sekte

* Saat `crowd` dan ada murid penonton atau ≥ 2 pemain menonton: `DuelCrowd` (U, klien) memutar lapisan sorak: 3 suara
  `VILLAGER_CELEBRATE` pitch 0.85-1.15 vol 0.15 tertunda 0/3/7 tick + `VoiceDisciples.line(d, "cheer", 160)` dari 1-2
  murid acak (server, S memanggil) pada: rantai 6, `PERFECT` berat, `GUARD_BREAK`, akhir duel. Napas penonton (desis
  rendah `ELYTRA_FLYING` vol 0.05) saat kunci bilah. Tabuh `NOTE_BLOCK_BASEDRUM` tiap detik selama hitung mundur,
  `NOTE_BLOCK_SNARE` gulungan saat Tarian Pedang.
* Musik: `MusicCue.M_BOUT` saat intro duel resmi bila musik nyala (sudah dipakai turnamen), dilepas di akhir.

---------------------------------------------------------------------------------------------------------------------

### 35.7 AI duelist (permintaan butir 6)

#### 35.7.1 Kerangka

* `DuelistGoal extends Goal` (S) ditambahkan dengan prioritas 0 dan bendera `MOVE | LOOK` ke `goalSelector` setiap
  duelist non-pemain saat sesi dimulai (akses lewat mixin aksesor baru `mixin/duel/MobGoalSelectorAccessor`
  `@Accessor("goalSelector")`), dicabut di `ENDED`. Karena memegang `MOVE|LOOK`, `MeleeAttackGoal` murid
  (`DiscipleAi.CombatGoal`) dan perampok tidak berjalan selama duel; serangan dilakukan oleh `DuelistBrain` lewat API yang
  sama dengan pemain (`Fighters.input(entity, DuelInputKind, dir, tick)`).
* `DuelistBrain` berjalan tiap tick: (1) jaga jarak (gaya), (2) baca ayunan lawan yang **sudah terlihat** (35.7.3), (3)
  jadwalkan reaksi pada `tick + reactionTicks`, (4) pilih serangan dari tabel gaya, (5) dorong saat kunci bilah.

#### 35.7.2 Gaya (`DuelStyle`) — empat pedang Dugu Qiubai

| gaya | ilham | perilaku | angka khas |
|---|---|---|---|
| `AGRESIF` "Pedang Tajam" | pedang tajam masa muda Dugu: tajam, kuat, garang | rangkaian ringan 3, berat sering, maju terus, jarang jaga, menyerang begitu lawan pulih | serang 70%, tangkis 20%, tipuan 5%, mundur bila postur > 80% |
| `BERTAHAN` "Pedang Berat" | pedang berat tumpul: "kecerdikan tertinggi berdasar kesederhanaan" | jaga lama, membalas setelah tangkis, berat `GUNUNG` dan pemecah pertahanan, sabar | jaga 50%, tangkis 35%, berat 60% dari serangannya |
| `PENIPU` "Pedang Lunak" | pedang lunak lentur, ungu | tipuan, tusuk, langkah samping, mengubah ritme (jeda 0-8 tick acak sebelum impak rangkaian), Sapuan Rendah | tipuan 30%, tusuk 35%, samping 25% |
| `SANTO_PEDANG` "Tanpa Pedang" | setelah 40 tahun: rumput, kayu, batu pun pedang; Sembilan Pedang Dugu tanpa pola tetap | tanpa urutan tetap (pilihan acak berbobot yang menghindari 3 pilihan terakhirnya sendiri), membaca kebiasaan lawan (35.7.3), sering `PERFECT`, menunggu Celah, memakai Lepas Tiba-tiba dalam kunci | lihat tingkat |

Menyerah: `AGRESIF` tidak pernah; `BERTAHAN` di < 15% bila tanpa taruhan; `PENIPU` < 25%; `SANTO_PEDANG` memberi hormat
(`HORMAT`) dan menyerah bila postur jebol dua kali dan nyawa < 20% (kecuali taruhan).

#### 35.7.3 Membaca serangan (`AttackReader`) — tanpa curang

* Yang boleh dilihat AI = yang dilihat pemain: awal windup, jenis, arah, kelas senjata, bendera berbahaya (tanda 危),
  pose isi berat, jarak, postur yang tampil di HUD. **Tidak** boleh: rencana tipuan (bendera tipuan baru ada saat
  dibatalkan), masukan yang belum diterima server, hasil acak masa depan.
* Reaksi dijadwalkan pada `seen + reactionTicks` dan melewati jendela yang **sama** (`ClashTiming`), dengan `comp = 0`.
  AI tak pernah mendapat jendela lebih lebar daripada pemain; `HANDICAP` berlaku sama untuk AI.
* Kesalahan waktu tangkis: AI memilih tick tekan `impact − 1 + round(N(0, σ))`. Peluang sempurna muncul dari σ, bukan
  dari angka ajaib.
* Kebiasaan lawan (12 masukan terakhir): rata-rata selisih waktu tangkis lawan (terlalu cepat → AI menunda impak dengan
  berat 2-4 tick lebih lama), arah favorit (AI menyiapkan jaga), "selalu membalas ringan setelah tangkis" (AI menipu
  setelah tangkisnya), spam tangkis (AI menunggu `SPAM_LOCK` lalu menyerang). Hanya `SANTO_PEDANG` memakai semua; `PENIPU`
  memakai arah favorit dan spam; yang lain tidak membaca kebiasaan.
* Tipuan yang menipu AI: peluang AI "termakan" tipuan = `feintBite` (tabel), keputusan diambil saat tipuan terjadi.

#### 35.7.4 Kesulitan menurut ranah

Tingkat AI dari `Rank.step()` duelist: T0 Fana-Pemurnian Qi (0-4), T1 Fondasi-Inti Emas (5-12), T2 Jiwa Baru Lahir-
Transformasi Dewa (13-20), T3 Penyatuan Dao-Kenaikan Abadi (21-60), T4 Dewa Abadi (61-64).

| tingkat | reaksi (tick) | σ tangkis (tick) | termakan tipuan | dorong kunci (klik/s) | sempurna kira-kira |
|---|---|---|---|---|---|
| T0 | 7 | 2.0 | 0.6 | 5 | 20% |
| T1 | 6 | 1.6 | 0.5 | 6 | 27% |
| T2 | 5 | 1.2 | 0.4 | 7 | 37% |
| T3 | 4 | 0.9 | 0.3 | 8 | 50% |
| T4 | 4 | 0.7 | 0.2 | 9 | 62% |

`SANTO_PEDANG`: reaksi −1 (min 3), σ ×0.85, tipuan ×0.5. Murid dengan bakat Indra Pedang (`Talents.talents(data) &
PowerIds.TALENT_SWORD_SENSE`): σ ×0.9. Reaksi manusia ±200-250 ms = 4-5 tick, jadi T3/T4 setara pemain mahir, bukan
mesin. Boneka Duel (`PAIR_UJI`) memakai gaya dan ranah dari perintah.

#### 35.7.5 Dorongan kunci bilah dan teknik AI

AI mendorong dengan tabel klik/s ± 1 acak, memakai Lepas Tiba-tiba bila lawan mendorong ≥ 3/3 tick dan
`random < 0.15 × (tingkat + 1)` (`SANTO_PEDANG` ×2). Dengan `JURUS_BEBAS`, murid memakai
`DiscipleCultivation.tryCastInCombat(disciple, target)` paling sering sekali per 120 tick saat jarak > 5 b.

#### 35.7.6 NPC kultivator dan Dunia (DESIGN 34)

* `DuelistRegistry.register(EntityType<?> type, DuelistProfile.Provider provider)` (publik, S) dan antarmuka opsional
  `dev.kultivator.duel.api.Duelist` (`DuelistProfile duelProfile()`, `boolean acceptsDuel(ServerPlayer challenger)`,
  `void onDuelEnd(boolean won)`) — kultivator pengembara Dunia mengimplementasikan salah satunya; Duel tidak bergantung
  pada kelas Dunia (yang masih ditulis). Penyedia bawaan: `RogueCultivatorEntity` (gaya dari varian: liar →
  `AGRESIF`, hantu → `PENIPU`, energi → `BERTAHAN`, iblis → `AGRESIF`; tier 4 patriark → `SANTO_PEDANG`; peringkat dari
  `realm()`), `DiscipleEntity` (gaya dari jalur/bakat: Dao Pedang → `SANTO_PEDANG` bila pemahaman ≥ 60, selain itu
  `BERTAHAN`/`AGRESIF` menurut kepribadian bila ada, kalau tidak acak dari `soulId`).
* `DuelistProfile(DuelStyle style, int rankStep, WeaponClass weapon, int color, String voiceKiai, int honour,
  float surrenderAt, int stoneGrade, int stoneCount, @Nullable String slipSpec, boolean offersChallenges)`.
* NPC yang `offersChallenges` (pengembara ber-gaya `SANTO_PEDANG`/`AGRESIF`) dapat menawarkan duel kepada pemain dalam 8 b
  yang memegang bilah, paling sering sekali per 20 menit per pemain, lewat `DuelChallenges.npcOffer(npc, player)`
  (`DuelChallengeOffer` biasa; menolak tidak ada akibat).
* Renderer NPC Dunia ber-`PlayerModel` memanggil `PoseRenderData.extract` agar pose duel tampil (kewajiban modul Dunia,
  dicatat di kontrak; tanpanya NPC tetap bertarung dengan animasi vanilla).

---------------------------------------------------------------------------------------------------------------------

### 35.8 Jaringan, waktu, konfigurasi, perintah, panduan, sumber (permintaan butir 7)

#### 35.8.1 Payload (`dev.kultivator.duel.net.DuelPayloads`, didaftarkan `DuelNet.init` dengan `PayloadTypeRegistry`)

Tidak ada edit ke `network/Payloads` atau `Net` (pola `aura/AuraPreviews`, `voice/VoicePayload`). Ukuran tanpa header.

**C2S** (`PayloadTypeRegistry.serverboundPlay()`, penerima `ServerPlayNetworking.registerGlobalReceiver` di S):

| record | id | field | ukuran |
|---|---|---|---|
| `DuelInput` | `kultivator:duel_input` | `byte kind` (`SWING 0, CHARGE_START 1, CHARGE_RELEASE 2, PARRY 3, GUARD_ON 4, GUARD_OFF 5, FEINT 6, BIND_PUSH 7, BIND_RELEASE 8, DODGE 9, BC_SWING 10, SURRENDER_HOLD 11`), `byte dir`, `byte flags` (bit0 lari, bit1 maju, bit2 di udara, bit3 jongkok, bit4 tangan kiri BC, bit5 berat BC, bit6 prediksi sempurna — diagnostik, tak dipercaya), `short seq`, `short clientTick` (16 bit bawah perkiraan tick server), `byte arg` (jumlah dorongan / windup BC / arah hindar) | 8 B |
| `DuelChallenge` | `kultivator:duel_challenge` | `int targetId`, `int secondId` (−1; murid kedua untuk `PAIR_TONTON`), `byte realmRule`, `byte jurusRule`, `byte winRule`, `byte radius`, `int timeLimitTicks`, `byte stakeKind`, `byte stakeGrade`, `short stakeCount`, `String slipSpec` (≤ 64, `writeUtf(s, 64)`), `byte flags` (bit0 intro film, bit1 penonton) | ≤ 90 B |
| `DuelAnswer` | `kultivator:duel_answer` | `int challengeId`, `byte answer` (`TERIMA 0, TOLAK 1, MENYERAH 2, TARIK 3, TUNDA 4`), `byte arg` | 6 B |
| `DuelPrefs` | `kultivator:duel_prefs` | `byte kiai`, `byte inputMode`, `byte flags` (bit0 intro film, bit1 tangkis di luar duel, bit2 modul visual ada) | 3 B |

**S2C** (`PayloadTypeRegistry.clientboundPlay()`; dikirim ke peserta + `PlayerLookup.around(level, center, 64)`):

| record | id | field | ukuran / frekuensi |
|---|---|---|---|
| `DuelChallengeOffer` | `kultivator:duel_offer` | `int challengeId`, `int challengerId`, `Component name`, `Component rankLine` (ranah + gelar), aturan (5 byte), taruhan (`byte kind, byte grade, short count, Component slipName`), `short expiresTicks`, `byte pair` | ≤ 320 B, sekali |
| `DuelSessionSync` | `kultivator:duel_session` | `int sessionId`, `byte phase`, `byte pair`, `int aId`, `int bId`, `float cx, cy, cz`, `byte radius`, `float markAx, markAz, markBx, markBz`, aturan (5 byte), `int phaseTicksLeft`, `int fightTicksLeft`, `int serverTick`, `Component aName, bName, aRank, bRank`, `int aRgb, bRgb`, `byte flags` (intro film, penonton, keluar-gelanggang A/B) | ≤ 300 B; tiap ganti fase + tiap 40 tick + saat pemain masuk jangkauan |
| `DuelFighterSync` | `kultivator:duel_fighter` | `int entityId`, `short postureX10`, `short postureMaxX10`, `byte state` (`IDLE, WINDUP, STRIKE, RECOVER, GUARD, STAGGER, BIND, DODGE, RECOIL, HELD`), `byte flags` (celah, qi siap, pendamping, terhuyung-tanda 決, tangkis-acak), `byte chain`, `byte weaponClass` | 13 B; ≤ 10 Hz bila berubah; juga untuk mob berpostur dalam 32 b |
| `DuelSwing` | `kultivator:duel_swing` | `int entityId`, `short swingId`, `byte kind`, `byte dir`, `byte weaponClass`, `byte windup`, `int startTick`, `short flags` (`HEAVY, PERILOUS_GUNUNG, PERILOUS_MAUT, PERILOUS_SAPU, QI, FEINT_CANCEL, REJECTED, BC, CHAIN_NEXT, RIPOSTE`), `int qiRgb` | 18 B; per ayunan |
| `DuelClash` | `kultivator:duel_clash` | `int aId` (penyerang), `int bId`, `short swingA`, `short swingB` (−1), `byte outcome` (`HIT, WHIFF, BLOCK, DEFLECT, PERFECT, CLASH, CLASH_MENANG, BIND, BIND_MENANG_A, BIND_MENANG_B, BIND_LEPAS, BIND_RELEASE_TRICK, GUARD_BREAK, POSTURE_BREAK, DEATHBLOW, EVADE, MIKIRI, QI_DEFLECT, QI_REFLECT, COMPANION_DEFLECT`), `float x, y, z`, `byte dirA, dirB`, `byte power` (0-255), `int rgbA, rgbB`, `int serverTick`, `short seed`, `byte flags` (`DECISIVE, PREDICTED_MATCH`) | 42 B; per hasil |
| `DuelBindSync` | `kultivator:duel_bind` | `int aId`, `int bId`, `byte pressure` (−100..100), `byte ticksLeft`, `byte flags` | 11 B; tiap 2 tick selama kunci |
| `DuelResult` | `kultivator:duel_result` | `int sessionId`, `int winnerId`, `int loserId`, `byte reason` (`SEPULUH_PERSEN, PENENTU, KELUAR, MENYERAH, WAKTU, SERI, BATAL`), `short hitsA, hitsB, perfectA, perfectB`, `byte maxChain`, `int durationTicks`, `short honourDelta`, `Component title`, `Component stakeLine` | ≤ 240 B, sekali |
| `DuelPreview` | `kultivator:duel_preview` | `byte kind` (`SPARK, SLOWMO, INTRO, FINISHER, TRAIL, BIND, SHOCK, DECAL, ALL_POSES`), `int arg`, `float f`, `int rgb` | 13 B, perintah uji |

Anggaran: satu duel aktif ≈ 2 × (≤ 3 ayunan/s × 18 B + ≤ 2 hasil/s × 42 B + 10 × 13 B) + kunci 5 × 11 B ≈ 0.7 KB/s per
penonton, jauh di bawah batas.

#### 35.8.2 Model waktu klien dan anti-desinkronisasi

* `DuelClock` (U, klien): `serverTickNow(partial) = lastServerTick + (clientTicks − clientTickAtReceipt) + partial`,
  `offset` dihaluskan EMA 0.2 dari tiap `DuelSessionSync.serverTick`/`DuelSwing.startTick`, diabaikan bila melompat > 40
  tick (jeda server) lalu dipasang ulang.
* **Prediksi milik sendiri**: masukan ayunan langsung memulai pose dan pita lokal (`DuelPoses.predict(swingLocalId)`);
  `DuelSwing` balasan mencocokkan lewat `seq`: selisih ≤ 2 tick → biarkan, > 2 → pose diwarp 3 tick ke waktu server;
  `REJECTED` → pose melunak ke pemulihan, pita padam.
* **Prediksi tangkisan bertahan**: klien bertahan tahu impak lawan (dari `DuelSwing.startTick + windup` dalam waktu
  servernya) dan tick tekannya sendiri; bila jelas di dalam jendela (≥ 1 tick dari batas), langsung menampilkan percik
  `DEFLECT`/`PERFECT` + suara + hit-stop berlabel "prediksi". `DuelClash` yang cocok (`swingA` sama) tidak diputar ulang
  (bendera `PREDICTED_MATCH`); yang tidak cocok: percik prediksi dipudarkan 2 tick tanpa suara ulang, lalu hasil
  sebenarnya diputar (mis. `HIT` dengan guncangan). Kesalahan prediksi dicatat di `uji duel jendela`.
* Penyerang dan penonton tidak memprediksi hasil; mereka memutar `DuelClash` saat tiba, dan karena
  `resolveAt = impact + comp + 1`, jarak ke animasi strike ≤ 5 tick; pose strike penyerang menahan follow-through sampai
  hasil tiba (maks 6 tick) agar kontak terlihat di pose yang benar.
* Server: `seq` per pemain harus naik (mod 65536), duplikat dibuang; > 24 masukan/s dibuang (log sekali per menit);
  masukan dari fase yang salah ditolak diam; `clientTick` hanya untuk diagnostik — **tidak pernah** dipakai untuk waktu
  efektif (yang dipakai `tiba − comp` dari latensi server).
* Penyinkronan ulang penuh: saat ganti fase, tiap 40 tick, saat pemain mulai melacak peserta, setelah dimensi/relog.
* Posisi: gerak pemain tetap otoritatif klien seperti vanilla; server memakai posisinya sendiri untuk uji busur.

#### 35.8.3 Konfigurasi

Server `config/kultivator-duel.json` (`DuelConfig`, S; kunci tak dikenal dipertahankan):
`enabled` (true), `formalDuels` (true), `mobParry` (true), `mobPosture` (true), `armedMobClash` (true),
`guardDamageFactor` (0.4), `maxRewindTicks` (4), `answerSeconds` (60), `challengeRange` (24.0), `ringRadiusDefault` (9),
`ringOutTicks` (60), `timeLimitSeconds` (180), `maxSessions` (16), `allowStakes` (true), `maxStoneCount` (64),
`allowSlipStakes` (true), `honour` (true), `npcOffers` (true), `npcOfferMinutes` (20), `rewards` (true),
`introFilmMaxTicks` (100), `blockVanillaHitsInClassic` (true), `combatRollCompat` (true).

Klien (lewat `EffectQuality.get/put`, kunci baru; baris di Pengaturan, U): `duel_input` (otomatis), `duel_parry_outside`
(true), `duel_camera` (mati), `duel_intro` (true), `duel_slowmo` (true, juga tunduk "Gerak lambat"), `duel_hitstop`
(true), `duel_sparks` (penuh/ringan/mati), `duel_trails` (otomatis/selalu/mati), `duel_kiai` (mati), `duel_hud_world`
(false = bar di HUD, true = bar di atas kepala), `duel_crowd` (true), `duel_lockon` (false), `duel_pal` (false).

#### 35.8.4 Perintah

Pemain (`/kultivasi duel ...`, S): `tantang <entitas>` (pemain, murid, NPC yang ditatap; membuka layar tantangan bila
tanpa argumen), `terima`, `tolak`, `menyerah`, `tunda`, `hormat` (pose salam), `catatan [pemain]` (rekor dan
Kehormatan), `layar` (buka layar duel), `murid <murid1> <murid2>` (duel tonton).

Uji (`/kultivasi uji duel ...`, op, S kecuali disebut):

| perintah | fungsi |
|---|---|
| `boneka [gaya] [ranah] [tahap] [senjata]` | memunculkan Boneka Duel (RIVAL `spawnUnowned`, nama "Boneka Duel · gaya", `pedang_latih`) 6 b di depan dan memulai duel `PAIR_UJI` tanpa intro panjang |
| `boneka diam` / `boneka jaga` / `boneka serang <arah>` | boneka hanya berdiri / selalu jaga / mengulang satu arah tiap 30 tick (latihan tangkis) |
| `mulai <entitas> [aturan]` | memulai duel resmi langsung dengan entitas (lewati undangan) |
| `pasangan <a> <b>` | duel dua entitas (murid vs murid) dan pemain menonton |
| `stop` | membatalkan semua sesi pemain (`CANCELLED`) |
| `info` | sesi, fase, postur, rantai, latensi, komp, statistik prediksi |
| `jendela` | mencetak jendela efektif pemain (dengan HANDICAP), latensi, komp, 10 hasil terakhir dengan `impact − tEff` |
| `postur <0-100>` | menyetel postur sendiri (bagian) untuk menguji jebol |
| `ikat` | memaksa kunci bilah dengan lawan terdekat |
| `lambat [tick]` | `DuelPreview SLOWMO`: σ 0.35 dengan percik dan pita contoh (V) |
| `percikan [hasil] [warna]` | `DuelPreview SPARK`: hasil tertentu di 2 b depan mata, dua bilah maya bersilang (V) |
| `intro` / `akhir` | pratinjau sinematik intro / penutup dengan boneka maya (V) |
| `bilah` | garis debug pegangan-ujung tiap bilah (A) |
| `pose <id>` / `pose semua` | memutar pose duel pada diri / berurutan (A) |
| `bc` | mencetak animasi BC terakhir dan arah hasil tabel (U) |
| `gelanggang [radius]` | menggambar gelanggang di posisi pemain tanpa sesi (V) |
| `catatan reset [pemain]` | mengatur ulang `DuelRecords` |

`CheatHelp` mendapat grup `duel` dengan chip di atas (S).

#### 35.8.5 Kitab Panduan, hub, pencapaian

* Hub (DESIGN 33): kartu baru **"Duel Pedang"** di bagian Dunia (`HubCardsPage`, U) → `DuelScreen` (catatan, tantang
  yang terdekat, tantang murid, duel tonton, aturan, setelan cepat). Layar murid tidak diedit; murid dipilih dari
  `DuelScreen`.
* Langkah Kitab Panduan baru (U, `GuideChapters`, cek otomatis lewat `adv:` yang sudah didukung `GuideProgress`):

| id | bab | judul | cek |
|---|---|---|---|
| `c2.tangkis` | 2 Pemurnian Qi | Tangkis serangan monster | `adv:duel_first_deflect` |
| `c2.postur` | 2 | Postur dan Tebasan Penentu | `adv:duel_first_deathblow` |
| `c3.duel` | 3 Pembangunan Fondasi | Duel Pedang pertama | `adv:duel_first_win` |
| `c3.sempurna` | 3 | Tangkisan Sempurna | `adv:duel_first_perfect` |
| `c4.kunci` | 4 Inti Emas | Kunci Bilah | `adv:duel_bind_win` |
| `c4.bahaya` | 4 | Serangan Berbahaya: Injak Pedang dan lompat | `adv:duel_mikiri` |
| `c6.tonton` | 6 Murid | Menonton murid berduel | `adv:duel_disciples_watched` |
| `c12.duel_tips` | 12 Tips | Kesalahan umum duel (spam tangkis, menyerang saat lawan isi berat) | manual |

  Teks memakai token `[[key:parry]]` (`KeyHints.fill`, DESIGN 33.6 R11).
* Pencapaian baru (`data/kultivator/advancement/duel_*.json` dari `docs/ADVANCEMENT_TEMPLATE.json`, U; diberikan S lewat
  `Advancements.grant`): `duel_first_deflect`, `duel_first_perfect`, `duel_first_deathblow`, `duel_first_win`,
  `duel_bind_win`, `duel_mikiri`, `duel_chain_10`, `duel_disciples_watched`, `duel_honour_1300` (Pedang Tanpa Tanding),
  `duel_sword_saint` (mengalahkan `SANTO_PEDANG` T4).

#### 35.8.6 Sumber (diverifikasi 2026-10-22)

* Gu Long, *Juezhan Qianhou* (决战前后, seri Lu Xiaofeng): duel Ximen Chuixue melawan Ye Gucheng di atap Kota Terlarang
  pada malam bulan purnama, waktu dan tempat disepakati lebih dulu; jurus andalan Ye "Tian Wai Fei Xian" (天外飞仙); Ye
  tewas dan disebut sengaja kalah. → tantangan resmi dengan waktu/tempat, kartu judul nama dan gelar, satu tebasan penentu
  yang hening (diam 3 tick sebelum dentum). (wuxiasociety.com/ye-gucheng; en.wikipedia.org/wiki/The_Duel_of_the_Century)
* Jin Yong, *The Return of the Condor Heroes* (神雕侠侣): Makam Pedang Dugu Qiubai yang ditemukan Yang Guo, empat tahap:
  pedang tajam ("tajam, kuat, garang", sebelum 20), pedang ungu lentur (20-an, dibuang setelah tak sengaja melukai orang
  benar), pedang berat tumpul (30-an, "kecerdikan tertinggi berdasar kesederhanaan"), setelah 40 tanpa senjata (rumput,
  pohon, bambu, batu menjadi pedang). → empat gaya AI 35.7.2. (en.wikipedia.org/wiki/Dugu_Qiubai)
* Jin Yong, *The Smiling, Proud Wanderer* (笑傲江湖): Sembilan Pedang Dugu diajarkan Feng Qingyang kepada Linghu Chong;
  gerakannya tidak mengikuti urutan atau pola tetap sehingga lawan tak bisa menebak. → `SANTO_PEDANG` menghindari pola
  sendiri. (en.wikipedia.org/wiki/Dugu_Qiubai)
* Er Gen, *I Shall Seal the Heavens*, Buku 1 Bab 31 "Fight!": Meng Hao melawan Wang Tengfei di laga final latihan Sekte
  Luar di panggung, ditonton murid Sekte Luar; Meng Hao meluncurkan kelompok dua puluh pedang terbang lalu hujan sekitar
  empat puluh pedang. → arena sekte dengan penonton, pedang terbang pendamping. (wuxiaworld.com, issth-book-1-chapter-31)
* Yan Bi Xiao Sheng, *Emperor's Domination*: Li Qiye menyinggung duel pedang yang belum selesai dengan Bai Jianzhen dan
  menolak melanjutkannya. → hasil "Seri/Tunda" tanpa pemenang dan rekor tanding ulang. *Sumber hanya ringkasan wiki
  penggemar (emperors-domination.fandom.com/wiki/Bai_Jianzhen); bab persisnya belum diverifikasi — jangan mengutip
  nomor bab di teks game.*
* Desain game (bukan novel): *Sekiro: Shadows Die Twice* (FromSoftware, 2019) — bar Postur, tangkis (deflect), serangan
  berbahaya bertanda kanji 危, Mikiri Counter, deathblow; *For Honor* (Ubisoft, 2017) dan *Mount & Blade* — arah serangan
  dari gerak tetikus. Angka mod ini tidak disalin dari game mana pun.

---------------------------------------------------------------------------------------------------------------------

### 35.9 Modul dan kontrak (tepat empat, file tidak bertumpuk)

Urutan: **S** dulu (payload, `DuelIds`, `WeaponClass`, `SwingDir`, `SwingKind` dan API publik; stub dengan tanda tangan
persis cukup), **A** bagian bersama (`DuelPose`, `DuelPoseTable`, `BladeShape`, `BladeKinematics`, `BladeArcs`) bersamaan
dengan S, lalu **U** (`DuelBus`, `DuelClient`, `DuelClock`) dan **V** paralel. Siapa pun boleh memakai stub modul lain
dengan tanda tangan di bawah. Tak ada modul yang mengedit file modul lain.

#### Modul S: inti duel server (aturan, waktu, postur, AI)

File baru (semua `src/main/java/dev/kultivator/`): `duel/Duel.java`, `duel/DuelConfig.java`, `duel/DuelIds.java`,
`duel/DuelCommands.java`, `duel/DuelTests.java`, `duel/DuelHooks.java`, `duel/session/DuelSession.java`,
`duel/session/DuelSessions.java`, `duel/session/DuelChallenges.java`, `duel/session/DuelRuleSet.java`,
`duel/session/DuelRing.java`, `duel/session/DuelStakes.java`, `duel/session/DuelEscrow.java`,
`duel/session/DuelRecords.java`, `duel/clash/ClashClock.java`, `duel/clash/ClashTiming.java`, `duel/clash/Fighter.java`,
`duel/clash/Fighters.java`, `duel/clash/Swing.java`, `duel/clash/SwingDir.java`, `duel/clash/SwingKind.java`,
`duel/clash/WeaponClass.java`, `duel/clash/Exchange.java`, `duel/clash/Bind.java`, `duel/clash/Posture.java`,
`duel/clash/ClashDamage.java`, `duel/clash/HitTest.java`, `duel/clash/MobGuard.java`, `duel/clash/QiWave.java`,
`duel/clash/CompanionSword.java`, `duel/ai/DuelistGoal.java`, `duel/ai/DuelistBrain.java`, `duel/ai/DuelStyle.java`,
`duel/ai/AttackReader.java`, `duel/ai/DuelistProfile.java`, `duel/ai/DuelistRegistry.java`, `duel/api/Duelist.java`,
`duel/net/DuelPayloads.java`, `duel/net/DuelNet.java`, `mixin/duel/MobGoalSelectorAccessor.java`;
`src/main/resources/data/kultivator/damage_type/duel.json`, `data/kultivator/tags/damage_type/...` (tambah `duel` ke tag
`minecraft:bypasses_armor` lewat `data/minecraft/tags/damage_type/bypasses_armor.json` `replace: false`);
`lang-src/duel_inti.en_us.json` + `duel_inti.id_id.json` (pesan server, perintah, alasan, gelar, nama gaya).

Edit file lama (hanya S): `Kultivator.java` (satu baris `dev.kultivator.duel.Duel.init();` setelah
`DuniaContent.init()`), `command/CultivateCommand.java` (satu baris `dev.kultivator.duel.Duel.addCommands(root, uji);`
sebelum `TestCommands.addCommands`), `command/CheatHelp.java` (grup `duel`), `src/main/resources/kultivator.mixins.json`
(entri `"duel.MobGoalSelectorAccessor"`).

Kontrak:
* **C35.S1** `Duel.init()` mendaftarkan payload (`DuelNet.init`), peristiwa (ALLOW_DAMAGE fase `kultivator:duel_clash`
  sebelum default, `AFTER_DAMAGE`, `END_SERVER_TICK`, `LEAVE`, `AFTER_PLAYER_CHANGE_LEVEL`, `SERVER_STARTED` untuk
  penitipan), `Techniques.addCastBlocker(Duel::castBlocker)`, `DuelistRegistry` bawaan, lalu memanggil
  `dev.kultivator.sound.DuelSounds.register()` (U), `dev.kultivator.duel.compat.DuelCompat.init()` (U),
  `dev.kultivator.fx.DuelParticles.register()` (V), `dev.kultivator.duel.item.DuelItems.init()` (A).
* **C35.S2** API publik: `DuelSessions.byEntity(Entity) → @Nullable DuelSession`; `DuelSession.phase()`, `pair()`,
  `rules()`, `a()`, `b()`, `ring()`; `DuelChallenges.challenge(ServerPlayer, LivingEntity target, @Nullable LivingEntity
  second, DuelRuleSet) → Component` (null = terkirim, selain itu alasan tolak), `answer(ServerPlayer, int id, int answer)`,
  `npcOffer(LivingEntity npc, ServerPlayer)`; `Duel.surrender(LivingEntity)`; `Fighters.get(LivingEntity) → Fighter`
  (`posture()`, `postureMax()`, `state()`, `chain()`); `Fighters.input(LivingEntity, int kind, int dir, int flags, int
  arg, long tick)` (pemain lewat jaringan, AI langsung); `DuelHooks.onRoll(ServerPlayer, Vec3)`,
  `DuelHooks.telegraph(LivingEntity, int perilousKind, long impactTick)`, `DuelHooks.bcSwing(ServerPlayer, int dir, int
  windup, boolean heavy, boolean offhand)`; `DuelistRegistry.register(EntityType<?>, DuelistProfile.Provider)`.
* **C35.S3** Semua payload 35.8.1 persis (urutan field = urutan di tabel; `writeVarInt` untuk id entitas + 1 seperti
  `VoicePayload`, `writeShort`, `writeFloat`, `ComponentSerialization.TRUSTED_STREAM_CODEC` untuk Component). Konstanta di
  `DuelIds` (fase, pasangan, hasil, jenis masukan, bendera, alasan, `StakeKind`, `RealmRule`, `JurusRule`, `WinRule`).
* **C35.S4** Server memanggil suara lewat U: `DuelVoices.kiai(LivingEntity, int what)`, `DuelVoices.cheer(DuelSession,
  int moment)`, `DuelVoices.npcLine(LivingEntity, String line)`; semua suara benturan diputar klien (tidak ada
  `level.playSound` untuk benturan).
* **C35.S5** Tak pernah mengubah blok; tak pernah melambatkan tick server; semua modifier atribut transien dengan id
  `kultivator:duel_hold`, `kultivator:duel_guard`, `kultivator:duel_stagger`, `kultivator:duel_kind` dilepas di
  `ENDED`/logout/mati.

#### Modul V: visual klien (percik, pita, decal, gerak lambat, kamera, film intro/penutup)

File baru: `src/client/java/dev/kultivator/client/duel/fx/DuelFx.java` (init + pendengar `DuelBus`),
`ContactSolver.java`, `SparkBurst.java`, `DuelSparkParticle.java`, `DuelEmberParticle.java`, `MetalFlash.java`,
`BladeTrail.java`, `DuelTrailRenderer.java`, `ShockRing.java`, `ScorchDecal.java`, `DuelDust.java`, `DuelTime.java`
(hit-stop/gerak lambat), `DuelShake.java`, `DuelIntroFilm.java`, `DuelFinisherFilm.java`, `DuelCamera.java`,
`DuelRingVisual.java`, `DuelRopeRenderer.java`, `QiWaveVisual.java`, `CompanionSwordVisual.java`, `DuelMarks.java` (危 /
決 / panah tanda), `DuelPreviews.java`; `src/client/java/dev/kultivator/client/mixin/duel/DuelCameraMixin.java`; sisi
utama `src/main/java/dev/kultivator/fx/DuelParticles.java`; aset `assets/kultivator/particles/duel_spark.json`,
`duel_ember.json`, `textures/misc/duel/trail_gradient.png`, `scorch_slash.png`, `scorch_ring.png`, `shock_ring.png`,
`ink_arrow.png`, `rope.png`; `lang-src/duel_fx.en_us.json` + `duel_fx.id_id.json` (teks pratinjau, judul kartu).

Edit file lama (hanya V): `client/epic/EpicDirector.java` (fabrik publik `duelScene(int aId, int bId, Vec3 center, int
seed, int life)`, memakai `sceneFor(EpicIds.EXTRA, ...)` dan bendera lokal yang mengabaikan `bId`),
`client/epic/camera/ShotGuard.java` (field publik `static java.util.function.IntPredicate ignoreHostile = id -> false;`
diperiksa di `hostiles`).

Kontrak:
* **C35.V1** `DuelFx.init()` (dipanggil `DuelClient.init`, U) mendaftar ke `DuelBus` dan `LevelRenderEvents.COLLECT_SUBMITS`;
  pemilik provider partikel (`ParticleProviderRegistry`).
* **C35.V2** Memakai `BladeTracker.segment(int entityId, float partial) → @Nullable BladeSegment` (A) dan
  `BladeArcs.sample` (A) untuk kontak; memakai `DuelClock.serverTickNow(partial)` (U).
* **C35.V3** Semua efek 35.4 dengan anggaran 35.4.12; tak ada efek yang memodifikasi blok; tak ada post chain.
* **C35.V4** `DuelCamera` menghormati `CinematicDirector.active()`, F1, layar terbuka, `EffectQuality.reduceMotion()`
  (BAHU tetap, guncangan 0).

#### Modul A: animasi, model, tekstur, data bilah

File baru: sisi utama `src/main/java/dev/kultivator/duel/blade/DuelPose.java`, `DuelPoseTable.java`, `BladeShape.java`,
`BladeKinematics.java`, `BladeArcs.java`, `BladeSegment.java` (record `grip`, `tip`), `duel/item/DuelItems.java`; klien
`src/client/java/dev/kultivator/client/duel/anim/DuelAnim.java` (init), `DuelPoses.java`, `DuelPoseLibrary.java`,
`BladeProbeLayer.java`, `BladeTracker.java`, `FirstPersonDuel.java`, `PalBridge.java`, `BladeDebug.java`; aset
`assets/kultivator/items/pedang_latih.json`, `items/duel_post.json`, `models/item/pedang_latih.json`,
`models/item/duel_post.json`, `textures/item/pedang_latih.png`, `textures/item/duel_post.png`,
`assets/kultivator/player_animations/duel_*.json` (opsional), `data/kultivator/recipe/pedang_latih.json`,
`data/minecraft/tags/item/swords.json` (`replace: false`, hanya `kultivator:pedang_latih`), `tools/duel_pal_export.py`
(opsional); `lang-src/duel_gerak.en_us.json` + `duel_gerak.id_id.json` (nama item, nama pose untuk uji).

Edit file lama (hanya A): `technique/spec/dsl/Pose.java` (konstanta `DUEL` di akhir), `client/anim/PoseRenderData.java`
(tanya `DuelPoses.snapshot` lebih dulu, 35.5.1), `client/anim/AnimClient.java` (daftarkan `BladeProbeLayer` di callback
yang sudah ada, `DuelPoses.prune` tiap 20 tick, `DuelPoses.clear` di `clearAll`), `client/anim/FirstPersonCast.java`
(cabang duel), `client/musuh/RogueRenderer.java` (panggil `PoseRenderData.extract`),
`client/disciple/DiscipleHeldItemLayer.java` (helper statis `swordTransform(PoseStack)` dipakai lapisannya sendiri dan
`BladeProbeLayer`, tanpa perubahan tampilan).

Kontrak:
* **C35.A1** `DuelPose` 27 konstanta 35.5.2 (urutan tetap setelah rilis); `DuelPoseTable.key(DuelPose, int phase, float[]
  out)` dengan 19 kanal; `DuelPose.forSwing(SwingKind, SwingDir)`.
* **C35.A2** `BladeKinematics` dan `BladeArcs.sample(DuelPose, WeaponClass, float t01, Vec3 feet, float bodyYaw, float
  scale, boolean slim, boolean leftHanded) → BladeSegment` murni (tanpa kelas klien, aman dipanggil server tiap tick).
* **C35.A3** `DuelPoses.snapshot(Entity, float age) → @Nullable CastPoses.Snapshot` (jenis `Pose.DUEL`), mengikuti
  `DuelBus` (ayunan, hasil, kunci, status pejuang) dan σ `EpicClock`; aturan menyerah lengan BC 35.5.6.
* **C35.A4** `BladeTracker.segment(int entityId, float partial)` dan `BladeTracker.velocity(int entityId)` untuk V; data
  ≤ 10 frame umur.

#### Modul U: UI, suara, integrasi

File baru: klien `src/client/java/dev/kultivator/client/duel/DuelClient.java` (penerima semua payload S2C, cermin
keadaan), `DuelBus.java`, `DuelClock.java`, `DuelInput.java` (pembaca masukan, arah tetikus, prediksi),
`DuelKeys.java` (`parry`, `duel_camera` via `KeyMappingHelper`), `ui/DuelScreen.java`, `ui/ChallengeScreen.java`,
`ui/ChallengeOfferToast.java`, `ui/DuelHud.java` (bar postur kiri-kanan gaya Sekiro di atas hotbar dan di bawah boss bar
untuk lawan, meteran kunci yin-yang, rantai 一..十, hitung mundur 三 二 一 始, keluar-gelanggang, tanda 危/決 versi HUD,
tombol menyerah), `ui/DuelResultCard.java`, `ui/DuelSettings.java` (baris pengaturan), `sound/DuelSoundPlayer.java`,
`sound/DuelCrowd.java`, `compat/BetterCombatBridge.java`, `client/mixin/duel/DuelInputMixin.java` (`Minecraft.startAttack`
HEAD cancellable untuk KLASIK, `Minecraft.startUseItem` HEAD cancellable untuk tangkis/jaga; prioritas 900; tidak aktif bila
BC terpasang kecuali `startUseItem` saat tangan kiri kosong); sisi utama `src/main/java/dev/kultivator/sound/DuelSounds.java`,
`duel/compat/DuelCompat.java`, `duel/compat/CombatRollCompat.java`, `duel/voice/DuelVoices.java`; aset
`textures/gui/duel/posture_frame.png`, `posture_fill.png`, `bind_meter.png`, `seal_marks.png`;
`data/kultivator/advancement/duel_*.json` (10 berkas); `lang-src/duel_ui.en_us.json` + `duel_ui.id_id.json` (layar, HUD,
setelan, tombol, pencapaian, langkah Kitab Panduan `kultivator.guide.c*.duel*`/`tangkis`/`postur`/... judul dan isi,
subtitle suara).

Edit file lama (hanya U): `client/KultivatorClient.java` (satu baris `dev.kultivator.client.duel.DuelClient.init();`),
`src/client/resources/kultivator.client.mixins.json` (entri `"duel.DuelInputMixin"` dan `"duel.DuelCameraMixin"` milik V),
`client/screen/HubCardsPage.java` (kartu `duel` di bagian Dunia), `client/screen/SettingsRows.java` (baris duel di
Tampilan & Efek, Kamera & Film, Suara, HUD, Kontrol), `guide/GuideChapters.java` (langkah 35.8.5),
`src/main/resources/assets/kultivator/sounds.json` (peristiwa `duel.*` dengan `subtitle` `subtitles.kultivator.duel.*`).

Kontrak:
* **C35.U1** `DuelClient.init()` mendaftarkan penerima, `DuelKeys`, HUD (`HudElementRegistry.attachElementBefore(
  VanillaHudElements.CHAT, Kultivator.id("duel_hud"), ...)`, tersembunyi F1 dan saat `HudHider`), lalu memanggil
  `DuelFx.init()` (V) dan `DuelAnim.init()` (A).
* **C35.U2** `DuelBus` (statis, thread klien): `interface Listener { default void session(DuelPayloads.DuelSessionSync p)
  {} default void fighter(DuelFighterSync p) {} default void swing(DuelSwing p, boolean predicted) {} default void
  clash(DuelClash p, boolean predicted) {} default void bind(DuelBindSync p) {} default void result(DuelResult p) {}
  default void preview(DuelPreview p) {} default void correction(int swingId, int outcome) {} }`, `register(Listener)`;
  `DuelClient.session(int entityId)`, `fighter(int entityId)`, `partnerOf(int entityId)`, `inFight()`, `localMode()`.
* **C35.U3** `DuelClock.serverTickNow(float partial)`, `offsetStable()`.
* **C35.U4** `BetterCombatBridge` hanya refleksi: `Class.forName("net.bettercombat.api.client.BetterCombatClientEvents")`,
  `Publisher.register` dengan `java.lang.reflect.Proxy` untuk `PlayerAttackStart`; kegagalan → `available = false`.
  `CombatRollCompat` sama untuk `ServerSideRollEvents.PLAYER_START_ROLLING` (`Event.register`).
* **C35.U5** Suara 35.6 lewat `DuelSoundPlayer.clash(DuelClash)` dsb., dipanggil dari pendengar `DuelBus` U sendiri (V
  tidak memutar suara); `DuelSounds.register()` mendaftarkan semua `duel.*` di `BuiltInRegistries.SOUND_EVENT` sekali.
* **C35.U6** `DuelScreen`/`ChallengeScreen` bergaya giok (`screen/ui/Palette`, `TalismanButton`, `JadeCoin`,
  `JadeSlider`, `SealCard`), muat di 320 × 240, teks ≥ skala 0.6, tooltip di setiap ikon; Esc kembali ke hub bila dibuka
  dari hub (DESIGN 33 C33.N2 sudah menanganinya).

---------------------------------------------------------------------------------------------------------------------

### 35.10 Lang

Fragmen baru (tiap kunci di tepat satu fragmen, kedua bahasa bersamaan): `duel_inti` (S: `kultivator.duel.msg.*`,
`kultivator.duel.cmd.*`, `kultivator.duel.reason.*`, `kultivator.duel.title.*` gelar, `kultivator.duel.style.*`,
`death.attack.kultivator_duel` + `.player`), `duel_fx` (V: `kultivator.duel.fx.*`), `duel_gerak` (A:
`item.kultivator.pedang_latih`, `item.kultivator.duel_post`, `kultivator.duel.pose.*`), `duel_ui` (U:
`kultivator.duel.ui.*`, `kultivator.duel.hud.*`, `kultivator.duel.setting.*`, `key.kultivator.parry`,
`key.kultivator.duel_camera`, `advancement.kultivator.duel_*.title/.description`, `kultivator.guide.c2.tangkis.*` dan
langkah lain 35.8.5, `subtitles.kultivator.duel.*`). Istilah tetap: Postur, Tangkis, Tangkisan Sempurna, Kunci Bilah,
Tebasan Penentu, Celah, Rantai Pertukaran, Injak Pedang, Tebasan Gunung, Tusukan Maut, Sapuan Rendah, Gelombang Qi Pedang,
Pantulan Qi, Pedang Pendamping, Tekan Ranah, Kehormatan, Gelanggang. Inggris: Posture, Deflect, Perfect Deflect, Blade
Lock, Deathblow, Opening, Exchange Chain, Blade Step, Mountain Cleave, Death Thrust, Low Sweep, Sword Qi Wave, Qi
Reflection, Companion Sword, Realm Suppression, Honour, Ring.

---------------------------------------------------------------------------------------------------------------------

### 35.11 Uji (Indonesia)

**Dasar dan keamanan**
1. Pasang mod tanpa Better Combat: menyerang zombi di luar duel = vanilla persis (kecepatan serangan, kritikal, sapuan).
2. Klik kanan memegang pedang sambil menatap peti/pintu: peti/pintu tetap terbuka (tangkis tidak mencuri klik kanan).
3. Seluruh duel (termasuk gelombang qi, Tebasan Penentu, decal): tidak ada satu blok pun berubah (cek dengan
   `/kultivasi uji duel boneka` di atas rumput, salju, pasir, kaca); bekas tebasan tidak muncul di atas cairan/kaca/daun.
4. Tidak ada yang mati: kalah duel selalu berhenti di 10% nyawa; api/racun setelah duel tidak membunuh (masa rahmat).

**Tantangan dan aturan**
5. Pemain A menantang B dari layar duel dan dari `/kultivasi duel tantang B`: B melihat pop-up dan chip; menolak → A dapat
   pesan; setelah 60 s tantangan kedaluwarsa.
6. PvP dimatikan di server: tantangan pemain vs pemain ditolak dengan alasan jelas.
7. `GATHER`: panah tinta menuju tanda; NPC berjalan sendiri; bila ada creeper/zombi lain dalam 24 b → batal "tidak aman".
8. Intro: kamera (bila nyala) memotret lebar, bahu-ke-bahu dua arah, dua-shot; kartu judul menulis nama, ranah, gelar;
   daun sesuai bioma, angin, taiko, gong; WASD/Backspace melewati shot; kamera Mati → hanya kartu dan suara.
9. Hitung mundur 三 二 一 始; serangan sebelum 始 tidak berpengaruh.
10. Keluar gelanggang 3 s → kalah `KELUAR`; tali berpendar merah; kembali sebelum 3 s membatalkan hitungan.
11. Taruhan batu roh 10 tengah: batu hilang dari inventori saat intro, pemenang menerima 20; keluar server di tengah duel →
    batu kembali ke pemilik masing-masing; inventori penuh → batu diberikan setelah ada ruang.
12. Taruhan slip giok: pemenang menerima slip lawan dengan teknik yang benar.
13. `TEKAN_RANAH` Fana vs Inti Emas: jumlah tebasan untuk menang kira-kira sama untuk keduanya; `BEBAS`: yang lebih tinggi
    jauh lebih kuat tetapi pihak lemah mendapat bonus; `HANDICAP`: jendela tangkis pihak tinggi lebih sempit
    (`uji duel jendela`).
14. `PEDANG_SAJA`: G (rapal) ditolak dengan pesan; `JURUS_BEBAS`: teknik jalan, tusukan teknik terarah bisa ditangkis.
15. Menyerah (perintah, tombol HUD, jongkok-sujud 2 s) → kartu "Mengaku kalah", tanpa bingkai dampak.
16. Pemain ketiga memukul peserta: dibatalkan dengan pesan "jangan ikut campur".

**Mekanik benturan**
17. Boneka `boneka serang kanan`: tangkis tepat saat bilah hampir kena → percik terarah + dering; terlalu awal → kena;
    spam klik kanan → jendela mengecil (catatan `uji duel jendela`).
18. Tangkisan sempurna: dering panjang, hit-stop terasa, kilat emas, boneka terhentak; postur boneka naik.
19. Dua tebasan bertemu (`ikat` atau ayun bersamaan): percik di **titik di mana kedua bilah bersentuhan** (lihat garis
    `uji duel bilah`), bukan di tengah badan.
20. Kunci bilah: meteran yin-yang muncul, gesekan bunyi naik dengan tekanan, percik gesek bergeser di sepanjang bilah;
    mengklik lebih cepat menang; Lepas Tiba-tiba saat lawan mendorong membuatnya tersandung; 40 tick tanpa pemenang →
    keduanya terpental.
21. Tipuan: lawan (pemain kedua) melihat windup lalu batal; tipuan beruntun ditolak dalam 20 tick.
22. Tebasan Gunung (危): jaga → pertahanan jebol; tangkis → selamat. Tusukan Maut: hanya sempurna atau Injak Pedang (maju +
    tangkis) yang menghentikannya. Sapuan Rendah: lompat menghindar, lalu tebasan di udara memberi postur lebih.
23. Postur penuh → terhuyung 1.5 s, tanda 決; serangan = Tebasan Penentu, duel menang (aturan Penentu), sinematik penutup
    dengan bullet time, berlutut dan menyarungkan pedang, kartu 勝.
24. Rantai pertukaran: 一 二 三 四 → Tarian Pedang gerak lambat (paling sering sekali per 6 s).
25. Serangan qi: bilah berwarna Dao, gelombang qi meluncur, berhenti di dinding, pantulan qi saat ditangkis sempurna.
26. Pedang pendamping (Penguasaan pedang terbang ≥ 3): mengorbit, menangkis satu gelombang qi, menusuk saat lawan
    terhuyung.
27. Melawan zombi berpedang/wither skeleton: tangkis reaktif menolak pukulan; benturan dengan percik; elit/perampok punya bar
    postur; bos (Warden, Wither) tidak bisa dihabisi Tebasan Penentu.

**AI**
28. `boneka agresif|bertahan|penipu|santo` di T0 dan T4: gaya terasa berbeda (agresif terus maju, bertahan menunggu dan
    memecah pertahanan, penipu menipu dan menusuk, santo tak berpola dan menghukum spam tangkis).
29. AI tidak pernah menangkis tipuan sebelum tipuan terlihat; persentase sempurna T4 ≈ 60% (lihat `uji duel info`), T0 ≈ 20%.
30. Murid vs murid (`/kultivasi duel murid a b`): pemain menonton dengan kamera penonton; murid bersorak di arena sekte.
31. Perampok tier 1-3 bisa ditantang; patriark tier 4 bergaya santo; NPC menolak/menerima sesuai profil.

**Better Combat, PAL, Combat Roll**
32. Dengan Better Combat: animasi, jejak dan suara BC tetap sama; tidak ada jejak/desau ganda; tangkisan lawan terhadap
    tebasan BC menghasilkan percik di bilah animasi BC; `uji duel bc` mencetak arah yang benar untuk tiap animasi pedang,
    kapak, tombak, gada.
33. BC dua senjata: klik kanan tetap serangan tangan kiri BC; tangkis lewat tombol `parry` (C atau tombol samping).
34. Tanpa BC tetapi dengan PAL: tidak ada galat; `duel_pal` nyala tidak membuat crash bila refleksi gagal (log satu baris).
35. Combat Roll: berguling menghindari Sapuan Rendah/Tusukan Maut; tanpa Combat Roll, Langkah Bayangan (jongkok sambil
    bergerak) melakukannya.

**Jaringan**
36. Dua pemain dengan latensi buatan 150 ms: tangkisan yang ditekan saat bilah tampak mengenai di layar bertahan dinilai
    tangkis; 400 ms: dibatasi 200 ms (kadang terlambat), tidak ada percik ganda; koreksi prediksi memudar tanpa suara ulang.
37. Relog saat menonton duel: gelanggang, bar postur dan fase tersinkron kembali dalam 2 s.
38. Makro klik 50/s: dorongan kunci tetap dibatasi 10/s; tidak ada disconnect.

**Kinerja dan tampilan**
39. Sodium + Iris dengan paket shader: percik, pita, decal, kilat tampak; tidak ada artefak di bayangan; FPS di laptop lemah
    (Minimal) tetap ≥ 40 saat kunci bilah dengan 12 murid menonton.
40. Mutu Minimal: percik inti tetap terlihat (≥ 4), tanpa pita baja, tanpa bara; Maksimal: kilat lensa dan cincin jimat.
41. "Kurangi gerakan" nyala: tanpa hit-stop, tanpa gerak lambat, tanpa guncangan; kamera BAHU tetap.
42. F1: HUD duel, tanda 危/決 versi HUD, kartu judul tersembunyi.
43. GUI skala 2-4, jendela 854 × 480: layar tantangan dan HUD tidak bertumpuk.

**Panduan dan teks**
44. Kitab Panduan: langkah baru muncul di bab 2, 3, 4, 6, 12 dan tercentang otomatis lewat pencapaian; token tombol
    menulis ikatan `parry` yang sebenarnya.
45. Hub › Dunia › Duel Pedang membuka layar duel; Esc kembali ke hub.
46. Semua teks duel ada dalam bahasa Indonesia dan Inggris; `mergeLang` tidak gagal.
