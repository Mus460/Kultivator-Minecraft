# Audit Integrasi Kultivator: semua alur saling terhubung (sintesis)

Tanggal: 2026-10-22 (putaran 2). Penyusun: audit:sintesis. Audit ini **hanya membaca**: tidak ada berkas proyek yang
diubah, dan Gradle tidak dijalankan.

**Sumber.** Laporan ini menggabungkan enam laporan area: `audit-spine-laporan.md` (17 temuan), `audit-combat-laporan.md`
(13), `audit-sekte-laporan.md` (26), `audit-dunia-laporan.md` (17), `audit-ekonomi-laporan.md` (23) dan
`audit-ux-laporan.md` (21), jadi 117 temuan mentah. Versi pertama berkas ini (22:04) ditulis sebelum laporan-laporan itu
ada. Versi ini menggantikannya.

- Semua temuan **KRITIS dan TINGGI saya cek sendiri** ke kode. Bukti berkas:baris di bawah sudah dicocokkan.
- Temuan yang muncul di beberapa laporan saya gabung.
- Temuan yang sudah tidak berlaku saya buang (lihat akhir bagian 2).
- Dua temuan saya turunkan dari TINGGI ke SEDANG, dengan alasan tertulis.

**Hasil setelah digabung: KRITIS 2, TINGGI 18, SEDANG 34, RENDAH 29 (total 83).** Catatan untuk rencana Sekte Langit
(37) ada terpisah di bagian 4 dan tidak ikut dihitung.

**Status Sekte yang Hidup (36).** Kodenya sudah di-commit (`40d06e1`). Working tree sekarang hanya mengubah berkas suara
(`sounds.json`, `lang-src/voice.*`, `.ogg`). Workflow 36 masih berjalan, jadi semua temuan di `dev.kultivator.sekte.**`
dan di berkas disciple/sect yang disentuh 36 ditandai **[SDB] = SEDANG DIBANGUN**. Temuan itu diperbaiki setelah 36
selesai, atau diteruskan ke tim 36.

**Keamanan blok.** Tidak ada auditor yang menemukan `setBlock`, `LightningBolt`, perubahan cuaca/waktu, atau perubahan
tick-rate di luar `kehancuran/` dan `sect/build/`. Jurus Pemusnah otomatis selalu aman blok (`DestroyerArts.java:41-46`).

---

## 1. Peta perjalanan pemain (satu halaman)

Gerbang diambil dari kode (`atLeast(Realm…)`, `minRealm`, kunci hub). Kolom "Masuk lewat" berisi apa yang membawa pemain
ke fitur itu. Kolom "Memberi makan" berisi ke mana hasilnya mengalir. Tanda ⚠ merujuk ke temuan di bagian 2.

| Ranah (Rank.step) | Fitur yang terbuka | Masuk lewat | Memberi makan |
|---|---|---|---|
| **Fana** (0) | Uji akar, meditasi, HUD Qi, Kitab bab 1, Langkah Pertama bab 1, kota (jalan kaki), peta, peristiwa langit, cuaca roh, Menara lantai 1-10. ⚠ Penjara Iblis bisa dibuka (T7) | layar mulai, `/kultivasi`, hub | bar Fana → Kebangkitan (⚠ 50% Retak, S3) |
| **Pemurnian Qi** (1-4) | Situs/urat, batu roh (bahan bakar ×1,5-3), formasi Qi 1-3, Elite T1, pengembara, penyergapan, Gua Kecil, harta lahir T1, Alam Rahasia, toko kota dan Balai Lelang (⚠ K2, T14), Duel Pedang (tangkis/postur) | Kitab bab 2; seharusnya juga bab 13 (Dunia), ⚠ tetapi bab itu baru tersorot setelah Dewa Abadi (T1) | XP, NB, pil, herba |
| **Pembangunan Fondasi** (5-8) | Alkimia, pedang terbang, Elite T2, Pasar Gelap, Prasasti Aula (tanpa gerbang), Jalan Dunia bab 6 (⚠ pelacak hanya muncul bila Langkah bab 1-5 penuh, T2) | Kitab bab 3, Langkah bab 2 | pil → XP/akar; Aula → calon murid |
| **Inti Emas** (9-12) | Tribulasi pertama, pilih Dao (`DaoPaths:100`), Elite T3, Gua Agung, Perahu Awan, Laut, ajak pengembara, Ujian ranah | Kitab bab 4, Jalan Dunia bab 7 | Dao → teknik 6c, pemahaman |
| **Sekte** (paralel, sejak Prasasti) | Aula lv1-9, templat T1-T9 (`Founding:154`), murid, Formasi Sekte (⚠ tertelan batas ×3,5, T6), Serbuan (aula ≥ 3), Sekte yang Hidup 36: watak, hubungan, tangga, paviliun, tetua, misi cerita, turnamen, diplomasi (⚠ sejak aula lv1, T11), kebun/tambang | Kitab bab 5-6, 8-9 dan 15-16 (⚠ bab 15-16 baru tersorot di akhir permainan), hub SEKTE (⚠ meluap, T9) | renown, PK kas, murid kuat, bonus meditasi saudara sekte |
| **Jiwa Baru Lahir** (13-16) | Ujian Iblis Hati (⚠ tanpa penjaga sibuk, T3), Pewaris, Dunia Bawah (⚠ murid ikut jiwa, S2), naga liar terlihat, Kapal Sekte | Kitab bab 7, bab 14 | pemahaman, binatang roh |
| **Transformasi Dewa → Penyatuan Dao** (17-24) | Gua Kuno, Istana Naga, tundukkan Jiao, Perkokoh Fondasi, Gua Kediaman (⚠ abaikan Formasi Sekte, S6), Penglai, Penjara Iblis sebagai sasaran desain | Kitab bab 7, Jalan Dunia bab 8 | kualitas terobosan, stat |
| **Kenaikan Abadi** (25-60, 9 lapis × 4) | Makam Langit 9/81, Menara ~40-95, Kota Abadi (lahir di Alam Abadi), binatang T5. ⚠ Lapisan IA terasa datar melawan musuh ujian (S7). ⚠ IA T8 tidak membuka teknik baru (RENDAH). ⚠ Pelacak HUD hilang di seluruh ranah ini (T2) | Kitab bab 10 (7 langkah untuk 36 tahap) | Esensi Makam → Kenaikan (⚠ dimakan diskon bintang, T15) |
| **Dewa Abadi** (61-64) + Puncak | Bintang, Bencana Bintang 1-7 (⚠ kartu hub terkunci, T18; ⚠ tanpa sinematik/Hu Fa, S8), Pengakuan Langit, Titah Langit, menara lantai 100, Ketua Generasi Kedua (⚠ butuh pewaris ★9, S1) | Kitab bab 11, perintah `/kultivasi bencana` | ★ → faktor T teknik; ★9 + 9 esensi → Kenaikan |
| **Pasca-batas** (Alam Abadi) | Batu Asal, Purba, Murka Langit, Dunia Batin, Jurus Ciptaan, Lelang Abadi, Pasar Abadi (⚠ tanpa stok, S24), Kota Abadi (⚠ tidak bisa dicari, S19; ⚠ musuh lahir di atap kota, T4), Jalan Abadi bab 11-15 (⚠ urutannya terbalik, S5) | hub KEABADIAN (`ascentLock`) | gelar, bintang tanpa batas |
| **Rencana 37 Sekte Langit** | Aula lv6 / templat 6 → pulau awan A1-A9, Utusan Istana Langit, Bajak Awan, Tantang Langit | kartu hub ke-18 (⚠ T9), rencana bab 17 (⚠ T1) | situs langit ×1,05-1,25 (⚠ tertelan batas, T6) |

**Ringkasan kesinambungan.**
- Tulang punggung kultivasi pribadi (ranah → Dao → bintang → Alam Abadi) mengalir baik.
- Patahan terbesar ada di **lapisan penuntun**. Ada dua panduan dengan nomor bab berbeda. "Bab sekarang" macet di
  bab awal. Bab Dunia dan Sekte 36 ditaruh setelah bab Dewa Abadi. Pelacak hilang selama seluruh Kenaikan Abadi.
- Patahan kedua: **tidak ada satu status "sibuk/aman"**. Iblis Hati, sergapan Saingan, Murka dan makhluk Alam Abadi bisa
  muncul di tengah duel, tribulasi atau kota.
- Patahan ketiga: **sistem kembar yang tidak saling kenal**. Contohnya dua cara "mendirikan sekte", tiga daftar sekte NPC,
  lima jalur misi/utusan, dua lelang dengan aturan berbeda, dan beberapa harga untuk barang yang sama.
- Ada dua lubang yang menghapus barang pemain: sakelar modul 36 dan penjualan cincin berisi.

---

## 2. Temuan terverifikasi (sudah digabung, urut keparahan)

Kolom "asal" menyebut ID di laporan area: sp = spine, cb = combat, sk = sekte, du = dunia, ek = ekonomi, ux = ux.

### KRITIS (2)

**K1. Sakelar modul 36 ikut mematikan pendaftaran item, entitas dan komponen. [SDB]** (asal: sk K1)
- **Bukti:** `SekteContent.init` langsung `return` bila `umum.aktif == false` (`sekte/SekteContent.java:56-58`). Setiap
  modul melewati seluruh `init()` bila sakelarnya mati, termasuk pendaftaran registry:
  - `Kebun.java:90-96`: `KebunItems.init`, `KebunKomponen.register`;
  - `Paviliun.java:66-72`: `Dupa.init`, `PaviliunKomponen.register`;
  - `Turnamen.java:115-122`: `TurnamenEntities.init`, `TribunKomponen.register`;
  - Tangga (`BatuHatiKomponen`) dan Seragam (`PanjiKomponen`) memakai pola yang sama.
  Hanya `Misi.java:59-63` yang sengaja mendaftar lebih dulu, tetapi itu pun tidak jalan bila `umum.aktif` mati.
- **Kenapa memutus:** item yang sudah ada di inventaris, peti atau Lumbung (Inti Roh Gunung, Dupa, Gulungan Laporan) hilang
  saat dunia dimuat. Server dan klien dengan config berbeda tidak bisa tersambung, karena registry tidak cocok. Rencana
  bangun kehilangan komponen `sx_*`. Ini mematahkan janji DESIGN 36.0.8 bahwa setiap modul "bisa dimatikan".
- **Perbaikan (M6):** setiap modul punya `registerContent()` yang **selalu** dipanggil dari `SekteContent.init` sebelum cek
  sakelar. Isinya item, entitas, payload dan `*Komponen.register()`. Logika runtime (listener, tick, perintah) tetap
  mengikuti sakelar. Aturan ini juga wajib untuk 37 (`langit.aktif`).

**K2. Cincin penyimpanan yang masih berisi bisa dijual ke toko kota atau dititipkan ke Balai Lelang, dan seluruh isinya
hilang.** (asal: ek K1)
- **Bukti:**
  - `Toko.buys` menerima semua `id.startsWith("storage_")` (`dunia/kota/Toko.java:112`).
  - `Toko.sell` langsung menjalankan `stack.shrink(count)` (`:196`) tanpa memeriksa `DataComponents.CONTAINER`.
  - `Harga.BASE` memberi cincin nilai positif (`Harga.java:76-80`), jadi `BalaiLelang.consign` (`:463-483`) menyalin dan
    menyita cincin itu.
  - Isi cincin disimpan di komponen `CONTAINER` (`tempa/StorageRingItem.java:304-305`).
- **Kenapa memutus:** satu klik "Jual" dengan cincin di tangan membuang puluhan tumpuk barang tanpa peringatan.
  `RingProtection` melindungi cincin dari api dan void, tetapi toko kota menjadi lubang yang tidak terlindungi.
- **Perbaikan (M3):** `Toko.buys` dan `BalaiLelang.consign` menolak cincin yang berisi atau sudah terikat
  (`StorageRingItem.bondOwner`), dengan pesan baru "Kosongkan cincin dulu". `Toko.view` tidak menampilkan harga jual untuk
  cincin berisi.

### TINGGI (18)

**T1. Kitab Panduan: "bab sekarang" macet, dan urutan bab tidak mengikuti urutan perjalanan.**
(asal: sp S1+S11, sk T4, ux U13, du D11, temuan lama c6.disciple_gc)
- **Bukti:** `GuideProgress.currentChapter` (`guide/GuideProgress.java:125-135`) mengembalikan bab pertama yang punya
  *satu* langkah belum selesai. Langkah-langkah yang menahannya:
  - langkah manual tanpa cek di bab 1 (`c1.hud`, `settings`, `jalan`; `GuideChapters.java:102-106`);
  - `c2.formation3` yang butuh End Rod dari The End (`:115`; teksnya sendiri berkata "target nanti",
    `guide.id_id.json:37`);
  - langkah duel di bab 2-3 (`:118-119`);
  - `c8.seat` = `tier:8`, yang diminta sebelum `c9.tier5`.

  Selain itu:
  - Bab 13 "Dunia yang Luas" (target Pemurnian Qi sampai Inti Emas), bab 14, dan bab 15-16 Sekte 36 (aula lv1-3) berada
    **setelah** bab 11 Dewa Abadi dan bab 12 Tips (`:225-271`).
  - `c15.penerus` butuh pewaris ★9 (S1), sehingga bab 16 tidak pernah tersorot.
  - Bab 14 berisi 13 langkah (aturan 34.9 hanya 4-10). `c14.menara` (lantai 10 = Fondasi) ikut ada di "Ujung Dunia".
- **Kenapa memutus:** pemain Inti Emas tetap melihat "Bab 2". Konten Dunia dan Sekte 36 tidak pernah ditawarkan pada
  waktunya. Toast "Bab selesai" muncul bertahun-tahun terlambat.
- **Perbaikan (M1):**
  1. `Step.optional`. Langkah manual dan optional tidak menahan bab.
  2. `Chapter.minRealm`, ditambah syarat aula untuk bab sekte.
  3. Urutan tampil terpisah dari indeks simpan: 1, 2, 13, 3, 4, 5, 15, 6, 16, 7, 8, 9, 14, 10, 11, 12.
  4. Langkah `c14.menara` dipindah ke bab 13 dengan id baru.
  5. `c15.penerus` dibuat optional.

  Bit `GuideSync` dihitung ulang dan centang manual disimpan per id, jadi simpanan aman.

**T2. Pelacak HUD dan dua panduan paralel: pelacak macet di bab 5, hilang selama Kenaikan Abadi, dan nomor babnya saling
bertentangan.** (asal: sp S2+S5, ux U2, sintesis lama T1/T3)
- **Bukti:**
  - `GuideTracker.world()` butuh `Guide.bits == ALL`, termasuk langkah `sect`, `disciple` dan `expedition`
    (`src/client/java/.../goals/GuideTracker.java:96-98`; `goals/Guide.java:99-100`).
  - `abadi()` butuh Jalan Dunia penuh **dan** `rank.isMax()` (`:91-93`). Karena itu `visible()` (`:86-88`) bernilai false
    dari akhir Jalan Dunia sampai Dewa Abadi Puncak.
  - Segel panel memakai `chapter + 1` (`:190`), sedangkan judul memakai `chapter + offset + 1` (`:150`, `:158`).
  - Langkah Pertama punya 15 bab, Kitab punya 16 bab. Keduanya tampil di satu widget (`:168-170`, `:224`).
- **Kenapa memutus:** pertapa tanpa sekte melihat "Dirikan Sekte" dari Fondasi sampai Dewa Abadi. Masa terpanjang
  permainan tidak punya penunjuk arah.
- **Perbaikan (M1):**
  - `world()` = Langkah bab 1-4 selesai dan ranah ≥ Fondasi. Langkah sekte dilewati selama belum ada aula.
  - `abadi()` = TI Puncak saja.
  - Bila tidak ada rangkaian Langkah yang aktif, tampilkan langkah Kitab berikutnya alih-alih menghilang.
  - Perbaiki offset di `:190`.
  - Segel diberi label "Langkah" dan "Kitab".

**T3. Tidak ada satu pintu "sedang sibuk": peristiwa besar saling tumpuk.** (asal: sp S3+S17, cb 2, du D3, sintesis lama T4
dan S11)
- **Bukti:** setiap sistem memeriksa daftar yang berbeda.
  - `HeartDemons.canFace` (`trial/HeartDemons.java:287-291`) hanya memeriksa `Breakthrough.isBusy`, `breakingThrough` dan
    `Sparring.inDuel`. Duel Pedang/`DuelRules`, `Alam.inRun`, Purba, Bencana dan Tribulasi tidak diperiksa.
  - Pindah dimensi selama uji tekanan dihitung `FLED` (`:464-466`) dan berujung Qi Deviation 0,6 (`:666-668`).
  - `Ascension.canBegin` (`ascension/Ascension.java:280-283`) punya celah yang sama.
  - `Saingan.tickAmbushes` (`dunia/kota/Saingan.java:514-515`) hanya memeriksa kota, langit, jiwa dan mode.
  - `Purba.busy` (`:259-263`), `Batin.busy` (`:120-123`) dan `TreasureBirths.busy` (`:198-200`) juga punya daftar sendiri.
  - Pembanding yang benar: `Ambushes.refusal` (`musuh/Ambushes.java:193-230`).
  - Ada lima sistem sergapan/serbuan dengan batas harian masing-masing.
- **Kenapa memutus:** Iblis Hati bisa muncul di tengah Murka Langit, dan sergapan bisa datang saat tribulasi. Ini melanggar
  aturan 7b "jangan saat bahaya nyata". Pemain juga dihukum karena keluar portal.
- **Perbaikan (M2):**
  - Buat `util/Sibuk.java` dengan `register(id, Predicate)`, `why(player)` dan `addProvider` untuk 36/37.
  - Semua penjadwal peristiwa acak **menunda** saat sibuk, bukan membuang peristiwanya.
  - Uji tekanan yang pindah dimensi menjadi `CANCELLED`.
  - Tambahkan hitungan tekanan harian bersama, dengan aturan "maksimal satu peristiwa musuh sekaligus" (batas angka per
    hari perlu persetujuan pengguna).

**T4. "Tempat aman" tidak dihormati: penyergapan di Kota Kultivator, dan makhluk elit di Kota Abadi.** (asal: du D4+D5)
- **Bukti:**
  - `Ambushes.refusal` tidak mengenal kota fase 34. `Towns.inTown` (`dunia/kota/Towns.java:93`) hanya dipakai Saingan.
  - `level.isVillage` (`Ambushes.java:227`) hanya mencakup POI ranjang.
  - `abadi/Natives.java:211-257` memunculkan 1-2 elit di `Heightmap MOTION_BLOCKING_NO_LEAVES` tanpa cek kota. Kota Abadi
    melayang di y 150-170 (`worldgen/structure/kota/kota_abadi.json:15`), jadi heightmap di sana adalah atap dan jalan kota.
  - Panduan `c14.saingan` berjanji "Kota adalah tempat aman" (`dunia_panduan.id_id.json:27`).
- **Kenapa memutus:** pemain yang percaya Panduan diserang di alun-alun saat lelang. Kuliah Dao di Kota Abadi terganggu
  elit yang tidak dilawan siapa pun.
- **Perbaikan (M2):** layanan `DuniaApi.safeGround(level, pos)` (diisi modul kota dengan `Towns.inTown` dan Kota Abadi)
  dipakai oleh `Ambushes.refusal`, `Saingan`, `Natives.groundSpot`, dan nanti 37.

**T5. Musuh dunia (rival Kota, Kepiting Batu, Naga Liar, makhluk Reruntuhan) tidak tergolong "musuh ujian", jadi teknik
memakai P penuh.** (asal: cb 1)
- **Bukti:** `TechniquePower.isTrialMob` (`technique/spec/TechniquePower.java:677-681`) hanya mengenal tag `dao_protector`,
  `trial_guard`, `elite`, `rogue` dan `abadi_foe`. `finalDamage` (`:839-845`) memakai legacy × 1,5 hanya untuk mereka.
  Entitas berikut hanya diberi `NoGrief.TAG` dan tag sendiri, plus `TAG_BOSS` untuk bosnya:
  - `KultivatorNpc.java:134-135, 206`;
  - `KepitingBatuEntity.java:51-52`;
  - `NagaLiarEntity.java:213`;
  - `RCreature.java:97-98, 420`.
- **Kenapa memutus:** rival Kota di ranah Dewa Abadi mati dalam satu pukulan, sedangkan kultivator nakal yang setara butuh
  sekitar 10 pukulan. Konten dunia tingkat tinggi jadi kosong tantangan.
- **Perbaikan (M4):** buat API `TechniquePower.markRealmFoe(mob)` (tag `dunia_foe` masuk ke `isTrialMob`) dan panggil di
  `setup` setiap entitas dunia yang hostile dan berskala ranah. Ini hanya mengubah klasifikasi, bukan angka.

**T6. Batas meditasi ×3,5 menelan Formasi Sekte (dan nanti situs Sekte Langit).** (asal: sp S4, sintesis lama S1)
- **Bukti:** `gainPerSecond = base × min(3.5, site × formation × fuel)` (`cultivation/Meditation.java:83`, `:378`).
  Formasi Sekte hanya menaikkan `formationTerm` ke 2,6/2,8 (`formasi/stack/Stacking.java:45-46`).
  - Formasi pribadi Bintang Surgawi (2,2) dengan bahan bakar terendah (×1,5) sudah 3,3.
  - Dengan Formasi Sekte jadi 3,9, lalu terpotong ke 3,5. Tambahannya hanya 6%.
  - Dengan situs ditambah bahan bakar, tambahannya 0%.
- **Kenapa memutus:** fitur bab 8 dan rencana bab 17 tidak memberi apa-apa kepada pemain yang serius. Hadiahnya tak
  berharga tepat di titik yang dijanjikan.
- **Perbaikan (M4):**
  - Tanpa mengubah angka: pesan "batas penyerapan tercapai" di meditasi dan di `InfoPart` formasi, sehingga hal ini
    terlihat.
  - Dengan persetujuan pengguna: faktor sekte G dikeluarkan dari `min`, dibatasi `GATHER_SECT_CAP 1,5`.

**T7. Penjara Iblis Kuno (target Penyatuan Dao) tidak punya gerbang ranah dan bisa lahir dekat spawn.** (asal: du D1+D2)
- **Bukti:**
  - `ShrineRun.pull` (`dunia/reruntuhan/penjara/ShrineRun.java:105-128`) tidak memeriksa ranah. Pemain Fana bisa
    mencabut kelima jimat.
  - Raja iblis selalu 2000 HP (`CoreRun.java:268`).
  - Struktur `penjara_iblis`, `gua_kuno`, `gua_agung`, `gua_kecil` dan `menara_seratus` memakai `random_spread` tanpa
    `min_chunks`, sehingga aturan W4 (6 chunk dari spawn) dilanggar.
  - Gua, kapal, laut dan naga semuanya punya gerbang ranah. Hanya penjara yang tidak.
- **Perbaikan (M2):**
  - Tolak cabut jimat di bawah `DAO_UNION`, mengikuti pola `GateRun.touch`.
  - Raja hanya bangkit bila ada pemain ≥ Transformasi Dewa.
  - Kartu peringatan menyebut ranah yang disarankan.
  - `tools/gen_dunia_reruntuhan.py` memakai `kultivator:jauh_dari_pusat` (6 chunk; penjara dan Gua Kuno 12). Ini hanya
    berlaku di chunk baru, jadi aman untuk simpanan.

**T8. "Dirikan sekte" punya dua arti, dan langkah Jalan dicentang oleh hal yang salah.** (asal: sk T6, ux U3, sintesis lama
S8)
- **Bukti:**
  - Langkah `sect` dicentang oleh `sect_founded` (`goals/Guide.java:99`). Advancement itu hanya diberikan oleh
    `/sect create` (`sect/SectCommands.java:194`), yang juga memutar suara Langit (`:195`). Perintah itu hanya membuat
    kelompok pemain tanpa murid.
  - `Halls.found` (Prasasti Aula yang sungguhan) hanya memberi `sect_gate_open` (`sect/hall/Halls.java:544`), tanpa
    suara Langit dan tanpa centang.
  - Teks pemandu menyuruh `/sect create` (`goals.id_id.json:43`, `screen.id_id.json:170`, "…lalu undang murid").
  - Kartu hub "Dirikan" justru mengarah ke Prasasti.
- **Perbaikan:**
  - M1: langkah `sect` memakai `sect_gate_open` atau `first_recruit_hall`.
  - M6: `Halls.found` memutar `VoiceHooks.sectFounded`.
  - M6: teks `sects.*` memakai istilah **"Saudara Sekte"** untuk kelompok pemain.
  - M1 dan M5: teks pemandu diubah agar menunjuk ke Prasasti.

**T9. Hub SEKTE meluap: 16-17 kartu tanpa gulir, dan kartu 36 tanpa gembok aula. [SDB]** (asal: ux U1+U11, sk S1+R2,
sintesis lama T7)
- **Bukti:**
  - Ada 8 kartu bawaan, kartu "Dirikan", dan 8 kartu `sekte_*` (`HubCardsPage.java:183-196`; 8× `HubCards.register(…,
    null, …)`).
  - `layoutCards` (`:234-256`) memaksa tinggi kartu ≥ 20 px tanpa gulir. Di 320×240 grid butuh 234 px, padahal ruangnya
    169 px.
  - Kontrak `HubCards.java:14-16` sendiri menjanjikan grid itu muat.
  - Rencana 37 menambah kartu ke-18.
- **Perbaikan (M5):**
  - SEKTE diberi dua sub-koin: "Aula" (kartu bawaan) dan "Perguruan" (kartu dengan awalan `sekte_`).
  - Kartu `sekte_*` otomatis mendapat `hallLock` di `HubCardsPage`, jadi berkas klien 36 tidak perlu disentuh.
  - Ikon kartu dibuat berbeda-beda.

**T10. Tantangan sekte NPC memunculkan dua prompt, dan pemain dihukum walau sudah menerima. [SDB]** (asal: sk T1+S12)
- **Bukti:**
  - Peristiwa `TANTANG` dan `KETUA` memanggil `Utusan.offer` **dan** `Diplomasi.fire(…, TANTANGAN)`
    (`sekte/diplomasi/Simulasi.java:406-413`).
  - Listener T mengubah setiap `TANTANGAN` menjadi tombol chat turnamen (`sekte/turnamen/Turnamen.java:126-130`).
  - Bila pemain menerima lewat chat, tawaran Utusan tetap kedaluwarsa dan dihitung sebagai penolakan, −5 standing
    (`Utusan.java:254-275`).
  - `Musim.pending` tidak dibersihkan saat pemain menerima lewat D, sehingga turnamen tamu kedua bisa terjadi.
- **Perbaikan (M6):** satu pintu saja. Utusan D memegang tawaran. `Simulasi` tidak lagi menembakkan `TANTANGAN` untuk
  `TANTANG`/`KETUA`. `AntarSekte.host` membersihkan `pending`.

**T11. Diplomasi dan perang sekte NPC menekan aula sejak level 1. [SDB]** (asal: sk T2)
- **Bukti:**
  - `Simulasi.dailyNext` hanya mensyaratkan `hall.active()` (`:210-236`).
  - `pickSect` memilih dari **semua** sekte, termasuk yang belum ditemui (`:307-327`).
  - `strong = sect.strength() > kekuatanAula` hampir selalu benar untuk aula baru, sehingga `TUNTUT` terbuka (`:349`,
    `:357`).
  - Kekalahan pertempuran data di bawah aula lv3 memotong PK (`Perang.java:236-270`, dibatasi `pool/20` ≤ 150).
  - Serbuan lama sengaja baru mulai di aula lv3 (`SectRaids.MIN_HALL_LEVEL`, `:144`).
- **Kenapa tetap TINGGI:** kerugiannya dibatasi, tetapi pemain yang baru memasang prasasti langsung dituntut oleh sekte
  tingkat 7-9 yang tidak dikenalnya, tanpa penjelasan. Polanya juga berlawanan dengan serbuan lama.
- **Perbaikan (M6):**
  - Peristiwa negatif baru boleh terjadi bila `Halls.level ≥ 3` (field `DiplomasiConfig.mulaiAulaLevel`, bawaan 3).
  - `pickSect` hanya memilih sekte yang sudah ditemui atau yang tingkatnya ≤ aula + 2.

**T12. Tiga daftar "sekte NPC" yang tidak selaras: Saingan, domain Peta, dan Diplomasi (ditambah Turnamen Elit statis).
[SDB]** (asal: du D6, sk S3)
- **Bukti:**
  - Saingan: 6 kelompok di wilayah 900-5100 blok (`SainganData.java:99-100`).
  - Peta: `PetaDomains` (`:42-85`) menggambar wilayah saingan, atau 12 nama sendiri.
  - Diplomasi: 8 sekte di cincin 1500-2900 blok (`GerbangPlan.java:11-12`) dengan `rivalLink` per **indeks**
    (`Simulasi.java:60-63`), sehingga sekte 7-8 tidak punya pasangan.
  - Gerbang sekte NPC tidak diterbitkan ke `DuniaSites`/peta.
  - Turnamen Elit memakai 8 nama statis `Rivals` (`Tournament.java:507-510`).
  - "Lembah Seribu Racun" muncul dua kali.
- **Perbaikan:**
  - M2, sisi dunia: fungsi statis `SainganData.regionOf(seed, i)`, jenis marker dan penunjuk `sekte/gerbang`, dan
    `PetaDomains` mengambil nama dari `DiplomasiService`.
  - M6, sisi sekte: `GerbangPlan` memakai `regionOf`, `rivalLink` disimpan per id, gerbang menerbitkan situs, dan Turnamen
    Elit memakai sekte D yang sudah ditemui.

**T13. Lima jalur misi/utusan dengan aturan berbeda; tiga di antaranya bernama "Utusan". [SDB]** (asal: sk T5, ek S9 sebagian)
- **Bukti:** kelima jalurnya adalah:
  - Ekspedisi lama;
  - Misi Cerita X, yang berjalan "beside the existing expedition board" (`sekte/misi/Misi.java:29-31`) dan memakai tabel
    jarahan yang sama;
  - Utus Perekrut (`sect/event/Envoys.java:6-16`);
  - aksi D "Kirim Utusan" (`diplomasi/Aksi.java:60,134`);
  - misi templat `UTUSAN` (`misi/Kepulangan.java:282-298`).
- **Perbaikan (M6):**
  - Satu tablet Misi dengan tab "Harian" dan "Cerita".
  - Aksi D "Kirim Utusan" membuat misi X `UTUSAN`, dengan satu rumus standing.
  - Utus Perekrut diganti nama menjadi "Utus Pencari Bakat".

**T14. Lelang kota tidak aman: titipan hanya disimpan di memori, dan aturannya beda dengan Lelang Abadi.** (asal: ek T1+S3)
- **Bukti:**
  - `BalaiLelang.SESSIONS` adalah `HashMap` biasa (`:138`).
  - `Kota.java:72-75` memanggil `BalaiLelang.clear()` saat `SERVER_STOPPED` tanpa `finish`/`forceClose`. `forceClose`
    hanya dipanggil oleh perintah op (`KotaCommands.java:96`).
  - Titipan kota mengambil satu tumpuk penuh, sedangkan Lelang Abadi mengambil satu buah dari whitelist dan menyimpannya
    (`abadi/Auction.java:497-571`).
- **Kenapa memutus:** keluar dunia saat senja setelah menitip berarti barang hilang. Pemain belajar aturan di satu lelang,
  lalu kehilangan barang di lelang yang lain.
- **Perbaikan (M3):**
  - Titipan disimpan di `KotaData.player(uuid)`, mengikuti pola `owed` yang sudah ada.
  - `forceClose` dipanggil sebelum `clear`.
  - Satu helper `Titipan` dengan aturan yang sama: satu buah per titipan, whitelist, bayaran 0,6-1,0 ×.

**T15. Pemadatan bintang otomatis memakan Esensi Energi Makam yang dibutuhkan untuk Kenaikan.** (asal: ek T2)
- **Bukti:**
  - Ada diskon 2% per esensi (maksimal 10), dan `addCondenseListener` **mengambil** esensi itu
    (`goals/Goals.java:82-83`, `:143-158`).
  - Kenaikan butuh tepat 9 esensi (`abadi/Ascend.java:67`, `:215`) dan ★9.
  - `OriginStones.java:66-67` memakan Batu Asal dengan cara yang sama.
- **Kenapa memutus:** bintang ke-9 memakan esensi ke-9, jadi gerbang Kenaikan tertutup kembali. Pesannya baru muncul setelah
  esensi hilang.
- **Perbaikan (M3):**
  - Sebelum `ascended`, sisakan `Ascend.OFFER_ESSENCE`.
  - Atau buat tombol diskon (default mati) dan pratinjau "akan memakai N esensi".

**T16. Herba berumur dihargai linear, sehingga hadiah herba milenium jadi murah.** (asal: ek T3)
- **Bukti:**
  - `missionValue = 1 + age/100` (`harta/SpiritHerbs.java:187-190`), dan `Harga.value` herba = ×2 (`Harga.java:124-126`).
  - Paviliun Pil band 6+ menjual herba sampai 1000 tahun (`herbMax`, `:213-215`) seharga ±22 NB.
  - Di tempat lain herba yang sama jauh lebih mahal: 8 Batu Asal di Pasar Abadi, dasar 25 Batu Asal di Lelang Abadi.
  - Herba milenium juga hadiah inti harta langit T3.
- **Perbaikan (M3):** kurva harga kota yang non-linear, atau `herbMax` toko maksimal 300 tahun. Angka ini perlu persetujuan
  pengguna, kecuali pilihan memotong `herbMax`.

**T17. Harga sekte (PK) tidak sejalan dengan kota: Paviliun Yue Lu 8-30× lebih mahal, Pil Sumsum dikunci di aula lv6, dan
Kantor Misi menilai pil rata.** (asal: ek T4+S2, sintesis lama S4)
- **Bukti:**
  - `Contribution.Ware` (`sect/hall/Contribution.java:59-72`): Penyembuh 40 PK, Pil Sumsum 900 PK di aula lv6.
  - Toko kota menjual Pil Sumsum 80 NB sejak band 1 (`Harga.java:55`, `:166`), dan resep vanilla tanpa gerbang juga ada
    (`recipe/marrow_cleansing_pill.json`).
  - Persembahan pil dinilai rata 5/10/20/40 PK per mutu (`:153-160`).
- **Kenapa memutus:** "hadiah" naik level aula ternyata barang yang sudah dijual kota sejak Pemurnian Qi.
- **Perbaikan (M3):**
  - Satu kurs PK/NB.
  - Nilai persembahan pil = `Harga.base × mutu`.
  - MARROW di Yue Lu diganti barang yang tidak dijual kota (misalnya Pil Sumsum mutu Sempurna).
  - Diskon Pakta 10% (DESIGN 36.2.9) dibaca dari `DiplomasiService`.
  - Semua angka baru perlu persetujuan pengguna.

**T18. Kartu hub "Bencana Bintang" terkunci "naik ke Alam Abadi dulu", padahal bencana justru syarat untuk naik.**
(asal: sintesis lama T6)
- **Bukti:**
  - Kartu `bencana` memakai `ascentLock` (`src/client/java/.../screen/HubCardsPage.java:142-147`, `:207`).
  - `StarCalamity.refusal` hanya menuntut `rank.isMax()` (`purba/StarCalamity.java:255-262`).
  - Bencana 1 menahan bintang ke-6/7/8, dan `Ascend` butuh ★9.
- **Perbaikan (M5):** kunci kartu `bencana` = TI Puncak (`rank.isMax()`), bukan `ascentLock`.

### SEDANG (34)

| # | Temuan | Bukti utama | Asal | Modul |
|---|---|---|---|---|
| S1 | Penyerahan Takhta butuh pewaris ★9 (Dewa Abadi Puncak), sedangkan syarat ranah tertulis Nascent Soul. **Diturunkan dari TINGGI**: layar sudah menampilkan baris ★0/9, dan langkah panduannya ditangani di T1. [SDB] | `sekte/tetua/Generasi.java:60-61,180-184`; `disciple/path/Stars.java:35-41` | sk T3 | M6 |
| S2 | Murid pengikut ikut ke Dunia Bawah (jiwa, sementara tubuh ditinggal) dan ke slot Menara. Tidak ada kait veto. **Diturunkan**: dampaknya terbatas pada rasa cerita dan kemudahan ujian | `disciple/Disciples.java:993-1012` | sintesis lama T5 | M2 |
| S3 | Kualitas Kebangkitan: 50% Retak, Surgawi mustahil, padahal ikut dirata-rata ke stat | `cultivation/Breakthrough.java:609-626`; `data/CultivationData.java:374-388` | sp S6 | M4 |
| S4 | XP tempur tetap HP × 0,5, dan konten Dunia hampir tidak memberi XP kultivasi | `cultivation/XpSources.java:119-121`; `dunia/naga/DarahJiaoItem.java:49` | sp S7 | M4 (angka perlu persetujuan) |
| S5 | Urutan Jalan Abadi terbalik (`a_realm` sebelum `a_calamity`) | `goals/Guide.java:116-120` | sp S8 | M1 |
| S6 | Gua Kediaman hanya mengakui Formasi Pelindung lama, bukan Formasi Sekte | `cultivation/CaveAbode.java:123,154`; `formation/ProtectiveFormations.java:133-145` | sp S10 | M4 |
| S7 | `legacyD` mengabaikan lapis Kenaikan Abadi, jadi 9 lapis terasa datar melawan musuh ujian | `TechniquePower.java:414-417` | cb 3 | M4 (angka perlu persetujuan) |
| S8 | Bencana Bintang pemain: tanpa beat sinematik, Hu Fa, atau cincin petir (bencana murid punya semuanya) | `purba/StarCalamity.java:292-330,398-421`; `epic/EpicHooks.java:213`; `Tribulation.java:514-545` | cb 4 | M4 |
| S9 | Dua teks bantuan Kehancuran bertentangan dan daftar perlindungannya beda; ada "Balai Sekte" dan "Aula Sekte" | `kehancuran.id_id.json:3` vs `kehancuran_diri.id_id.json:36`; `ProtectedZones.java:35-51` | cb 5 | M4 |
| S10 | Tiga penjaga FPS; plafon quad duel 600/2400/6000 di luar `EpicBudget.QUADS` | `DuelFxSettings.java:104-113,136`; `EpicBudget.java:21` | cb 6 | M4 |
| S11 | Ensiklopedia tanpa topik Duel Pedang dan 10 modul 36; 15 layar baru tanpa "?"; help.turnamen 60 dtk vs 90 dtk; help.misi/calon/serbuan basi [sebagian SDB] | `HelpPage.java:39-54`; `sectevent.id_id.json:375,377`; `TurnamenConfig.java:33` | ux U4/U5, sk S2, sk R3 | M5 |
| S12 | Luapan Lumbung dibuang bila pemilik offline [SDB] | `sect/event/ForbiddenGround.java:160-163`; `PrimordialArk.java:131-136` | sk S4 | M6 |
| S13 | "Membangkang" hanya dihormati sistem baru, tidak oleh Ekspedisi, Envoys, Turnamen dan lainnya [SDB] | `sect/event/Expeditions.java`, `Envoys.java`, `Tournament.java:486-492` | sk S5 | M6 |
| S14 | `Perang.serangan` mengumumkan serangan walau `callRaid` (boolean) menolak [SDB] | `sekte/diplomasi/Perang.java:236-255`; `SectRaids.java:219` | sk S6 | M6 |
| S15 | Aktivitas 13-20 dipulihkan dari simpanan tanpa cek modul, sehingga murid tertahan merajuk/menambang (risiko simpanan) [SDB] | `disciple/DiscipleEntity.java:1499-1505`; `Kepribadian.java:195-198` | sk S7 | M6 |
| S16 | Templat 1 tidak mendapat satu pun komponen 36, tanpa penjelasan [SDB] | `KebunKomponen.java:91-93`; `PaviliunKomponen.java:97-100` | sk S8 | M6 |
| S17 | Suara 36.6 baru tidak dipanggil kode (masih memakai kunci lama) [SDB] | `docs/progres/sekte-voices.md`; `VoiceDisciples.line` di `sekte/**` | sk S9 | M6 |
| S18 | Gulungan Penunjuk dan Kabar Angin mencatat gua tingkat apa pun sebagai "Gua Kecil" T1, dan situs palsu tersimpan permanen (risiko simpanan) | `dunia/peta/PetaItems.java:43-45`; `kota/KedaiTeh.java:265-273`; `DuniaSites.java:312-313` | du D7 | M2 |
| S19 | Kota Abadi tidak bisa dicari (tidak ada jenis penunjuk/kabar) | `PetaItems.java:43-45`; `KedaiTeh.java:58`; `dunia_panduan.id_id.json:45` | du D8 | M2 |
| S20 | Kekuatan saingan di perlombaan harta mengikuti pita server, bukan tingkat harta | `kota/Saingan.java:122-129,358-359` | du D9 | M2 |
| S21 | Saingan, Kapal, Naga dan Laut tidak terlihat di hub; daftar dendam hanya lewat perintah op | `dunia_kota.id_id.json:561`; `HubCardsPage.java:197-203` | du D10, sintesis lama S9 | M5 (kartu) + M2 (payload) |
| S22 | Hadiah yatim: Sobekan Segel, Pahala, Lencana Pengembara, Kitab Formasi Kuno, Segel Titah kedua | `ShrineRun.java:117`; `CoreRun.java:182-186`; `KotaItems.java:45`; `gua/HallRun.java:198`; `HeavenDecreeSealItem.java:23-26` | du D12, ek R1 | M2 (dunia) + M3 (Segel Titah) |
| S23 | Batu Agung 600 NB vs resep 9:1 (= 720), arbitrase +20% lewat `NilaiBatu.give` | `dunia/kota/NilaiBatu.java:21,103-117`; `SpiritStones.java:71-72` | ek S1 | M3 |
| S24 | Pasar Abadi tanpa stok; kurs Batu Asal:NB 22-642; Pil Abadi dijual ke toko kota ±3400 NB; Mutiara Penunjuk 12/900/1350 NB | `KotaAbadi.java:270-319`; `Harga.java:64,101,206`; `SeaPeople.java:85` | ek S4+S6 | M3 |
| S25 | Imbalan Titah Utusan: jenis "batu" untung gratis, jenis "mutiara" rugi | `KotaAbadi.java:67-68,356` | ek S5 | M3 |
| S26 | Pil murid tidak sejalan dengan pemain: Sumsum selalu sukses; 4 pil fase 7 ditolak "for now", dan hadiah pil 36 gagal diam-diam | `DiscipleCultivation.java:730-736`; `MarrowCleansing.java:54`; `Harta.java:214-222` | ek S7 | M3 |
| S27 | Inflasi NB di akhir permainan (Papan Misi ×3 per band); hadiah harian selalu 1 Batu Rendah | `PapanMisi.java:123-140`; `DailyChores.java:274` | ek S8 | M3 |
| S28 | Kas PK 36: sink besar, faucet baru melewati batas 300/hari, tidak ada pembaca kas (`poolHas` mengambil lalu mengembalikan) [SDB] | `sekte/kebun/TambangRoh.java:82-97`; `Contribution.java:56` | ek S10 | M6 |
| S29 | Tombol `peta`, `parry` dan `duel_camera` tidak diikat, sehingga muncul "tidak diikat" di tengah kalimat | `KeyHints.java:36`; `PetaClient.java:25`; `DuelKeys.java:21,24` | ux U6 | M5 |
| S30 | Koin "HUD Qi" ikut menyembunyikan peringatan tribulasi; 7 HUD baru tidak terpengaruh | `SettingsRows.java:190`; `TribulationHud.java:46` | ux U7 | M5 |
| S31 | Nama ranah dan konsep tidak konsisten: "Abadi Sejati"/"Dewa Abadi Sejati", "Pembentukan Fondasi", "Fondasi" enam arti, Tribulasi vs Kesengsaraan | `aura.id_id.json:8`; `aura_high:11`; `tempa:168`; `hub:68`; `dunia_kota:523,585,609`; `dunia_naga:163`; `arts_lightning:57,67` | ux U8, cb 9/10, sp S9, du D15 | M4 (aura, arts, tempa) + M3 (dunia_kota) + M5 (hub, dunia_naga) |
| S32 | Satu kata untuk banyak sistem: Titah ×4, Papan Misi ×3, Kantor Misi (sebenarnya kantor persembahan), Pusaka Natal ×2, Saingan ×2, Tetua Agung ×2, dua kebun, Bajak Langit vs Bajak Awan [sebagian SDB] | lang `kota.tab.titah`, `sectevent.ui.board`, `hall.ui.kantor`, `dpower.natal.*`; `JabatanTetua.java:74-80`; `Duties.java:49-56` | ek S9, sk S10/S11, du D15, ux U12 | M5 (glosarium) + M6 (hall, sekte_*, dpower) + M3 (dunia_kota) |
| S33 | Penggantian nama baru setengah jalan: "Cincin Segel" masih ada di `/teknik slot`; "Mode Kehancuran" 66× vs "Kehancuran Diri" 6×; "Teknik Penghancur" vs "Jurus Pemusnah" | `teknik.id_id.json:159`; `hub.id_id.json:44` | ux U9 | M4 (teknik) + M5 (hub) |
| S34 | PANDUAN/README basi: 14 bab/124 langkah (kode 16/153); bantuan tablet Jalan "20 langkah dalam 5 bab"; daftar config tanpa duel/sekte | `docs/PANDUAN.md:8448-8470`; `goals.id_id.json:249`; `README.md:192-206` | ux U10/U20, sp S12 | M1 |

### RENDAH (29)

| # | Temuan | Bukti | Modul |
|---|---|---|---|
| R1 | Teks biaya Perkokoh Fondasi menyebut "ranahmu", padahal kode memakai ranah dengan kualitas terendah | `Reforge.java:52-96`; `guide.id_id.json:135` | M1 |
| R2 | Bintang pemain +2 HP vs murid +4 HP +serangan, padahal kurvanya sama; perbedaannya tidak dijelaskan | `Stats.java:31,104`; `disciple/path/Stars.java:45-50` | M1 (teks) |
| R3 | Sambaran terakhir Bencana ×2 tanpa pemulihan, sedangkan tribulasi ×1,5 | `StarCalamity.java:57,405` | M4 (angka perlu persetujuan) |
| R4 | Hadiah Langkah bab 9 (Tomb Sigil) datang setelah Makam selesai | `goals/Guide.java:109,565-568` | M1 |
| R5 | Percikan duel ditulis dua kali (Ringan 0,4 vs 0,5), dengan dua UI | `DuelSettings.java:78-90`; `DuelFxSettings.java:38-48` | M4 |
| R6 | "169 teknik" vs penghitung x/168 | `guide.id_id.json:71`; `TechniqueSpec.java:542` | M1 |
| R7 | IA Tahap 8 tidak membuka teknik baru (Tahap 6 hanya satu) | `technique/arts/*Arts.java` | M4 (teks Kitab saja) |
| R8 | Duel Ketua Sekte memakai `DuelRules.begin` polos (tanpa intro, aturan, atau cek sibuk) [SDB] | `KetuaSekte.java:151-170` | M6 |
| R9 | `DuelConfig.timeLimitSeconds` tidak pernah dibaca | `duel/DuelConfig.java:52,169`; `DuelRuleSet.java:84,97` | M4 |
| R10 | Diskon Pakta 10% ada di DESIGN tetapi tidak ada di kode [SDB] | `DESIGN.md:23026`; `Contribution.java` | M3 |
| R11 | Tiga akar perintah: `/sekte`, `/kultivasi aula`, `/kultivasi perguruan` | `SectCommands.java:97`; `BuildCommands.java:178`; `SekteCommands.java:29-37` | M6 |
| R12 | Misi "Lelang Kota" membayar PK secara abstrak, terlepas dari Balai Lelang [SDB] | `TimMisi.java:54,286` | M6 |
| R13 | Razia Pasar Gelap: penjaga maksimal Inti Emas, tetapi suap naik per ranah pemain | `PasarGelap.java:218,232` | M3 |
| R14 | Harga Pasar Gelap jauh di atas biaya resep (Dupa Arwah, Mutiara, Selendang) | `Harga.java:99-101` | M3 |
| R15 | Makam Surgawi dan Menara Seratus sama-sama "panjat lantai", tanpa rujukan silang | `RealmSpecs.java:33`; `LobbyRun.java:151-170` | M2 (teks) |
| R16 | "batu roh Tertinggi" vs nama item "Batu Roh Agung" | `abadi.id_id.json:43,49,209` | M3 |
| R17 | Tabel NB diduplikasi 3× (NilaiBatu, DimensiData, RRewards); konstanta bahan bakar dobel | `DimensiData.java:27`; `RRewards.java:38`; `SpiritStones.java:67-68` vs `Meditation.java:103-104` | M3 |
| R18 | Pasar Gelap membeli tanpa batas harian | `PasarGelap.java:192-205` | M3 |
| R19 | Hadiah ke murid: Batu Agung hanya 6 kepuasan, dan barangnya lenyap [SDB] | `Kepribadian.java:713-763` | M6 |
| R20 | Bahan bakar Agung 30× lebih mahal per detik untuk faktor 2× | `Meditation.java:103-104` | M3 (teks tooltip) |
| R21 | Nama mirip: Dupa vs Dupa Arwah; tiga "esensi" | `paviliun/Dupa.java:23`; `DimensiItems.java:36` | M3 |
| R22 | Satu hub punya empat nama (Hub, Gulungan, menu utama, Layar kultivasi) | `duel_inti:34`; `guide:13`; `core:125` | M5 |
| R23 | Kartu "Catatan Tangga" menutup hub dan menulis ke chat [SDB] | `TanggaClient.java:53-59` | M6 |
| R24 | Bantuan `/kultivasi perguruan` hanya menyebut 6 dari 10 modul | `sekte.id_id.json:9`; `screen.id_id.json:177` | M6 (sekte) + M5 (screen) |
| R25 | Tombol "?" di Pengaturan membuka topik yang salah | `SettingsPage.java:28` | M5 |
| R26 | `voice.langit.treasure_born` dan `blood_eclipse` tidak dipakai | `sounds.json:3508,3517` | M5 |
| R27 | "Prasasti Sekte" vs "Prasasti Aula Sekte"; "kemasyhuran" vs "Ketenaran" | `hub.id_id.json:28,30` | M5 |
| R28 | Dua mesin lelang dengan kode salinan (utang refaktor; aturannya ditangani di T14) | `abadi/Auction.java`; `dunia/kota/BalaiLelang.java` | M3 |
| R29 | Pemadatan Bintang dan ukuran ★ pemain tidak dijelaskan di Kitab bab 11 (pelengkap R2) | `guide.c11.stars.body` | M1 |

**Dibuang setelah diperiksa:**
- sk R6 "`c16.perang` tidak ada": langkahnya sudah ada di `GuideChapters.java:271`.
- Catatan lama "Hu Fa bisa pergi": auditor sekte mencabutnya sendiri, karena `Kesetiaan.bound` melindungi pewaris dan Ketua
  Generasi Kedua.
- "Kunci lang tidak berpasangan": 14 933 kunci, 0 yang ganda.
- "Config yang tidak berbuat apa-apa": `KultivatorConfig` 120/120 dibaca, modul 36 semua dibaca. Pengecualiannya adalah
  `DuelConfig.timeLimitSeconds` (R9).
- "Turnamen 36 menduplikasi Tournament": `AntarSekte` memakai `sect.event.Tournament`, jadi keduanya tersambung.

---

## 3. Rencana integrasi: tepat 6 modul perbaikan

Keenam modul dikerjakan **setelah** workflow Sekte yang Hidup selesai (`docs/progres/sekte-finish.md` tercentang dan
suara 36.6 di-commit). Setiap berkas hanya punya satu pemilik, jadi keenam modul bisa berjalan paralel.

**Aturan umum untuk semua modul:**
- Semua baris Sumber tetap.
- Angka gameplay tidak berubah, kecuali yang ditandai "perlu persetujuan pengguna". Tanpa persetujuan, pakai alternatif
  yang tidak mengubah angka.
- Blok hanya boleh berubah lewat WorldBreaker/BuildPlacer. Tidak ada `LightningBolt`, cuaca, waktu atau tick-rate.
- Kode server tidak merujuk kelas klien. ItemStack dibuat secara malas.
- Pertahankan akhir baris setiap berkas (230 berkas CRLF di git, misalnya `musuh/SectRaids.java`).
- Kunci lang **baru** hanya masuk ke fragmen baru milik modul (en_us + id_id, kunci identik). Kunci lama hanya disunting
  oleh pemilik fragmennya (daftar di bawah).
- API Minecraft diverifikasi di `butterbot-fabric/reference/mcsrc`.
- Hanya compile + jar, tanpa test atau runClient.
- Progres di `docs/progres/integrasi-M<n>.md`.

Ada satu urutan kecil: M2 menyediakan `util/Sibuk` dan `DuniaApi.safeGround`, yang dibaca M4 dan M6. M2 menulis kedua API
itu di langkah pertamanya (stub publik), jadi modul lain hanya bergantung pada tanda tangannya.

### M1 "Satu Jalan Panduan"
Temuan: T1, T2, T8 (langkah Jalan), S5, S34, R1, R2, R4, R6, R29.

**Berkas:**
- Java: `guide/GuideChapters.java`, `guide/GuideProgress.java`, `goals/Guide.java`,
  `src/client/java/.../client/goals/GuideTracker.java`, `client/guide/GuideScreen.java`, `client/guide/GuideBook.java`,
  `client/guide/GuideClient.java`.
- Lang: `guide.*`, `guide_client.*`, `goals.*`, `dunia_panduan.*`, `sekte_panduan.*`, `jalan8.*`, `jalan9.*`.
- Dokumen: `docs/PANDUAN.md`, `README.md`.

**Langkah:**
1. Tambahkan `Step.optional`. Langkah manual dan langkah optional tidak menahan `currentChapter`. Tandai optional:
   `c2.formation3`, langkah duel, `c6.disciple_gc`, `c6.hufa`, `c8.seat`, `c8.a1`, `c9.level9`, `c14.puncak`,
   `c15.penerus`, `c16.perang`, dan seluruh bab 12.
2. Tambahkan `Chapter.minRealm` dan syarat aula (`hall`) untuk bab 5-6, 8-9 dan 15-16. Tambahkan `displayOrder()` = 1, 2,
   13, 3, 4, 5, 15, 6, 16, 7, 8, 9, 14, 10, 11, 12. Indeks simpan dan id tidak berubah. Judul "Bab N" memakai posisi tampil.
   Sediakan slot untuk bab 17 milik 37 setelah bab 9.
3. `currentChapter(done, data)` = bab pertama dalam urutan tampil yang ranahnya sudah sesuai dan masih punya langkah inti.
4. Langkah `c14.menara` dipindah ke bab 13 dengan id baru (`c13.menara`). Bab 14 dibuat ≤ 10 langkah.
5. Pelacak:
   - `world()` = Langkah bab 1-4 dan ranah ≥ Fondasi. Langkah sekte dilewati selama belum ada aula; flag diambil dari data
     yang sudah tersinkron.
   - `abadi()` = TI Puncak saja.
   - Bila tidak ada Langkah aktif, tampilkan langkah Kitab berikutnya.
   - Perbaiki offset `:190`.
   - Beri label segel "Langkah" dan "Kitab".
6. `Guide.currentStep` melewati `a_realm` selama `star_calamity_1` belum selesai. Larik tidak diurutkan ulang, karena bit
   disimpan per indeks.
7. Langkah `sect` dicek dengan `sect_gate_open|first_recruit_hall`, dan `goals.step.sect.how` menunjuk ke Prasasti.
8. Tukar hadiah Langkah bab 8 dan bab 9.
9. Perbaiki teks R1, R2, R6 dan bantuan tablet Jalan. Perbarui PANDUAN (16 bab, 153 langkah, Rangkai Segel, Mode
   Kehancuran, kartu hub fase 34-36) dan daftar config di README.

**Uji terima:**
- Dunia baru, pemain Pemurnian Qi yang tidak mencentang langkah manual: "Bab sekarang" = Pemurnian Qi, dan pelacak
  menunjuk langkah QR.
- Pemain Fondasi tanpa aula: pelacak masuk Jalan Dunia bab 6.
- Pemain Kenaikan Abadi: pelacak tetap tampil (langkah Kitab).
- Simpanan lama: centang manual dan klaim bab tetap.
- Segel dan judul menulis nomor bab yang sama.
- `/sect create` tidak lagi mencentang langkah `sect`, sedangkan memasang Prasasti mencentangnya.

### M2 "Sibuk, Tempat Aman & Dunia Tersambung"
Temuan: T3, T4, T7, T12 (sisi dunia), S2, S18, S19, S20, S21 (payload), S22 (dunia), R15.

**Berkas:**
- Baru: `util/Sibuk.java`.
- Sibuk dan Kehendak Langit: `trial/HeartDemons.java`, `ascension/Ascension.java`, `musuh/Ambushes.java`,
  `musuh/SectRaids.java` (CRLF; hanya mendaftar ke Sibuk), `purba/Purba.java`, `purba/HeavenlyWrath.java` (termasuk
  `challenge()` untuk 37), `batin/Batin.java`.
- Dunia: `dunia/api/DuniaApi.java`, `dunia/kota/Towns.java`, `dunia/kota/Saingan.java`, `dunia/kota/SainganData.java`,
  `dunia/kota/KedaiTeh.java`, `dunia/kota/KotaNet.java` (tab Saingan), `dunia/langit/TreasureBirths.java`,
  `dunia/naga/WildDragons.java`, `abadi/Natives.java`, `abadi/ArrivalIsland.java`.
- Reruntuhan dan peta: `dunia/reruntuhan/penjara/{ShrineRun,CoreRun,Prisons}.java`, `dunia/reruntuhan/gua/HallRun.java`,
  `dunia/reruntuhan/menara/Menara.java`, `dunia/peta/{PetaItems,PetaDomains}.java`, `dunia/DuniaSites.java`,
  `dunia/DuniaMarkers.java`, `tools/gen_dunia_reruntuhan.py` beserta structure_set yang dihasilkannya.
- Murid dan jiwa: `disciple/Disciples.java`, `dunia/dimensi/bawah/SoulJourney.java`.
- Lang: `musuh.*`, `trial.*`, `dunia_reruntuhan.*`, `dunia_peta.*`, `dunia_langit.*`, dan fragmen baru `dunia_sambung.*`.

**Langkah:**
1. `Sibuk.register/why/addProvider`. Pendaftar: Breakthrough, Tribulation, HeartDemons, DuelRules, DuelSessions,
   Sparring, Alam, Purba (3), StarCalamity, Abadi (Kenaikan/lelang), InheritanceTrials, Creation, serbuan aktif, jiwa, dan
   lantai Menara. `canFace`, `canBegin`, `Purba.busy`, `Batin.busy`, `TreasureBirths.busy`, Saingan dan WildDragons memakai
   `Sibuk.why`, lalu menunda 200 tick. Uji tekanan yang pindah dimensi menjadi `CANCELLED`. Tekanan harian bersama: maksimal
   satu peristiwa musuh sekaligus (batas per hari perlu persetujuan).
2. `DuniaApi.safeGround` diisi kota dan Kota Abadi, lalu dipakai Ambushes, Saingan dan `Natives.groundSpot`. Tambah kunci
   `musuh.ambush.refuse.town`.
3. Penjara:
   - gerbang `DAO_UNION` di `pull` dan klik jimat;
   - kebangkitan karena waktu hanya bila ada pemain ≥ Transformasi Dewa;
   - teks ranah disarankan;
   - generator memakai `jauh_dari_pusat` untuk lima set struktur.
4. Penunjuk dan Kabar: terbitkan jenis gua yang sebenarnya. Tambahkan perintah `uji peta situs bersihkan` untuk simpanan
   lama. Tambahkan jenis `kota_abadi` dan `sekte/gerbang`. Pada kedatangan pertama, Kota Abadi terdekat ditemukan otomatis.
5. Saingan: band perlombaan = min(server, tingkat harta, ranah pemain terdekat + 1). Tambahkan payload tab Saingan untuk
   kartu milik M5. `SainganData.regionOf(seed, i)` dibuat publik dan statis untuk dipakai M6. `PetaDomains` mengambil nama
   dari `DiplomasiService` bila tersedia.
6. Hadiah yatim di dunia:
   - Sobekan Segel menggantikan 2 jimat kosong di Perkuat Segel;
   - Pahala bisa ditukar di Jantung Segel;
   - Lencana Pengembara memberi satu rumor gratis per hari di Kedai Teh;
   - Kitab Formasi Kuno diganti hadiah Giok Warisan kedua.
   Semua ini perlu persetujuan pengguna, karena berupa hadiah baru.
7. `Disciples.addFollowVeto`. SoulJourney memveto (murid berjaga di tubuh) dan Menara memveto (murid menunggu).
8. R15: satu kalimat bantuan Makam vs Menara.

**Uji terima:**
- Tekanan Iblis Hati dipaksa saat Duel Pedang: peringatan tertunda sampai duel selesai.
- Sergapan Saingan saat tribulasi: tertunda.
- Penyergapan tidak terjadi di dalam kota. Natives tidak lahir di Kota Abadi.
- Pemain Fana tidak bisa mencabut jimat.
- Kabar Angin di dekat Gua Kuno mencatat `gua_kuno`.
- Murid tidak ikut ke Dunia Bawah dan Menara.
- Tidak ada perubahan blok.

### M3 "Ekonomi Sejalan"
Temuan: K2, T14, T15, T16, T17, S22 (Segel Titah), S23-S27, R10, R13, R14, R16-R18, R20, R21, R28, dan sebagian S31/S32
di `dunia_kota`.

**Berkas:**
- Kota: `dunia/kota/{Toko,BalaiLelang,Kota,KotaData,Harga,NilaiBatu,PasarGelap,KotaAbadi,PapanMisi}.java`.
- Harta dan Abadi: `harta/SpiritStones.java`, `harta/Harta.java`, `abadi/OriginStones.java`, `abadi/Auction.java` (hanya
  ekstraksi helper `Titipan`).
- Sekte: `sect/hall/Contribution.java`.
- Murid dan pil: `disciple/DiscipleCultivation.java`, `item/MarrowCleansing.java`.
- Goals: `goals/Goals.java`, `goals/DailyChores.java`.
- Dunia lain: `dunia/dimensi/DimensiData.java`, `dunia/dimensi/laut/SeaPeople.java`, `dunia/reruntuhan/RRewards.java`.
- Pusaka: `pusakalangit/HeavenDecreeSealItem.java`, `tempa/StorageRingItem.java` (hanya pembaca `isEmpty`).
- Baru: `dunia/kota/Titipan.java`.
- Lang: `dunia_kota.*`, `abadi.*`, `harta.*`, `pills.*`, `alchemy.*`, dan fragmen baru `ekonomi_selaras.*`.

**Langkah:**
1. K2: tolak cincin berisi atau terikat di `Toko.buys/view` dan `BalaiLelang.consign`.
2. T14: helper `Titipan` (persisten di `KotaData`, satu buah, whitelist, bayaran 0,6-1,0) dipakai kedua lelang. `Kota`
   memanggil `forceClose` sebelum `clear`.
3. T15: diskon esensi dan Batu Asal tidak memakan stok di bawah 9 esensi sebelum `ascended`. Tambahkan tombol diskon dan
   pratinjau biaya.
4. T16: `herbMax` toko maksimal 300 tahun, tanpa mengubah angka. Kurva non-linear hanya dengan persetujuan.
5. T17: satu konstanta kurs PK/NB. Persembahan pil = `Harga.base × mutu`. MARROW di Yue Lu diganti. Diskon Pakta dibaca
   dari `SekteApi.diplomasi()`. Semua angka baru perlu persetujuan.
6. S23: `NilaiBatu.give` tidak pernah mengeluarkan Agung. `DimensiData` dan `RRewards` memakai `NilaiBatu`. Nilai 81/729
   hanya dengan persetujuan.
7. S24-S27, R13, R14, R18: stok Pasar Abadi per pemain; toko kota tidak membeli barang fase 9; harga Mutiara disatukan;
   imbalan Titah Utusan (setelah ganti nama "Perintah Utusan") dikoreksi; Papan Misi dibatasi; hadiah harian mengikuti
   ranah. Semua angka perlu persetujuan. Tanpa persetujuan, kerjakan hanya batas stok dan larangan beli.
8. S26: murid memakai `MarrowCleansing.chance`. Pil fase 7 diberi efek murid sederhana, atau teks penolakan yang jelas.
   `Kepribadian.giftValue` tetap milik M6, jadi M3 hanya menyediakan `Harta.discipleAccepts(stack)` yang publik.
9. Segel Titah kedua: tooltip "hanya satu yang berfungsi". Perbaiki teks R16, R20 dan R21.

**Uji terima:**
- Jual cincin berisi: ditolak dengan pesan.
- Menitip lalu menghentikan server lalu masuk lagi: dibayar.
- Esensi tetap 9 setelah bintang ke-9.
- Paviliun Pil band 8 tidak menjual herba di atas 300 tahun.
- Persembahan pil Penangkal Menengah lebih besar daripada pil Penyembuh.
- Penjualan 600 NB tidak menghasilkan Batu Agung.

### M4 "Kekuatan & Kultivasi Selaras"
Temuan: T5, T6, S3, S4, S6-S10, S31 (aura, arts, tempa), S33 (teknik), R3, R5, R7, R9.

**Berkas:**
- Teknik: `technique/spec/TechniquePower.java`, `technique/spec/run/TechniqueCommands.java` (hanya info).
- Entitas dunia (hanya `setup` untuk tag): `dunia/kota/KultivatorNpc.java`,
  `dunia/dimensi/laut/{KepitingBatuEntity,UlarLautEntity,YakshaEntity}.java`, `dunia/naga/NagaLiarEntity.java`,
  `dunia/reruntuhan/RCreature.java`, `dunia/langit/TreasureGuardians.java`.
- Kultivasi: `cultivation/{Meditation,Breakthrough,XpSources,CaveAbode,Tribulation}.java`,
  `formation/ProtectiveFormations.java`, `formasi/integrate/ZoneBridge.java`, `formasi/InfoPart.java`.
- Bencana dan sinematik: `purba/StarCalamity.java`, `epic/EpicHooks.java`, `client/epic/SceneCatalogPower.java`,
  `client/epic/EpicBudget.java`.
- Duel: `client/duel/fx/{DuelFxSettings,DuelGeo}.java`, `client/duel/ui/DuelSettings.java`, `duel/DuelConfig.java`,
  `duel/session/DuelRuleSet.java`.
- Lang: `aura.*`, `aura_high.*`, `arts_*.*`, `tempa.*`, `teknik.*`, `techniques.*`, `kehancuran.*`, `kehancuran_diri.*`,
  `kehancuran_semua.*`, `meditation.*`, `breakthrough.*`, `duel_fx.*`, `duel_ui.*`, `momen.*`, dan fragmen baru
  `kekuatan_selaras.*`.

**Langkah:**
1. T5: `TechniquePower.markRealmFoe` (tag `dunia_foe` di `isTrialMob`) dipanggil di setup semua entitas dunia yang hostile.
2. T6: pesan "batas penyerapan Qi tercapai" di meditasi dan di `InfoPart`. Mengeluarkan G dari `min` hanya dengan
   persetujuan.
3. S6: `ProtectiveFormations.addProtectionProvider`, didaftarkan oleh `ZoneBridge` untuk formasi sekte pelindung yang
   berdaya.
4. S8: `EpicHooks.playerCalamity` dengan beat `CAL_*` untuk subjek pemain. Tambahkan `Tribulation.strike(player, share,
   n)` yang memanggil `HuFaTribulation.beforeStrike` dan `LightningRings`. Pakai kamera 7b dengan gerbang bahaya yang ada.
5. S10: `EpicBudget` menjadi penjaga tunggal. `DuelFxSettings.tier()` membaca `EpicBudget`, dan quad duel diambil lewat
   `EpicBudget.takeQuads`.
6. S9, S31, S33:
   - satu teks Kehancuran bertingkat dengan angka dari config;
   - "Balai Sekte" menjadi "Aula Sekte";
   - nama ranah di aura, aura_high dan tempa disamakan;
   - Kesengsaraan menjadi Tribulasi di nama teknik (Sumber tidak disentuh);
   - "Cincin Segel" menjadi "Rangkai Segel".
7. R5: satu sumber nilai percikan (0,5). R9: `DuelRuleSet.defaults` memakai `DuelConfig`. R7: tambahkan kalimat Kitab
   "IA T8: kuasai jurus T7", lewat kunci baru di fragmen M4 yang dirujuk M1.
8. Hanya dengan persetujuan pengguna: S3 (Kebangkitan minimal Normal), S4 (XP berskala dan tonggak Dunia), S7 (`+0,25 ·
   layer`), R3 (sambaran terakhir ×1,5).

**Uji terima:**
- Rival Kota realm 8 tier 3 butuh lebih dari satu pukulan di Dewa Abadi.
- Meditasi formasi 3 + batu di medan sekte: pesan batas muncul sekali.
- Gua Kediaman di dalam formasi sekte pelindung: bonus aktif.
- Bencana 1: ada omen sinematik, dan Hu Fa menanggung sambaran.
- Finisher duel ditambah adegan 7b tidak melewati `EpicBudget.QUADS`.
- grep `Pembentukan Fondasi|Abadi Sejati|Balai Sekte` di fragmen M4 = 0.

### M5 "Hub, Ensiklopedia, HUD & Istilah"
Temuan: T8 (teks ensiklopedia), T9, T18, S11, S21 (kartu), S29, S30, S31 dan S32 (glosarium dan fragmen milik M5),
S33 (hub), R22, R24 (screen), R25-R27.

**Berkas:**
- Hub dan bantuan: `client/screen/HubCardsPage.java`, `client/hub/HubCards.java`, `client/screen/HelpPage.java`,
  `client/screen/SettingsPage.java`, `client/screen/SettingsRows.java`, `client/screen/ui/KeyHints.java`.
- Klien dunia: `client/dunia/peta/PetaClient.java`, `client/dunia/kota/KotaClient.java` (kartu Saingan).
- HUD: `client/hud/*Hud.java` untuk saklar visibilitas (Dimensi, Kapal, Naga, Langit, Alarm, Laporan, Tribulasi).
- Duel: `client/duel/ui/DuelScreen.java` (hanya tautan "?"; bukan DuelSettings, yang milik M4).
- Layar baru: tautan "?" di `PetaScreen`, `MenaraScreen`, `DimensiScreen` dan `HelmScreen`.
- Lang: `screen.*`, `hub.*`, `uxpages.*`, `pengaturan.*`, `sectevent.*`, `dunia_naga.*`, `dunia_luas.*`, `core.*`, `hud.*`,
  `voice_ui.*`, dan fragmen baru `ensiklopedia_sambung.*`.
- Aset: `assets/kultivator/sounds.json`, hanya untuk R26 dan hanya setelah suara 36.6 di-commit.

**Langkah:**
1. T18: kunci kartu `bencana` = TI Puncak.
2. T9: SEKTE punya sub-koin "Aula" dan "Perguruan" (saring awalan `sekte_`). Kartu `sekte_*` otomatis memakai `hallLock`.
   Kontrak di `HubCards.java` diperbarui. Kartu ke-18 dari 37 masuk ke "Perguruan".
3. S11: topik `duel_pedang`, `sekte_hidup` (ringkasan 10 modul), `diplomasi_sekte`, `kebun_tambang`, `turnamen_sekte` dan
   `glosarium`. Tautan "?" di layar-layar baru, termasuk layar 36 lewat `HubNav.help` (M6 menambah satu panggilan di
   layarnya). Perbarui help.turnamen (90 dtk, Elo, tamu), help.misi (Misi Cerita), help.calon (Tangga) dan help.serbuan.
4. T8: help.sects ditulis ulang ("Sekte didirikan dengan Prasasti Aula; `/sekte` = Saudara Sekte").
5. S21: kartu DUNIA "Saingan" (payload dari M2).
6. S29: `peta` masuk `QUICK_REACH`; teks parry dan kamera duel diganti. S30: label "HUD Kultivator", `TribulationHud`
   selalu tampil, dan HUD baru mematuhi `hudVisible()`.
7. Glosarium S31/S32: Titah = hanya dekret pusaka; Perintah Utusan; Kantor Persembahan; Papan Ekspedisi; dua menara;
   Saudara Sekte; Tribulasi. Perbaiki nama ranah di `hub` dan `dunia_naga`, dan "Teknik Penghancur" menjadi "Jurus
   Pemusnah". Tambahkan R22, R25-R27.

**Uji terima:**
- Di 320×240 dan di GUI skala 4 pada 1366×768, setiap kartu SEKTE bisa diklik.
- Pemain tanpa aula: kartu `sekte_*` terkunci dengan alasan.
- TI Puncak yang belum naik: kartu Bencana terbuka.
- Ensiklopedia memuat topik duel dan sekte.
- Menyembunyikan HUD Qi tidak menyembunyikan peringatan tribulasi.
- `mergeLang` lulus (kunci unik dan identik).

### M6 "Sekte Hidup Menyambung" (setelah 36 selesai)
Temuan: K1, T8 (Halls dan sects), T10, T11, T12 (sisi sekte), T13, S1, S12-S17, S28, S32 (hall, sekte_*, dpower), R8, R11,
R12, R19, R23, R24 (sekte).

**Berkas:**
- Inti 36: `sekte/SekteContent.java`, `sekte/{kebun/Kebun,paviliun/Paviliun,turnamen/Turnamen,tangga/Tangga,seragam/Seragam,misi/Misi}.java`.
- Diplomasi: `sekte/diplomasi/{Simulasi,Utusan,Perang,Aksi,GerbangPlan,KetuaSekte,DiplomasiConfig}.java`,
  `sekte/api/DiplomasiService.java`.
- Turnamen dan tetua: `sekte/turnamen/AntarSekte.java`, `sekte/tetua/Generasi.java`.
- Watak dan misi: `sekte/kepribadian/{Kepribadian,KepergianMurid}.java`, `sekte/misi/{TimMisi,Kepulangan}.java`,
  `sekte/kebun/TambangRoh.java`.
- Sekte lama: `sect/hall/Halls.java`, `sect/SectCommands.java`,
  `sect/event/{Tournament,Rivals,Expeditions,Envoys,ForbiddenGround,PrimordialArk,VoidRealm,Duties}.java`.
- Murid: `disciple/DiscipleEntity.java`, `disciple/power/NatalTreasure.java` (hanya nama).
- Klien sekte: `client/sekte/tangga/TanggaClient.java` dan layar klien `sekte/**` (hanya tautan "?").
- Lang: `sects.*`, `hall.*`, `sekte.*`, `sekte_*.*` kecuali `sekte_panduan`, `dpower.*`, `disciples.*`, `voice.*`, dan
  fragmen baru `sekte_sambung.*`.

**Langkah:**
1. K1: setiap modul punya `registerContent()` yang selalu dipanggil sebelum cek `umum.aktif`.
2. S15: saat dimuat, aktivitas 13-20 dari modul yang mati diturunkan ke NONE/FOLLOW. `Kepribadian.tick20` membebaskan
   SULK sebelum `if (!on())`.
3. T10: satu prompt tantangan. T11: gerbang `mulaiAulaLevel` = 3, dan `pickSect` hanya memilih sekte yang sudah ditemui
   atau yang tingkatnya ≤ aula + 2. S14: periksa nilai balik `callRaid`.
4. T12: `GerbangPlan` memakai `SainganData.regionOf` dari M2, `rivalLink` disimpan per id, gerbang menerbitkan
   `DuniaSites` `sekte/gerbang`, dan Turnamen Elit memakai sekte D yang sudah ditemui (nama statis hanya cadangan).
   Tambahkan `DiplomasiService.meet()` untuk 37.
5. T13: tablet Misi dengan tab Harian/Cerita; aksi D "Kirim Utusan" menjadi misi X `UTUSAN`; ganti nama "Utus Pencari
   Bakat".
6. T8: `Halls.found` memutar `VoiceHooks.sectFounded`. Teks `sects.*` memakai "Saudara Sekte" dan "saudara", bukan
   "murid". Tambahkan kartu atau tombol `/sekte info` di halaman Aula.
7. S1: keputusan pengguna antara (a) `HEIR_STARS = 0` dengan ranah Transformasi Dewa, atau (b) ★9 tetap, ditulis jujur
   sebagai "setelah Dewa Abadi".
8. S12: `LumbungOverflow.store`. S13: helper `refuses()` di Expeditions, Envoys, ForbiddenGround, VoidRealm, PrimordialArk
   dan Tournament. S16: baris "Butuh templat N+" di layar 36. S17: ganti panggilan suara sesuai tabel `sekte-voices.md`,
   dengan cadangan ke kunci lama.
9. S28: `Halls.pool(UUID)` sebagai pembaca kas, dan ringkasan "PK masuk/keluar 7 hari". Kurva tambang hanya dengan
   persetujuan.
10. S32: jabatan Tetua Agung diganti "Tetua Pengatur" (id `agung` tetap); "Kantor Misi" menjadi "Kantor Persembahan";
    "Pusaka Natal Murid".
11. R8: Ketua Sekte melalui `DuelChallenges` dan `Sibuk`. R11: alias perintah. R12: nama misi. R19: nilai hadiah
    logaritmis. R23: kartu Tangga membuka layar.

**Uji terima:**
- Matikan `kebun.aktif`, lalu muat dunia yang inventarisnya berisi Inti Roh Gunung: item tetap ada, dan murid yang tadinya
  menambang kembali mengikuti.
- Klien dengan config berbeda tetap bisa tersambung.
- Aula lv1: tidak ada TUNTUT/PERANG.
- Menerima tantangan: tanpa denda kedaluwarsa, dan tanpa turnamen kedua.
- Gerbang sekte D muncul di peta dengan nama yang sama dengan domainnya.
- Murid Membangkang ditolak di Ekspedisi.

**Kepemilikan berkas yang rawan tabrakan:**

| Berkas | Pemilik | Modul lain hanya boleh |
|---|---|---|
| `Disciples.java` | M2 | – |
| `DiscipleEntity.java` | M6 | – |
| `DiscipleCultivation.java` | M3 | – |
| `Contribution.java` | M3 | M6 membaca lewat API |
| `Halls.java` | M6 | M2 dan M3 hanya membaca API yang sudah ada |
| `HubCardsPage.java`, `HubCards.java` | M5 | – |
| `dunia_kota.*` | M3 | M2 menulis kunci baru di `dunia_sambung.*` |
| `SectRaids.java` | M2 (CRLF) | M6 hanya membaca boolean `callRaid` |
| `KetuaSekte.java` | M6 | – |
| `Tribulation.java` | M4 | – |
| `Goals.java` | M3 | – |
| `Guide.java` | M1 | – |
| `Harga.java` | M3 | – |
| `KedaiTeh.java` | M2 | – |
| `sounds.json` | M5 | M6 hanya mengganti panggilan di Java |

---

## 4. Yang harus disambungkan Sekte Langit (37)

Semua butir di bawah berstatus RENCANA, karena belum ada kodenya.

1. **Registry (dari K1):** item, entitas (Feri, KapalBajak, Pijakan), payload dan komponen `aw_*` didaftarkan tanpa
   memandang `langit.aktif`. Bagian config diberi nama `sekteLangit`, dan grup chip CheatHelp `sekte_langit`. Nama
   `langit` sudah dipakai `kultivator-dunia.json` dan `uji langit`.
2. **Sibuk dan tempat aman (M2):** Bajak Awan, Raksasa Longbo, Utusan Istana Langit, tamu, feri, Terjun Awan dan pijakan
   tangga wajib memanggil `Sibuk.why` dan memakai tekanan harian bersama. Pulau masuk `Halls.inTerritory` dan
   `DuniaApi.safeGround`. Natives dan naga liar tidak boleh menganggap atap pulau sebagai tanah (pola T4). Murid terbang
   dan murid di feri memakai `Disciples.addFollowVeto`.
3. **Tantang Langit:** jangan buka `HeavenlyWrath.start`. Metode itu instan dan melewati `refusal()` serta gerbang
   `noticed` (`HeavenlyWrath.java:143`, `:200-215`). Pakai `HeavenlyWrath.challenge(player)` dari M2, yang hanya
   menjadwalkan `wrath_next`. Pemain yang belum `noticed` ditolak, atau tombolnya disembunyikan.
4. **Meditasi (M4):** faktor situs langit ×1,05-1,25 dan Qi Fajar tertelan `MULTIPLIER_CAP 3,5` (T6). Tampilkan pesan batas
   dari M4, atau pilih manfaat lain (misalnya `addDrainFactorProvider`). Provider dikalikan **sebelum** batas.
5. **Kitab Panduan (M1):** bab 17 memakai `minRealm` dan syarat `hall_level:6|tier:6`, dan masuk `displayOrder` setelah
   bab 9. Langkah `jaring` dan `sembilan` optional. Id `c17.*` ditambahkan di akhir. `GuideScreen` sudah dinamis, jadi
   cadangan "bab 16" tidak perlu.
6. **Hub (M5):** kartu `sekte_langit` masuk sub-koin "Perguruan" dengan `hallLock`. Ubah teks "7 kartu"
   (`37-sekte-langit.md:178`). Topik Ensiklopedia `sekte_langit` dan tautan "?" disiapkan sejak awal.
7. **Sekte NPC (M2/M6):** sekte langit NPC (maksimal 2) memakai satu sumber wilayah dengan Diplomasi dan Saingan, menerbitkan
   situs `DuniaSites` dengan marker peta, dan masuk lewat `DiplomasiService.meet()` (dibuat M6). Turnamen Elit dan perang
   memakai namanya bila sudah dikenal.
8. **Serbuan:** Bajak Awan memakai `SectRaids` dan `KapalBajakEntity` yang sudah ada, bukan sistem keenam. Namanya disamakan
   dengan "Perahu Bajak Langit" yang sudah ada. Perbaiki S14 lebih dulu agar Bajak Awan tidak mengumumkan serangan yang
   tidak terjadi.
9. **Istilah (M5):** jangan memakai "titah" untuk tugas Utusan Istana Langit. Pakai "Perintah Istana Langit", atau
   sambungkan ke Perintah Utusan Kota Abadi (`NpcRole.UTUSAN`, `KotaNet.TAB_TITAH`).
10. **Ekonomi (M3):**
    - Sesaji A9 (2 Agung = 1200 NB) lebih murah daripada A7-A8 (24 Tinggi = 1920 NB). Usulan: A9 = 4 Agung atau 36
      Tinggi.
    - Setoran 8 batu Tinggi untuk 500 renown harus memakai kurs persembahan yang sama.
    - Trofi `cangkang_kura_langit` perlu pemakaian nyata (misalnya bahan Altar Leluhur 36) dan entri di `Harga.BASE`.
    - Pembayaran lewat `NilaiBatu.pay`.
11. **Kepulangan misi:** kepulangan misi X lewat gerbang memakai titik kedatangan langit (`Halls.addArrivalSpotProvider`
    atau jangkar feri). Bila T13 sudah digabung, cukup satu kait.
12. **Musuh dan kekuatan:** Bajak Awan dan Longbo sebagai `RogueCultivatorEntity` sudah ber-tag `rogue`. Musuh baru berbasis
    `KultivatorNpc` harus memakai `markRealmFoe` (T5).
13. **Keamanan dan anggaran:** `ProtectedZones.addZoneProvider` (37.4); `EffectQuality`/`EpicBudget.takeQuads` dengan
    plafon di bawah `QUADS` (37.6.7, sudah sesuai); kamera 7b dengan `FLAG_SAFE`; petir hanya `setVisualOnly`, tanpa
    `LightningBolt`; mixin presipitasi hanya membatalkan, tidak mengubah blok. Komponen `sx_*` 36 di templat 11-19 sudah
    ditangani dengan benar (37.4.5).
14. **Murid dan Tetua 36:** murid baru mendaki Tangga Awan sendiri; Tetua Urusan Luar menyambut tamu langit; Laporan Pagi
    mencatat peristiwa langit (`SekteLog`).

## 5. Verifikasi ulang KRITIS dan TINGGI (fix:finish, setelah M1-M6)

Diperiksa terhadap kode di disk setelah build Gradle `compileJava compileClientJava jar` = BUILD SUCCESSFUL. Belum diuji di
dalam game (runClient dilarang); kolom "bukti" menunjuk berkas:baris.

| # | Status | Bukti (berkas:baris) | Catatan |
|---|---|---|---|
| K1 | FIXED | `sekte/SekteContent.java:58-70` (`registerContent()` 10 modul sebelum cek `umum.aktif`) | logika runtime tetap ikut sakelar |
| K2 | FIXED | `dunia/kota/Toko.java:114`, `dunia/kota/BalaiLelang.java:444`, `tempa/StorageRingItem.unsafeToSell` | pesan `kultivator.ekonomi.cincin_berisi` |
| T1 | FIXED | `guide/GuideChapters.java:61` (DISPLAY_ORDER), `:460` (currentChapter + gerbang ranah/aula), `guide/GuideProgress.java:122-128` | langkah manual/opsional tidak menahan bab |
| T2 | FIXED | `client/goals/GuideTracker.java:58-60,127,247` | mode Langkah/Dunia/Abadi/Kitab, label segel |
| T3 | FIXED | `util/Sibuk.java` (baru), `trial/HeartDemons.java:209,291,299,348`, `ascension/Ascension`, `dunia/kota/Saingan.java:101`, `musuh/Ambushes.java:75` | tunda 200 tick, maks 1 musuh sekaligus, 3/hari |
| T4 | FIXED | `dunia/api/DuniaApi.safeGround`; `musuh/Ambushes.java:244`, `abadi/Natives.java:218,260`, `dunia/kota/Saingan.java:113` | kota + Kota Abadi aman |
| T5 | FIXED | `technique/spec/TechniquePower.java:136,692-700`; KultivatorNpc:137, KepitingBatu:55, UlarLaut:56, Yaksha:69, NagaLiar:216, RCreature:100 | |
| T6 | FIXED | `cultivation/Meditation.java:379,388` (G di luar batas, maks 1,5); `formasi/InfoPart.meditationLimit`; `client/formasi/FieldPanel.meditation` (finish) | baris Meditasi di panel Medan |
| T7 | FIXED | `penjara/Prisons.java:60-68`, `ShrineRun.java:95,109`, `CoreRun.java:274`; 5 structure_set `kultivator:jauh_dari_pusat` (6 / 12 chunk) | hanya chunk baru |
| T8 | FIXED | `goals/Guide.java:102` (sect_gate_open/first_recruit_hall), `sect/hall/Halls.java:546` (suara Langit), `goals.step.sect.how`, `sects.*` Saudara Sekte | |
| T9 | FIXED | `client/screen/HubCardsPage.java:56,99,108-116` (sub-koin Aula/Perguruan, hallLock otomatis, gulir) | |
| T10 | FIXED | `sekte/diplomasi/Simulasi.java:478-484` (tanpa TANTANGAN), `sekte/turnamen/AntarSekte.java:80` (pending dibersihkan) | |
| T11 | FIXED | `sekte/diplomasi/DiplomasiConfig.java:36` (mulaiAulaLevel 3), `Simulasi.java:298,366` | |
| T12 | PARTIAL | `Simulasi.java:71-80` (rivalLink per id), `AnggotaSekte.java:36,96` (situs `sekte/gerbang`), `dunia/peta/PetaDomains.java:68`, `sect/event/Tournament.java:477-479` | GerbangPlan sengaja tidak dipindah ke `SainganData.regionOf` (gerbang dunia lama akan pindah); Saingan dan Diplomasi tetap dua daftar; nama cadangan "Lembah Seribu Racun" masih di dunia_kota dan dunia_peta |
| T13 | FIXED | `client/sectevent/MissionPage.java:37-41` (Harian/Tim/Pencari Bakat/.../Cerita/Kabar), `sekte/diplomasi/Utusan.java:401`, `sekte/api/MisiService` | |
| T14 | FIXED | `dunia/kota/Titipan.java` (baru, KotaData), `dunia/kota/Kota.java:74` (SERVER_STOPPING forceClose), `BalaiLelang.java:57,380` | |
| T15 | FIXED | `goals/Goals.java:69,152-158` (sisakan 9 esensi, `/kultivasi diskonbintang`) | |
| T16 | FIXED | `dunia/kota/Harga.java:29,251` (herba toko maks 300 tahun, kurva 2 x (1+(umur/100)^1,6)) | |
| T17 | FIXED | `sect/hall/Contribution.java:66-80,111,166-171` (Yue Lu 6/6/60/300/1000, Sumsum Sempurna 400, pil = base/2 x mutu, Pakta 10%) | |
| T18 | FIXED | `client/screen/HubCardsPage.java:193,263` (kunci bencana = TI Puncak) | |

Ringkasan: KRITIS 2/2 FIXED; TINGGI 17 FIXED, 1 PARTIAL (T12, disengaja demi simpanan lama).

Kabel tambahan oleh finish (needsFromOthers tanpa pemilik): HuFaTribulation `onRitual` + `join` saat Bencana Bintang;
FieldPanel baris batas meditasi; `XpSources.milestone` di TowerRun, HallRun, CoreRun (sekali per penjara), Courts.pass,
DarahJiaoItem; tooltip `ekonomi.nama.*` (Dupa, Dupa Arwah, Mutiara Esensi Abadi, Esensi Energi Makam); Lencana
Pengembara baris rumor gratis; KedaiTeh 81 NB; VoiceEvents tanpa grup `langit`; Kepribadian.giftValue memakai
`Harta.discipleAccepts`; teks hall/sectbuild/musuh/sekte_misi/sekte_panduan/screen/duel/dunia_peta/dunia_panduan.
Masih terbuka (SEDANG S11): segel "?" belum ada di 10 layar Sekte yang Hidup (topik Ensiklopedia sudah ada).
