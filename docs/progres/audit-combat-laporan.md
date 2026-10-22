# Audit Kesinambungan: Pertarungan & Kekuatan (audit:combat)

Lingkup: 169 teknik (seal/roda, biaya, cooldown, lockout pamungkas), film pamungkas, Dampak, Mode Kehancuran + Hancur
Total + perlindungan, Titah Langit, Duel Pedang + Better Combat/Combat Roll, binatang elit, bos purba, Bencana Bintang,
Murka Langit, Iblis Hati, aura, sinematik 7b / Film Spektakuler / Kosmik 3D, EffectQuality/EpicBudget.
Audit ini hanya membaca kode. Tidak ada file proyek yang diubah dan Gradle tidak dijalankan.
Kode Sekte yang Hidup (DESIGN 36, `dev.kultivator.sekte.**`) dan rencana Sekte Langit (37) diberi tanda **SEDANG DIBANGUN** / **RENCANA**.

## Ringkasan

| # | Tingkat | Temuan | Bukti utama |
|---|---|---|---|
| 1 | **TINGGI** | Musuh `dunia/**` (rival Kota, Kepiting Batu, Naga Liar, makhluk Reruntuhan) tidak memakai aturan "trial mob", jadi teknik memakai P penuh: di Dewa Abadi mereka mati sekali pukul, sedangkan elit/rogue setara butuh ±10 pukulan | `TechniquePower.java:677-681, 839-845`; `KultivatorNpc.java:198,205-207`; `KepitingBatuEntity.java:92-94` |
| 2 | **TINGGI** | Tidak ada satu daftar "sedang sibuk". Lima modul menulis daftarnya sendiri dan masing-masing ada yang terlewat, sehingga Iblis Hati bisa muncul di tengah Murka Langit/Bencana Bintang, dan Murka bisa turun saat ujian Iblis Hati berjalan | `HeartDemons.java:287-291`; `Purba.java:259-263`; `Batin.java:120-123`; `TreasureBirths.java:198-200`; `KetuaSekte.java:158` |
| 3 | SEDANG | `legacyD` tidak menghitung tahap (layer) Kenaikan Abadi. Melawan elit/rogue/Alam Abadi, sembilan tahap IA terasa datar (tidak ada kemajuan) | `TechniquePower.java:414-417` |
| 4 | SEDANG | Bencana Bintang milik pemain tidak punya beat sinematik, Hu Fa murid, maupun cincin petir. Bencana milik murid punya semuanya | `StarCalamity.java:292-330, 398-421`; `EpicHooks.java:213`; `Tribulation.java:514-534 vs 544` |
| 5 | SEDANG | Dua teks bantuan Kehancuran saling bertentangan (yang lama bilang "SATU-SATUNYA ... hanya murid", daftar perlindungannya berbeda, ada "Balai" dan "Aula" Sekte) | `kehancuran.id_id.json:3` vs `kehancuran_diri.id_id.json:36`; `ProtectedZones.java:35-51` |
| 6 | SEDANG | Ada tiga penjaga FPS yang bekerja sendiri-sendiri dan dua plafon quad terpisah (Duel 6000 + 7b 3000). Di laptop lemah, duel ditambah sinematik bisa melampaui batas | `DuelFxSettings.java:104-113,136`; `EpicBudget.java:21`; `EffectQuality.java:315-331` |
| 7 | SEDANG (RENCANA 37) | "Tantang Langit" di 37.7.4 memanggil `HeavenlyWrath.start` langsung. Metode itu instan, melewati semua penolakan dan gerbang `noticed` (pemain belum Dewa Abadi pun ikut terkena Murka) | `37-sekte-langit.md:1088-1089`; `HeavenlyWrath.java:143,177-215` |
| 8 | RENDAH | Pengaturan "Percikan duel" ditulis dua kali dengan nilai Ringan berbeda (0,4 vs 0,5) dan punya dua UI | `DuelSettings.java:78-90`; `DuelFxSettings.java:38-48`; `DuelScreen.java:222` |
| 9 | RENDAH | Nama ranah di aura tidak sama dengan nama resmi ("Pembentukan Fondasi", "Abadi Sejati") | `aura.id_id.json:8`; `aura_high.id_id.json:11` vs `core.id_id.json:4,10` |
| 10 | RENDAH | Nama teknik memakai "Kesengsaraan", sedangkan bagian lain memakai "Tribulasi" (±150 kali) | `arts_lightning.id_id.json:57,67`; `arts_sword.id_id.json:26` |
| 11 | RENDAH | Panduan menulis "169 teknik", padahal Kitab menghitung "x/168" | `guide.id_id.json:71`; `TechniqueSpec.java:542`; `TechniqueRegistry.java:227` |
| 12 | RENDAH | Ada periode kosong tanpa teknik baru: Kenaikan Abadi Tahap 8 nol teknik, Tahap 6 satu, Tahap 2 dua | `technique/arts/*.java` (`.realm(Realm.IMMORTAL_ASCENSION, n)`) |
| 13 | RENDAH (SEDANG DIBANGUN) | Duel perang Ketua Sekte memakai `DuelRules.begin` langsung, tidak lewat `DuelChallenges/DuelSessions` (tanpa intro, aturan, maupun gerbang sibuk) | `KetuaSekte.java:151-170` vs `BabakDuel.java` |

Jumlah: **KRITIS 0 · TINGGI 2 · SEDANG 5 · RENDAH 6**

Yang sudah dicek dan **aman** (tidak masuk temuan): gerbang `ShotGuard` (HP, musuh dekat, cairan, layar, Backspace), gerbang film per babak
(HP ≥ 50 %, tidak ada musuh dalam 24 b), Kamera Penuh/Singkat/Mati, lockout pamungkas 400 tick + GCD 10, cap Iblis Hati 6/10/12 %,
cap bos, hanya pemilik yang bisa melukai Iblis Hati, gerbang realm elit (Raja ≥ Inti Emas, Iblis ≥ Fondasi), gerbang bintang
Bencana yang konsisten dengan `ImmortalPerks.condense` (keduanya butuh `rank.isMax()`), Duel Pedang yang membungkus `DuelRules`
(lapisan clash tetap jalan), slowmo duel yang patuh pada `EpicSettings.slowmo` dan `reduceMotion`, Combat Roll lewat refleksi,
film yang hanya ada untuk pamungkas Dewa/Abadi (pamungkas Tian tidak punya film, sesuai DESIGN 30.3), dan Hancur Total yang
dikunci server `kehancuranTotalAllowed`.

---

## Detail

### 1. TINGGI: Dua skala kerusakan untuk musuh yang setara

**Bukti.** `TechniquePower.finalDamage` (`TechniquePower.java:839-845`) memakai `legacyPower × 1,5` hanya jika
`isTrialMob(target)` (`:677-681`), yaitu tag `dao_protector`, `trial_guard`, `elite`, `rogue`, atau `abadi_foe`. Musuh
fase dunia tidak memakai satu pun tag itu:
- `dunia/kota/KultivatorNpc.java:198`: HP = 24 × tier × (1 + 0,35·realm). Hanya tier 4 yang diberi `TAG_BOSS` (`:205-207`).
- `dunia/dimensi/laut/KepitingBatuEntity.java:92-94`: HP 90 + 70·tier, tanpa tag apa pun.
- `dunia/naga/NagaLiarEntity.java` (HP 600 × (1 + 0,8·over)) dan `dunia/reruntuhan/RCreature.java:97-98` (hanya `NoGrief` + tag sendiri; `TAG_BOSS` cuma di `:420`).

Karena itu teknik memakai P penuh. Contoh di Dewa Abadi: P = 6 × 400 × C × kualitas ≈ 4 000+ per koef
(`rankFactor`, `:360-369`). Rival Kota realm 8 tier 3 punya ±365 HP, jadi mati sekali pukul. Rogue Cultivator dengan realm
dan tier yang sama memakai `legacyD × 1,5` ≈ 36 per koef, jadi butuh ±10 pukulan. Di Inti Emas kedua skala masih hampir sama
(P ≈ 17, legacy ≈ 17). Selisihnya terus melebar tiap ranah.

**Kenapa memutus kesinambungan.** Musuh yang terlihat dan terasa sama (kultivator nakal di alam liar vs saingan di Kota, elit
vs Kepiting Batu) punya ketahanan yang berbeda puluhan kali lipat. Konten dunia tinggi jadi kosong tantangan, dan Duel Pedang
melawan rival Kota tidak berarti karena satu teknik langsung mengakhirinya.

**Perbaikan.** Pilih salah satu:
(a) Di setiap entitas dunia yang hostile dan berskala realm, tambahkan `TechniquePower.TAG_ROGUE` (kultivator) atau tag baru
`kultivator.dunia_foe`, lalu masukkan tag itu ke `isTrialMob` (`TechniquePower.java:679-680`). Entitas yang perlu diubah:
`KultivatorNpc.setup`, `KepitingBatuEntity.setup`, `UlarLautEntity`, `YakshaEntity`, `NagaLiarEntity.setup`, `RCreature`, dan
penjaga `TreasureGuardians:136`. Tag tidak memengaruhi save (ditulis ulang saat setup/load).
(b) Atau ganti tag ad hoc dengan satu API `TechniquePower.markRealmFoe(mob)` yang dipanggil semua modul.
Kerusakan bos tetap dibatasi lewat `bossShare`. Angka gameplay tidak berubah, karena yang berubah hanya klasifikasi target.

### 2. TINGGI: Daftar "sibuk" terpecah, aktivitas bisa bertumpuk

**Bukti.**
- `HeartDemons.canFace` (`HeartDemons.java:287-291`) hanya memeriksa `Breakthrough.isBusy`, `breakingThrough`, `Sparring.inDuel`,
  dan trial sendiri. Yang tidak dicek: `Purba.inCalamity/inWrath/inFight`, `DuelRules.inDuel`, sesi Duel Pedang, `Alam.inRun`,
  `SectRaids`, `InheritanceTrials`, `InnerCalamity`, `Abadi.inAscension`.
- `Purba.busy` (`Purba.java:259-263`) tidak memeriksa `HeartDemons.inTrial` maupun `InheritanceTrials.active`.
- `Batin.busy` (`Batin.java:120-123`) tidak memeriksa Iblis Hati, `DuelRules`, maupun Sparring.
- `TreasureBirths.busy` (`TreasureBirths.java:198-200`) hanya memeriksa 3 hal.
- `KetuaSekte.challenge` (`KetuaSekte.java:158`) hanya memeriksa `DuelRules.inDuel` (SEDANG DIBANGUN).

**Akibat nyata.** Membunuh 2+3+4 utusan Murka menambah tekanan Iblis Hati (`onKill`, `HeartDemons.java:299-313`). Jika
tekanan > 100, ada peluang 2 %/menit Iblis Hati muncul di tengah Duel Kehendak Langit. Dua bos bar dan dua DeathGuard
(`LANGIT` dan ambang 20 % Iblis Hati) berjalan bersamaan, dan kekalahan Iblis Hati memberi Qi Deviation di atas Luka Langit.
Kebalikannya juga bisa: senja tiba saat ujian Iblis Hati berjalan, lalu Murka dimulai (`refusal()` memakai `Purba.busy`).

**Perbaikan.** Buat `util/Sibuk.java` (kode umum, server) berisi `register(String id, Predicate<ServerPlayer>)` dan
`why(ServerPlayer) → @Nullable String`. Setiap modul mendaftarkan predikatnya di `init()`: Breakthrough/Tribulation,
HeartDemons, Purba (3), Alam, SectRaids, DuelRules, DuelSessions, Sparring, InheritanceTrials, InnerCalamity, Creation, Abadi,
Ascension, dan nanti juga Perang/KetuaSekte. Lalu ganti kelima daftar di atas dengan `Sibuk.why(player) != null`. Pesan
penolakan tetap memakai `kultivator.spine9.refuse.busy`, jadi tidak perlu kunci lang baru. Untuk sementara, tambahkan
`Purba.busy(player)` dan `DuelRules.inDuel(player)` ke `canFace`, serta `HeartDemons.inTrial(player)` ke `Purba.busy`.

### 3. SEDANG: Sembilan tahap Kenaikan Abadi datar melawan musuh "trial mob"

**Bukti.** `TechniquePower.java:414-417`: `D_legacy = (4 + 2,5·realm + 0,5·stage) × C`. Nilai `rank.layer()` tidak dipakai,
jadi IA Tahap 1 dan Tahap 9 pada stage yang sama memberi D yang persis sama (21,5–23 × C). Sementara itu `rankFactor` IA
tumbuh 1,3^layer (`:366`), dan HP elit/rogue mengikuti `realm` (bukan layer, `EliteMonsters.java:415`).

**Kenapa memutus kesinambungan.** IA adalah ranah terpanjang (9 tahap × 4 stage, XP × (1 + 0,3·layer)). Melawan elit, rogue,
dan musuh Alam Abadi, pemain tidak merasakan kemajuan selama sembilan tahap itu, padahal di Kitab angka P naik sekitar 8×.

**Perbaikan.** Di `legacyD`, tambahkan `+ 0,25 × rank.layer()` bila realm == IMMORTAL_ASCENSION, sehingga IA T9 setara
"setengah realm" lebih tinggi dan tetap di bawah TI. Lakukan hal yang sama di `legacyU` agar murid tetap setara. Perbarui
DESIGN 22.5 dan teks Kitab yang menampilkan D (`TechniqueCommands.java:440`).

### 4. SEDANG: Bencana Bintang pemain terputus dari sinematik dan murid

**Bukti.** `StarCalamity.start` (`StarCalamity.java:292-330`) dan `step` (`:398-421`) hanya mengirim `Fx WRATH_SKY/STRIKE` dan
bos bar. Tidak ada `EpicHooks` kecuali `EXTRA_PLAYER_TRAMPLING` di k = 7 (`:509`). Beat `CAL_OMEN…CAL_PASS/CAL_FAIL`
(`EpicIds.java:79-86`) hanya bisa dipicu lewat `EpicHooks.calamity(DiscipleEntity …)` (`EpicHooks.java:213`). Sambaran memakai
`Tribulation.strike(player, share)` (`Tribulation.java:514-534`), yang melewati `HuFaTribulation.beforeStrike` dan
`LightningRings` (keduanya hanya di sambaran tribulasi biasa, `:544-545`).

**Kenapa memutus kesinambungan.** Tujuh Bencana adalah puncak akhir permainan, tetapi presentasinya lebih sepi daripada
bencana muridnya sendiri dan lebih sepi daripada tribulasi Inti Emas. Murid Hu Fa yang di tribulasi biasa ikut menanggung
sambaran di sini cuma berdiri diam.

**Perbaikan.** (a) Tambahkan `EpicHooks.playerCalamity(ServerPlayer, beat, k, index, total)` yang memakai momen `CALAMITY`
dengan subjek pemain, lalu panggil saat omen (`start`), tiap sambaran (`step`), lulus/gagal (`finish`), dan batal (`abandon`).
Di klien, `SceneCatalogPower` cukup menerima subjek pemain. (b) Ganti `Tribulation.strike(player, share)` dengan varian
`Tribulation.strike(player, share, number)` yang memanggil `HuFaTribulation.beforeStrike` dan `LightningRings.impact`.
Angka tidak berubah (Hu Fa hanya membagi beban).

### 5. SEDANG: Teks bantuan Kehancuran saling bertentangan

**Bukti.**
- `kehancuran.id_id.json:3`: "SATU-SATUNYA bagian … tiga kunci … (2) Sumpah Kehancuran, (3) melepas segel merah seorang murid
  … Hanya murid …". Daftar perlindungannya: 64 Aula, 24 pemain, spawn, desa, formasi, peti/portal/batuan dasar/cairan.
- `kehancuran_diri.id_id.json:36`: "satu klik … jurusmu sendiri". Daftar perlindungannya: 4 blok di sekitarmu, 24 kasur dan
  pemain, 64 **Balai** Sekte, desa, peliharaan, murid. Pemulihan "hilang saat server dimulai ulang".
- Kode (`ProtectedZones.java:35-51`, `KultivatorConfig.java:62,64,114,116`): kastor 4, rumah/kasur 24, pemain 24, aula 64,
  penduduk desa/golem/formasi 32, murid/peliharaan/binatang roh 6, End 160.

**Kenapa memutus kesinambungan.** Pemain membaca dua aturan yang berbeda untuk fitur paling berisiko di mod ini. Teks lama
seolah mengatakan jurus pemain tidak pernah merusak blok. "Balai Sekte" hanya muncul 3 kali, sedangkan "Aula Sekte" 77 kali.

**Perbaikan.** Tulis ulang `kultivator.screen.help.kehancuran` (en_us + id_id, kunci sama) menjadi ringkasan bertingkat:
izin server → saklar diri / Sumpah murid → Hancur Total. Satu daftar perlindungan dengan angka dari config (4/24/24/64/32/6,
End 160) dan rujukan ke halaman "Kehancuran diri". Di `kehancuran_diri`, `kehancuran_semua`, dan `teknik`, ganti "Balai Sekte"
menjadi "Aula Sekte". Baris Sumber tidak disentuh.

### 6. SEDANG: Anggaran efek tidak satu pintu (laptop lemah)

**Bukti.** Ada tiga penjaga FPS: `EffectQuality` otomatis (rata-rata 5 dtk, `EffectQuality.java:315-331`), `EpicBudget`
(median 2 dtk, hanya selama adegan 7b), dan `DuelFxSettings.tick` (EMA, `:104-113`). Plafon quad duel 600/2400/6000
(`DuelFxSettings.java:136`) berdiri sendiri di luar `EpicBudget.QUADS` 400/1500/3000 (`EpicBudget.java:21`). FX teknik
(`client/fx/spec`), aura, purba, dan batin hanya memakai `EffectQuality.scale` tanpa penjaga bingkai.

**Kenapa memutus kesinambungan.** Saat finisher duel (adegan 7b lewat `EpicDirector.duelScene`) berjalan bersama FX duel,
total quad bisa mencapai 9 000 di Maksimal. Penurunan tier oleh satu penjaga juga tidak diketahui penjaga lain, sehingga
kualitas "melompat" antar fitur dalam satu pertarungan.

**Perbaikan.** Jadikan `EpicBudget` satu-satunya penjaga. `DuelFxSettings.tier()` membaca `EpicBudget.tier()` (hapus
`stepDown` dan `fpsAverage`), dan `DuelGeo` mengambil quad lewat `EpicBudget.takeQuads(n)` dengan plafon duel ≤ sisa plafon 7b.
`EffectQuality` otomatis cukup mengatur level dasar. Tambahkan satu baris di `/sinematik info` yang menampilkan tier efektif
untuk semua modul.

### 7. SEDANG (RENCANA 37): "Tantang Langit" memotong gerbang Murka

**Bukti.** `37-sekte-langit.md:1088-1089` meminta `HeavenlyWrath.start(player, false)` dijadikan `public` dan dipanggil untuk
"senja berikutnya". Padahal `start` (`HeavenlyWrath.java:200-215`) langsung memulai omen. Metode itu hanya mengecek Peaceful,
melewati `refusal()` (AFK, sibuk, menunggang, dimensi; `:177-197`) dan melewati gerbang `StarCalamity.noticed` (`:143`). Lawan
Panglima diskalakan dari `realmEff = 8 + bintang/9` (`Purba.java:254-256`), jadi pemain sekte langit yang belum Dewa Abadi
akan menghadapi Panglima tingkat TI.

**Perbaikan (untuk 37G).** Jangan buka `start`. Tambahkan `HeavenlyWrath.challenge(ServerPlayer)` (public) yang menulis
`wrath_next = hari ini` dan `wrath_skips = 0` di `extra.purba`, lalu membiarkan `dusk()` memulai Murka dengan semua penolakan.
Untuk pemain yang belum `noticed`, tolak dengan pesan baru (kunci lang en/id) atau sembunyikan tombol [Tantang Langit].

### 8. RENDAH: Pengaturan Percikan duel ditulis dua kali

`DuelSettings.Sparks` (`DuelSettings.java:78-90`) berisi `RINGAN(0.4F)`, tetapi pembaca sebenarnya `DuelFxSettings.sparks()`
(`:38-48`) mengembalikan 0,5. Hal yang sama terjadi pada enum `Camera` dan `Trails`, dan pengaturan muncul di dua UI
(`DuelScreen.java:222` serta hub lewat `SettingsRows.java:324`). **Perbaikan:** biarkan `DuelFxSettings` membaca
`DuelSettings.sparks().factor()` (satu sumber) lalu samakan nilainya (pilih 0,5, sesuai perilaku sekarang). Hapus enum
duplikat di `DuelFxSettings`.

### 9. RENDAH: Nama ranah di aura

`aura.id_id.json:8` "Aura Pembentukan Fondasi" dan `aura_high.id_id.json:11` "Aura Abadi Sejati", sedangkan nama resmi di
`core.id_id.json:4,10` adalah "Pembangunan Fondasi" dan "Dewa Abadi". "Abadi Sejati" juga muncul di `dunia_kota` (4),
`dunia_naga`, `tempa`, dan `hub` ("Dewa Abadi Sejati"). **Perbaikan:** samakan semuanya dengan nama di `core`.

### 10. RENDAH: Kesengsaraan vs Tribulasi

`arts_lightning.id_id.json:57,67,70` ("Pedang Petir Kesengsaraan Surgawi", "Formasi Kaisar Petir Kesengsaraan") dan
`arts_sword.id_id.json:26`, sedangkan layar tribulasi, panduan, dan combat memakai "Tribulasi" (±150 kali).
**Perbaikan:** "Pedang Petir Tribulasi Surgawi", "Formasi Kaisar Petir Tribulasi", "Seni Pedang Tribulasi Abadi". Kalimat
deskripsi juga diubah, tetapi kutipan judul di baris Sumber tidak diubah. "Kesengsaraan Binatang Roh" (`dunia_naga`) boleh
tetap dipakai sebagai nama ritual binatang, asal dijelaskan.

### 11. RENDAH: 169 vs 168

`guide.id_id.json:71` (dan en_us) menulis "169 teknik", sedangkan penghitung Kitab adalah "x/168" (`TechniqueSpec.java:542`,
`TechniqueRegistry.java:227`, karena `karma_domain` khusus murid dan Titah disembunyikan). **Perbaikan:** "168 teknik (ditambah
satu jurus khusus murid dan empat Titah Langit)".

### 12. RENDAH: Periode kosong teknik di Kenaikan Abadi

Sebaran gerbang (146 spec + 23 legacy): Fana 0, QR 2, FE 5, GC 27, NS 27, DT 24, DU 21. IA per tahap: T1 12, T2 2, T3 4, T4 7,
T5 3, **T6 1, T7 8, T8 0**, T9 3. TI 25. Tahap 8 IA adalah tahap kedua termahal, tetapi tidak membuka apa pun.
**Perbaikan (tanpa mengubah angka teknik):** geser 1–2 teknik T7 yang bukan pamungkas ke T8 (misalnya satu dari
Space/Shadow), atau beri T8 hadiah non-teknik yang terlihat di Kitab (mastery gate, slot seal). Bisa juga ditambahkan baris
panduan "T8: kuasai jurus T7".

### 13. RENDAH (SEDANG DIBANGUN): Duel perang Ketua Sekte

`KetuaSekte.challenge` (`KetuaSekte.java:151-170`) memakai `DuelRules.begin` langsung. Lapisan clash Duel Pedang tetap aktif
(`Fighters.java:155`), tetapi tidak ada intro film, aturan `DuelRuleSet` (HANDICAP/BEBAS), atau pemeriksaan sibuk (lihat #2).
Sementara itu `BabakDuel` turnamen sudah memakai `DuelChallenges/DuelSessions`. **Saran untuk tim sekte:** mulai lewat
`DuelChallenges` dengan aturan formal, sama seperti `BabakDuel`, agar ada intro dan kamera duel.

---

## Catatan integrasi untuk Sekte yang Hidup (36) dan Sekte Langit (37)
- Semua aktivitas baru yang mengunci pemain (perang, penyerbuan langit, utusan) sebaiknya mendaftar ke `Sibuk` (#2).
- Musuh baru Sekte Langit (Bajak Awan, Raksasa Longbo sebagai `RogueCultivatorEntity`) sudah memakai `TAG_ROGUE`, jadi aman
  dari #1. Musuh baru berbasis `KultivatorNpc` perlu tag (#1).
- Efek langit 37.6.7 sudah memakai `EpicBudget.takeQuads` dan plafon total di bawah `QUADS`, sesuai arah #6.
