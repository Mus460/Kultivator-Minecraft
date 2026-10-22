# Audit Tulang Punggung Progresi (audit:spine)

Tanggal: 2026-10-22. Audit **hanya baca**: tidak ada file proyek yang diubah selain laporan ini dan `docs/progres/audit-spine.md`. Gradle tidak dijalankan.
Cakupan: ranah, stage, lapis, sumber XP dan tempo, akar, Dao dan pemahaman, terobosan, tribulasi, Iblis Hati, kualitas, Perkokoh Fondasi, meditasi, urat, situs, formasi Qi, Gua Kediaman, bintang dan konten setelah batas, Pengakuan Langit, naik ke Alam Abadi, tablet Jalan, pelacak Langkah Pertama, Kitab Panduan (16 bab beserta cek langkahnya), dan advancement. Saya juga memeriksa ulang `audit_flow.md` yang lama dan perubahan keseimbangan sesudahnya (commit 60f6357). Semua temuan di bawah sudah dicocokkan dengan kode.

Sekte yang Hidup (DESIGN 36) sudah masuk kode (commit 40d06e1), tetapi pengerjaannya masih berjalan. Temuan di bagian itu ditandai **SEDANG DIBANGUN**. Sekte Langit (docs/rancangan/37) masih rencana, jadi temuannya ditandai **RENCANA**.

## Ringkasan

| Tingkat | Jumlah |
|---|---|
| KRITIS | 0 |
| TINGGI | 4 |
| SEDANG | 7 |
| RENDAH | 6 |

Tidak ada temuan KRITIS: tidak ada langkah wajib yang mustahil, tidak ada risiko kerusakan simpanan, dan semua advancement bisa diperoleh. Masalah terbesar ada pada **arah**. Kitab Panduan dan pelacak menunjuk ke tempat yang salah selama sebagian besar permainan. Iblis Hati dan Pengakuan Langit juga bisa memotong fitur lain yang sedang berjalan.

## Tabel temuan

| # | Tingkat | Judul | Bukti utama |
|---|---|---|---|
| S1 | TINGGI | "Bab sekarang" di Kitab Panduan macet di bab awal: satu langkah opsional, manual, atau langkah akhir permainan menahan seluruh penunjuk | guide/GuideProgress.java:126-135; guide/GuideChapters.java:99-119, 183-188, 214-271; lang-src/guide.id_id.json:37 |
| S2 | TINGGI | Pelacak Langkah Pertama macet di bab 5 bagi pemain tanpa sekte. Jalan Dunia dan Jalan Abadi hanya terbuka setelah bab sebelumnya 100% | goals/Guide.java:96-104; client/goals/GuideTracker.java:86-98 |
| S3 | TINGGI | Iblis Hati (tekanan) bisa muncul di tengah Duel Pedang, alam rahasia, pertarungan Purba, Bencana Bintang, dan ritual Abadi. Pindah dimensi = kalah = Qi Deviation | trial/HeartDemons.java:287-291, 464-466, 666-668; bandingkan musuh/Ambushes.java:213-214 |
| S4 | TINGGI | Batas meditasi ×3,5 menelan Formasi Sekte (dan nanti situs Sekte Langit). Fitur besar bab 8 hampir tidak menambah apa pun bagi pemain yang memakai bahan bakar atau situs | cultivation/Meditation.java:83, 373-380, 419-438; formasi/stack/Stacking.java:45-46 |
| S5 | SEDANG | Dua panduan paralel (Langkah Pertama 15 bab dan Kitab Panduan 16 bab) memakai nomor bab berbeda di satu widget | client/goals/GuideTracker.java:168-170, 224; goals/Guide.java:39-54 |
| S6 | SEDANG | Kualitas Kebangkitan: 50% Retak, Sempurna hanya dengan situs, Surgawi mustahil, padahal ikut rata-rata stat | cultivation/Breakthrough.java:609-626; data/BreakthroughQuality.java:51-59; data/CultivationData.java:374-388 |
| S7 | SEDANG | XP tempur dan tambang tetap (HP × 0,5), dan sebagian besar konten Dunia tidak memberi XP kultivasi. Dari Inti Emas ke atas jalurnya hanya meditasi | cultivation/XpSources.java:119-121; dunia/naga/DarahJiaoItem.java:49; bandingkan alam/AlamLoot.java:180, dunia/kota/KotaDuel.java:303 |
| S8 | SEDANG | Urutan Jalan Abadi terbalik: "naik ke Alam Abadi" (bab 11) diminta sebelum "Bencana Bintang 1" (bab 13), padahal bencana itu syarat ★9 | goals/Guide.java:116-120; purba/StarCalamity.java:39-41, 234-246; abadi/Ascend.java:198 |
| S9 | SEDANG | "Fondasi" punya enam arti dan dua ejaan ranah (Pembangunan dan Pembentukan) | lang-src/core.id_id.json:4; aura.id_id.json:8; goals.id_id.json:88, 227, 257; dpath.id_id.json:13-16, 351 |
| S10 | SEDANG | Gua Kediaman hanya mengakui Formasi Pelindung lama (Batu Magnet). Formasi Sekte tidak dihitung | cultivation/CaveAbode.java:123, 154; formation/ProtectiveFormations.java:133-145; formasi/catalog/LegacyMirrors.java |
| S11 | SEDANG (SEDANG DIBANGUN dan RENCANA) | Bab 15-16 (Sekte yang Hidup) dan rencana bab 17 (Sekte Langit) ditambah di ujung, jadi baru menjadi "bab sekarang" setelah Dewa Abadi dan Ujung Dunia | guide/GuideChapters.java:250-271; docs/rancangan/37-sekte-langit.md:1166-1181 |
| S12 | RENDAH | Teks bantuan tablet Jalan masih "20 langkah dalam 5 bab" (kenyataannya 60 langkah dalam 15 bab) dan tidak menyebut Kitab Panduan | lang-src/goals.id_id.json:249, goals.en_us.json:249 |
| S13 | RENDAH | Teks Perkokoh Fondasi menyebut biaya "bar Puncak ranahmu", padahal kode memakai ranah dengan kualitas terendah | cultivation/Reforge.java:52-60, 82-96; lang-src/guide.id_id.json:135 |
| S14 | RENDAH | Bintang pemain: +2 HP. Bintang murid: +4 HP +0,6 serangan. Rumus biayanya sama | cultivation/Stats.java:31, 104-105; disciple/path/Stars.java:45-50, 84-89 |
| S15 | RENDAH | Bencana Bintang masih memakai sambaran terakhir ×2 tanpa cincin atau pemulihan, padahal tribulasi sudah diseimbangkan ke ×1,5 dengan pemulihan 6% | purba/StarCalamity.java:55-57, 405; cultivation/Tribulation.java:60-75, 309-316, 595 |
| S16 | RENDAH | Hadiah Langkah Pertama bab 9 (Tomb Sigil) datang setelah langkah yang sudah butuh Heavenly Tomb | goals/Guide.java:109, 565-568 |
| S17 | RENDAH | Pengakuan Langit (30 detik, sinematik) memeriksa sparring lama tetapi tidak memeriksa Duel Pedang, alam, atau Purba | ascension/Ascension.java:279-283 |

---

## Detail

### S1 · TINGGI · "Bab sekarang" Kitab Panduan macet

**Bukti**
- `GuideProgress.currentChapter` (guide/GuideProgress.java:126-135) mengembalikan bab pertama yang punya **satu** langkah belum selesai. Nilai ini dipakai oleh baris pelacak "Panduan: Bab N" (client/goals/GuideTracker.java:224), sorotan emas di layar, tombol Home, dan `uji panduan info`.
- Bab 2 (Pemurnian Qi) berisi `formation3` = `adv:qi_formation_heavenly` (GuideChapters.java:115). Formasi itu butuh End Rod (dibuat dari buah korus dari The End) ditambah Suar atau Batu Magnet (cultivation/QiFormations.java:16-17, 38-42). Teks langkahnya sendiri mengakui "Tongkat End butuh buah korus dari The End: target nanti" (lang-src/guide.id_id.json:37). Jadi hampir semua pemain tertahan di "Bab 2" sampai pergi ke The End.
- Bab 2 dan 3 juga berisi langkah Duel Pedang `duel_first_deflect`, `duel_first_deathblow`, `duel_first_win`, dan `duel_first_perfect` (GuideChapters.java:118-119, 130-131). Pemain yang tidak berduel tidak pernah keluar dari bab 3.
- Ada 41 langkah manual (centang tangan). Bab 1 punya 4 (`scroll`, `hud`, `settings`, `jalan`; GuideChapters.java:99-106) dan bab 12 punya 10 (semuanya manual, :214-224). Pemain yang tidak pernah membuka buku akan selalu melihat "Bab 1".
- Urutan bab tidak cocok dengan isinya. Bab 8 meminta `tier:8` dan `ten_thousand_immortals` (formasi A1) (:183-184) sebelum bab 9 meminta `sect_tier_5` (:188). Bab 13 "Dunia yang Luas" (peta, kota, kedai teh, lelang: konten Pemurnian Qi atau Fondasi) baru menjadi "bab sekarang" setelah bab 11 Dewa Abadi dan bab 12 selesai (:225-235).

**Mengapa memutus alur:** buku bilang "kamu di Bab 2", padahal pemain sudah di Inti Emas. Toast "Bab selesai" juga muncul acak bertahun-tahun kemudian. Panduan, yang seharusnya menjadi benang merah, malah terasa melompat-lompat.

**Perbaikan konkret**
1. `guide/GuideChapters.java`: tambahkan field `boolean core` pada `Step` (atau overload `opt(...)`). Tandai sebagai opsional: semua langkah manual, `c2.formation3`, langkah duel (`tangkis`, `postur`, `duel`, `sempurna`, `kunci`, `bahaya`, `tonton`), `c8.seat`, `c8.a1`, `c9.level9`, dan bab 12 seluruhnya.
2. Tambahkan `Realm minRealm` per bab (bab 13 = QI_REFINING, bab 14 = GOLDEN_CORE, bab 15-16 = FOUNDATION ditambah syarat hall).
3. `GuideProgress.currentChapter(done, data)`: (a) bab pertama yang punya langkah **inti** belum selesai **dan** `minRealm` ≤ ranah pemain; (b) bab tanpa sekte (cek `hall`) dilewati sampai aula berdiri. Bit GuideSync tidak berubah, jadi simpanan aman (centang manual disimpan per id).
4. Pindahkan `formation3` ke bab 8 dengan id baru (`c8.formation3`). Dengan begitu `c2.formation3` lama hanya hilang dari tampilan. Ingat juga memindahkan kunci lang.
5. Perbarui javadoc `GuideChapters.chapter()` ("clamped to 0-13", :324), yang sudah usang karena sekarang ada 16 bab.

### S2 · TINGGI · Pelacak Langkah Pertama macet di bab 5 tanpa sekte

**Bukti:** bab 5 Langkah Pertama = `codex`, `sect` (sect_founded / first_recruit_hall), `disciple` (first_disciple), dan `expedition` (first_expedition) (goals/Guide.java:99-100). `GuideTracker.world()` hanya aktif bila `Guide.bits == ALL` (client/goals/GuideTracker.java:96-98). `abadi()` butuh `bits2 == ALL` (:91-93). Bit itu mencakup `w_tomb81`, `w_loyal`, `w_vassals`, `w_ring4`, dan `w_raid` (Guide.java:105-112), yang semuanya bergantung pada sekte, desa, atau makam.
**Mengapa:** pemain yang memilih jalan pertapa tunggal (yang sah menurut desain) melihat "Dirikan Sekte" di layar dari Fondasi sampai Dewa Abadi, dan pelacak tidak pernah menunjukkan Jalan Dunia atau Jalan Abadi.
**Perbaikan:** di `GuideTracker.world()/abadi()`, ganti syarat "semua bit sebelumnya" dengan syarat ranah (FOUNDATION untuk Dunia, TI Puncak untuk Abadi) ditambah "bab sebelumnya tinggal langkah sekte". Di `Guide.currentStep` (goals/Guide.java), lewati langkah sekte selama `Halls.hallOf(uuid) == null` (sisi server mengirim flag `noSect` lewat data yang sudah tersinkron). Format simpanan `guide/guide2/guide3` tidak berubah.

### S3 · TINGGI · Iblis Hati tekanan dan Pengakuan Langit tanpa penjaga "sedang sibuk" bersama

**Bukti:** `HeartDemons.canFace` (trial/HeartDemons.java:287-291) hanya memeriksa Creative/Spectator, `Breakthrough.isBusy`, `breakingThrough`, `Sparring.inDuel` (sparring lama), dan uji yang sedang berjalan. Ia **tidak** memeriksa `DuelSessions.byEntity` atau `DuelRules.inDuel` (Duel Pedang), `Alam.inRun/isRealmLevel`, `Purba.inFight/inCalamity/inWrath`, Bencana Bintang, `Abadi.inAscension/inAuction`, atau `Tribulation.isActive`. Pindah dimensi selama uji tekanan berarti `FLED` (:464-466), lalu `punishPressure` memberi Qi Deviation 0,6 (:666-668). Sebaliknya, `musuh/Ambushes.java:213-214` sudah memakai daftar sibuk yang lengkap. Celah serupa ada di Pengakuan Langit (S17).
**Mengapa:** di tengah duel formal atau alam rahasia, iblis muncul, duel atau alam menjadi kacau, dan saat pemain keluar portal ia dihukum. Ini menabrak aturan "jangan ganggu saat bahaya nyata".
**Perbaikan:** buat `util/Busy.java` (server) dengan `reason(ServerPlayer)` yang menggabungkan daftar dari Ambushes ditambah `DuelSessions.byEntity(player) != null`, `Purba.inFight/inCalamity/inWrath`, `Abadi.inAscension/inAuction`, dan `StarCalamity` run. Pakai di `HeartDemons.canFace`. Saat sibuk, peringatan **ditunda** (`WARNINGS.put(id, 20)`) dan tidak dibuang. Untuk uji *tekanan*, pindah dimensi dihitung `CANCELLED` (tanpa hukuman), bukan `FLED`; uji terobosan tetap seperti sekarang. Pakai `Busy` yang sama di `Ascension.canBegin` dan `Ambushes`.

### S4 · TINGGI · Batas ×3,5 menelan Formasi Sekte

**Bukti:** `gainPerSecond = base × min(3,5, situs × formationTerm × bahanBakar) × …` (cultivation/Meditation.java:373-380; batas di :83). Formasi Sekte hanya menaikkan `formationTerm` sampai 2,6 atau 2,8 (formasi/stack/Stacking.java:45-46; Meditation.java:419-438). Contoh angka (bahan bakar terendah 1,5; Meditation.java:104):
- Formasi pribadi Bintang Surgawi 2,2 × 1,5 = 3,3. Ditambah Formasi Sekte, 2,6 × 1,5 = 3,9, lalu terpotong menjadi 3,5, jadi hanya **+6%**.
- Dengan situs gunung (1,5) dan tanpa bahan bakar: 2,2 × 1,5 = 3,3, lalu 2,6 × 1,5 = 3,9, juga terpotong menjadi 3,5.
- Dengan bahan bakar dan situs sekaligus, Formasi Sekte memberi **0%**.

Teks Kitab sendiri sudah mengakui ini ("batas meditasi ×3,5", lang-src/guide.id_id.json:151). Rencana Sekte Langit (37.7.1) menambah situs pulau 1,875, yang juga akan tertelan.
**Mengapa:** bab 8 (Formasi Sekte) dan bab 17 yang direncanakan menjual pengumpulan qi, tetapi pemain yang sudah serius (memakai bahan bakar dan situs) tidak mendapat apa-apa. Ini hadiah tak berharga persis di titik yang dijanjikan.
**Perbaikan (pilih satu):** (a) di `Meditation.gainPerSecond`, keluarkan faktor sekte dari `min`, yaitu `base × min(3,5, situs × kursi × bahanBakar) × min(GATHER_SECT_CAP 1,5, G)`, dan pertahankan `FORMATION_STACK_CAP` untuk tampilan. (b) Naikkan `MULTIPLIER_CAP` hanya di dalam medan formasi sekte (`STACK_CAP` provider menjadi "cap meditasi"). Nilainya, misalnya, 3,5 → 4,2 dengan A1. Perbarui `guide.c8.seat.body` dan DESIGN 24.5. Angka gameplay lain tidak berubah.

### S5 · SEDANG · Dua panduan, dua nomor bab

**Bukti:** pelacak menggambar bab Langkah Pertama (manik-manik, segel "Bab N") **dan** baris "Panduan: Bab N" dari Kitab (client/goals/GuideTracker.java:168-170, 224). Langkah Pertama punya 15 bab (goals/Guide.java:39-54, `ALL_CHAPTERS = 15`) dan Kitab punya 16 bab. Pada saat yang sama pemain bisa melihat "Bab 5" (Langkah) dan "Panduan: Bab 2" (Kitab).
**Perbaikan:** jadikan Kitab satu-satunya sumber "bab". Di pelacak, segel Langkah diberi label "Langkah" dan Kitab diberi label "Kitab", atau Langkah Pertama dipetakan ke id langkah Kitab (tabel `Guide.STEPS[i] → GuideChapters id`) sehingga hanya ada satu daftar. Hadiah bab Langkah tetap diklaim lewat tablet Jalan.

### S6 · SEDANG · Kualitas Kebangkitan hampir selalu buruk

**Bukti:** untuk `Kind.AWAKENING`, bonus = `siteBonus` saja (Breakthrough.java:618), penyedia bonus dilewati (:610-611), dan rumusnya `roll × 0,7 + bonus` (:625). Ambangnya Retak < 0,35, Sempurna ≥ 0,7, Surgawi ≥ 0,9 (data/BreakthroughQuality.java:51-59). Akibatnya **50% Retak**, Sempurna hanya dengan situs atau formasi, dan Surgawi mustahil (maksimal 0,7 + 0,05 + 0,09 = 0,84). `qualityFactor` merata-ratakan kualitas Pemurnian Qi ke stat (data/CultivationData.java:374-388), sedangkan `Ascension.flawlessPath` sengaja mengecualikannya (ascension/Ascension.java:194-203). Kebangkitan adalah terobosan pertama dan dijanjikan "selalu berhasil", tetapi separuh pemain langsung mendapat label merah "Retak".
**Perbaikan:** di `Breakthrough.succeed`, untuk AWAKENING pakai `max(NORMAL, …)` (Retak hanya bila ada Deviation), atau `roll × 0,5 + 0,3 + siteBonus`. Alternatifnya, lewati Pemurnian Qi di `qualityFactor` seperti di `flawlessPath`.

### S7 · SEDANG · XP di luar meditasi tidak berskala

**Bukti:** XP bunuh = HP maks × 0,5 (×2 untuk bos) (cultivation/XpSources.java:119-121). Satu zombie memberi 10 XP, sementara satu stage Inti Emas butuh 1.500-3.075 XP dan Dewa Abadi 250.000+ (data/Rank.java:76-77). Saran #6 dari `audit_flow.md` belum dikerjakan. Konten Dunia (menara 100 lantai, reruntuhan, penjara, Sepuluh Pengadilan, laut) tidak memanggil `Progression` sama sekali. Darah Jiao memberi 600 XP tetap (dunia/naga/DarahJiaoItem.java:49). Sebaliknya, AlamLoot (alam/AlamLoot.java:180), KotaDuel (dunia/kota/KotaDuel.java:303), dan Bencana Bintang (`Stars.grantShare`, ascension/Stars.java:79-83) sudah memakai bagian dari bar.
**Mengapa:** fitur petualangan terasa terputus dari kultivasi, karena dari Inti Emas ke atas hanya meditasi AFK yang menaikkan ranah.
**Perbaikan:** tambahkan `XpSources.milestone(player, share)` yang memanggil `Stars.grantShare`. Panggil pada tonggak Dunia: menara per 10 lantai 0,03; reruntuhan selesai 0,05; pengadilan 0,04; penjara disegel 0,1. Atur juga `combatXp × clamp(rank.xpRequired()/4500, 1, 8)`. `DarahJiaoItem.XP` diganti dengan bagian bar 0,05.

### S8 · SEDANG · Urutan Jalan Abadi terbalik

**Bukti:** STEPS3 bab 11 diawali `a_realm` = `immortal_realm` (goals/Guide.java:116), sedangkan `a_calamity` = `star_calamity_1` baru muncul di bab 13 (:120). Bencana 1 menggerbang bintang ke-6, 7, atau 8 (purba/StarCalamity.java:39-41, 234-246), dan naik ke Alam Abadi butuh ★9 (abadi/Ascend.java:198). Kitab sudah benar urutannya (c11 stars, lalu calamity, lalu nine, lalu ascend; guide/GuideChapters.java:208-211).
**Perbaikan:** jangan mengubah urutan larik, karena bit disimpan per indeks di `extra.goals.guide3`. Cukup ubah `Guide.currentStep` agar memilih langkah pertama yang **bisa dikerjakan**: lewati `a_realm` selama `star_calamity_1` belum selesai. Bila larik harus diurutkan ulang, sertakan migrasi bit di `Guide.load`.

### S9 · SEDANG · "Fondasi" bermakna enam hal

**Bukti:** ranah "Pembangunan Fondasi" (lang-src/core.id_id.json:4), nama aura "Aura **Pembentukan** Fondasi" (aura.id_id.json:8), "Fondasi Kokoh" untuk advancement dan efek tiga Ujian ranah (goals.id_id.json:88, 227, 257), "Ujian Fondasi" di bantuan tablet (goals.id_id.json:249), "Perkokoh Fondasi" untuk ritual kualitas (guide.id_id.json:134), dan "Fondasi Dao" atau "Fondasi Sempurna" untuk jalur murid (dpath.id_id.json:13-16, 351). Ujian ranah Inti Emas sampai Dewa Abadi bernama "Fondasi" padahal tidak terkait ranah Fondasi.
**Perbaikan:** ubah nama aura menjadi "Aura Pembangunan Fondasi" (hanya kunci `.name`; baris `Sumber` dibiarkan utuh). Ganti "Fondasi Kokoh" dan "Ujian Fondasi" untuk ujian ranah menjadi "Ujian Ranah" / "Persiapan Matang" (en: "Realm Trials" / "Well Prepared"), dengan kunci yang sama di kedua bahasa. Biarkan "Perkokoh Fondasi" dan "Fondasi Dao" (konsep sumber).

### S10 · SEDANG · Gua Kediaman tidak mengakui Formasi Sekte

**Bukti:** `CaveAbode.bonusActive/set` hanya memakai `ProtectiveFormations.insideActiveFormation` (cultivation/CaveAbode.java:123, 154). Fungsi itu hanya membaca `FormationData` lama (formation/ProtectiveFormations.java:133-145). LegacyMirrors menegaskan bahwa formasi lama tidak pernah dideteksi oleh Formasi Sekte.
**Mengapa:** pemimpin sekte dengan formasi pertahanan sekte penuh tetap mendapat "gua tidak terlindung" dan harus memasang Batu Magnet lama.
**Perbaikan:** tambahkan provider `ProtectiveFormations.addProtectionProvider((level,pos)->bool)`, lalu daftarkan di `formasi/integrate/ZoneBridge` untuk formasi sekte kelompok pelindung yang berdaya. Hal ini otomatis juga berlaku untuk Tribulation.java:272 dan HallBridge.

### S11 · SEDANG · SEDANG DIBANGUN dan RENCANA · Bab sekte ditaruh di ujung

**Bukti:** bab 15 "Murid yang Hidup" dan bab 16 "Sekte di Dunia" (guide/GuideChapters.java:250-271) berisi konten yang sudah bisa dimainkan sejak aula berdiri (bab 5-6). Karena aturan S1, bab ini baru menjadi "bab sekarang" setelah bab 14 Ujung Dunia (`tower_100`, `dragon_king`). Rencana 37.7.8 menambah bab 17 Sekte Langit dengan gerbang ranah Inti Emas sampai Penyatuan Dao (37.3.3), jadi masalahnya sama. Situs pulau (37.7.1, 1,5 × 1,25 = 1,875) juga akan tertelan batas 3,5 (lihat S4).
**Perbaikan:** gunakan `minRealm` per bab dari S1 (bab 15-16: aula ada; bab 17: Inti Emas dan `hall_level:6|tier:6`). Untuk 37G: tambahkan langkah di akhir daftar seperti rencana (bit aman). Di catatan 37.7.1, sebutkan bahwa faktor situs hanya berarti bila S4 sudah diperbaiki. `GuideScreen` sudah dinamis (`GuideBook.chapterCount()`), jadi cadangan "bab 16" di 37.7.8 tidak diperlukan.

### S12 · RENDAH · Bantuan tablet Jalan basi

**Bukti:** `kultivator.screen.help.jalan` masih berbunyi "20 langkah dalam 5 bab" (lang-src/goals.id_id.json:249, en :249), padahal sekarang 60 langkah dalam 15 bab, dan teks itu tidak menyebut Kitab Panduan atau tombolnya.
**Perbaikan:** perbarui kedua bahasa. Sebutkan tiga jalur (Langkah Pertama, Jalan Dunia, Jalan Abadi) dan rujuk Kitab Panduan.

### S13 · RENDAH · Teks biaya Perkokoh Fondasi

**Bukti:** kode menghitung biaya dari ranah **target**, yaitu ranah dengan kualitas terendah (Pemurnian Qi lebih dulu bila seri; cultivation/Reforge.java:52-60, 82-96). Teks Kitab berbunyi "3 × kebutuhan bar Puncak ranahmu" (lang-src/guide.id_id.json:135).
**Perbaikan:** ubah teks menjadi "…bar Puncak ranah yang diperbaiki (kualitas terendahmu)". Pertimbangkan juga agar Pemurnian Qi dilewati bila S6 diperbaiki.

### S14 · RENDAH · Bintang pemain lebih lemah daripada bintang murid

**Bukti:** pemain mendapat +2 HP per ★ (cultivation/Stats.java:31, 104-105). Murid mendapat +4 HP, +0,6 serangan, dan +4% jurus per ★ (disciple/path/Stars.java:45-50). Kurva biayanya `Stars.cost(n)` sama (:84-89; ImmortalPerks memakai kelas yang sama).
**Perbaikan:** naikkan `Stats.HEALTH_PER_STAR` ke 4 dan tambahkan serangan, atau tulis di `guide.c11.stars.body` bahwa bintang pemain menguatkan jurus (`TechniquePower.playerStarFactor`), sehingga perbedaannya disengaja dan dijelaskan.

### S15 · RENDAH · Bencana Bintang belum mengikuti penyeimbangan tribulasi

**Bukti:** sambaran terakhir ×2 (purba/StarCalamity.java:57, 405), tanpa cincin dan tanpa pemulihan. Tribulasi IA/TI sudah diubah menjadi akhir ×1,5, pemulihan 6% per sambaran yang diserempet, dan batas 2,2 × HP (cultivation/Tribulation.java:60-75, 309-316, 595). Bencana 7 = 12 × 0,15 + 0,30 = 2,10 × HP (masih di bawah batas, tetapi aturannya berbeda).
**Perbaikan:** `LAST_STRIKE` menjadi 1,5, atau pakai `Tribulation.HIGH_TOTAL_CAP` dan cincin `LightningRings` yang sama.

### S16 · RENDAH · Hadiah Langkah bab 9 terlambat

**Bukti:** bab 9 (Jalan Dunia) berisi `w_tomb9` = `heavenly_tomb_9` (goals/Guide.java:109), tetapi hadiahnya `tomb_sigil`, yaitu kunci Heavenly Tomb (:565-568).
**Perbaikan:** tukar hadiah bab 8 dan 9 (sigil di bab 8, Supreme ditambah HIGH di bab 9). Bit klaim tidak berubah.

### S17 · RENDAH · Pengakuan Langit tanpa penjaga lengkap

**Bukti:** `Ascension.canBegin` (ascension/Ascension.java:279-283) hanya memeriksa sparring lama, Iblis Hati, dan terobosan. Peristiwa 30 detik dengan sinematik bisa mulai di tengah Duel Pedang atau pertarungan Purba. Kamera klien punya penjaga bahaya sendiri, jadi dampaknya kecil.
**Perbaikan:** pakai `util/Busy` dari S3.

---

## Yang diperiksa dan sudah tersambung baik

- **Advancement:** ada 338 file (tanpa resep) dengan satu `root`. Semua `parent` ada, semua diberikan kode (termasuk `realm_<id>` dan `<primordial>_slain` lewat penggabungan string), dan kunci judul serta deskripsi lengkap di en dan id.
- **Cek Kitab:** semua cek `adv:` di GuideChapters punya JSON dan pemberi. Ke-20 advancement `sekte/*` untuk bab 15-16 sudah ada dan diberikan. Semua kata perintah petunjuk (`/kultivasi …`) terdaftar sebagai literal. Semua kunci `kultivator.guide.<id>.title/body` ada di kedua bahasa.
- **Perubahan keseimbangan dari audit lama (60f6357)** sudah ada di kode: batas tribulasi 2,2, cincin mulai dari Jiwa Baru Lahir, pengurasan urat 0,5 di bawah Transformasi Dewa, situs untuk pencerahan dibatasi 2,0, dan pengembalian sesaji ritual Alam Abadi (abadi/Ascend.java:249-277). Yang **belum**: #5 bahan bakar, #6 XP tempur (lihat S7), dan #8 pertumbuhan biaya bintang 1,07.
- **Dao:** dipilih mulai Inti Emas (dao/DaoPaths.java:100). Pemahaman menggerbang slip giok dan penguasaan (technique/source/JadeSlips.java:232, technique/spec/run/Mastery.java:71). Situs yang menyuburkan Dao baru berguna setelah Dao dipilih. Itu konsisten, dan guide c4.dao ditaruh pada waktu yang tepat.
- **Simpanan:** bit GuideSync dihitung ulang setiap kali sinkron, dan centang manual disimpan per id, jadi menyisipkan langkah duel di tengah daftar aman. Bit Langkah Pertama disimpan **per indeks**, jadi jangan mengurutkan ulang `STEPS/STEPS2/STEPS3` tanpa migrasi (lihat S8).

## Urutan perbaikan yang disarankan

1. S1 + S11 (`GuideChapters` dan `GuideProgress`: langkah inti/opsional dan `minRealm`). Satu perubahan ini menyelesaikan rasa "melompat" paling besar.
2. S3 + S17 (`util/Busy`).
3. S2 + S5 (pelacak).
4. S4 (rumus meditasi; perbarui DESIGN 24.5 dan teks c8.seat).
5. Sisanya: teks (S9, S12, S13) dan angka (S6, S7, S14, S15, S16).
