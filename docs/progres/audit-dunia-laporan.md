# Laporan audit:dunia — Dunia & Petualangan (read-only)

Tanggal: 2026-10-22. Cakupan: DESIGN 34 (Dunia Kultivasi yang Luas: kota, pengembara, saingan, gua leluhur, penjara,
menara, langit, harta lahir, cuaca roh, naga, evolusi binatang, Dunia Bawah, Laut Tanpa Batas, kapal, peta), Alam Rahasia
(`alam/**`), Alam Abadi dan Kota Abadi (`abadi/**`, `dunia/kota/KotaAbadi`), purba, binatang roh, desa, kultivator liar
(`musuh/**`), plus DESIGN 36 (Sekte yang Hidup, kode yang ada ditandai **SEDANG DIBANGUN**) dan rancangan
`docs/rancangan/37-sekte-langit.md` (ditandai **RENCANA**). Semua bukti diperiksa di kode; tidak ada file proyek yang
diubah dan Gradle tidak dijalankan.

## Ringkasan

| Tingkat | Jumlah |
|---|---|
| KRITIS | 0 |
| TINGGI | 5 |
| SEDANG | 8 |
| RENDAH | 4 |

Yang sudah baik: gerbang ranah gua leluhur (`RuinLooks.minRealm` Qi / Inti Emas / Transformasi Dewa), kapal (Inti Emas /
Jiwa Baru Lahir + aula 3 / Transformasi Dewa + aula 7), laut (Inti Emas), Istana Naga (Transformasi Dewa), Penglai
(Penyatuan Dao), Dunia Bawah (Jiwa Baru Lahir), naga liar muncul di Jiwa Baru Lahir dan ditundukkan di Transformasi Dewa
(Petir: Penyatuan Dao), evolusi tingkat 4-6 mengikuti `TIER_REALM`. Semua 62 advancement `dunia/*` benar-benar diberikan
oleh kode, sehingga tidak ada langkah Panduan bab 13/14 yang mustahil tercentang. Resep kapal cocok dengan teks Panduan.
Harta lahir sudah menolak kota, Kota Abadi dan wilayah sekte. Setiap fitur punya topik bantuan.

Tabel progresi (yang dibuka per ranah, dari kode):

| Ranah | Yang terbuka di Dunia |
|---|---|
| Fana | kota (berjalan masuk), peta, peristiwa langit, cuaca roh, menara lantai 1-10 (tanpa gerbang), **penjara iblis (tanpa gerbang, lihat D1)** |
| Pemurnian Qi | pengembara, penyergapan, Gua Kecil, harta lahir T1, Ujian Darah Api |
| Pembangunan Fondasi | (tidak ada yang baru di Dunia; toko/lelang naik pita) |
| Inti Emas | Perahu Awan, Gua Agung, Laut Tanpa Batas, Aula Kehampaan, ajak pengembara |
| Jiwa Baru Lahir | Dunia Bawah, naga liar terlihat, Kapal Sekte, Teh Penenang |
| Transformasi Dewa | tundukkan Jiao, Gua Kuno, Istana Naga, Bahtera Langit, binatang tingkat 4 |
| Penyatuan Dao | Penglai, Jiao Petir, (sasaran) penjara iblis |
| Kenaikan Abadi | Kota Abadi, binatang tingkat 5, Makam Surgawi |
| Dewa Abadi | binatang tingkat 6, Coret Nama, menara lantai 100 |

## Tabel temuan

| # | Tingkat | Ringkas | Bukti utama |
|---|---|---|---|
| D1 | TINGGI | Penjara Iblis (sasaran Penyatuan Dao) bisa dibuka pemain ranah apa pun, dekat spawn | `penjara/ShrineRun.java:106-125`, `penjara/CoreRun.java:268`, structure_set `penjara_iblis.json` |
| D2 | SEDANG | Aturan W4 (6 chunk dari spawn) tidak berlaku untuk gua, penjara, menara | structure_set `reruntuhan/*`, `menara_seratus`, `penjara_iblis` = `minecraft:random_spread` |
| D3 | TINGGI | Sergapan Saingan tidak punya pemeriksaan "sibuk/aman" seperti Penyergapan lama | `kota/Saingan.java:514-515` vs `musuh/Ambushes.java:193-230` |
| D4 | TINGGI | Penyergapan (`musuh/Ambushes`) tetap terjadi di dalam Kota, padahal Panduan bilang kota aman | `musuh/Ambushes.java:193-230`, `kota/Towns.java:93`, `dunia_panduan.id_id.json:27` |
| D5 | TINGGI | Makhluk Alam Abadi muncul di atas/di dalam Kota Abadi | `abadi/Natives.java:211-257`, `worldgen/structure/kota/kota_abadi.json:15` |
| D6 | TINGGI (SEDANG DIBANGUN) | Tiga (rencana: empat) sistem "sekte NPC" yang tidak selaras: Saingan, domain Peta, Diplomasi | `SainganData.java:99-100`, `PetaDomains.java:42-85`, `diplomasi/Simulasi.java:60-63`, `GerbangPlan.java:11-12` |
| D7 | SEDANG | Gulungan Penunjuk dan Kabar Angin mencatat gua tingkat apa pun sebagai "Gua Kecil" (situs ganda tersimpan) | `peta/PetaItems.java:43-45,119-120`, `kota/KedaiTeh.java:265-273`, `DuniaSites.java:312-313` |
| D8 | SEDANG | Kota Abadi tidak bisa dicari (tidak ada penunjuk/kabar), Panduan hanya bilang "cari" | `PetaItems.java:43-45`, `KedaiTeh.java:58`, `dunia_panduan.id_id.json:45` |
| D9 | SEDANG | Kekuatan saingan di perlombaan harta memakai pita server, bukan tingkat harta/pemain yang hadir | `kota/Saingan.java:122-129,358-359` |
| D10 | SEDANG | Kelompok saingan tak terlihat oleh pemain (daftar, dendam, pemimpin yang sudah kalah) | `dunia_kota.id_id.json:561` (hanya `uji`), daftar `HubCards.register` |
| D11 | SEDANG | Urutan Panduan bab 14: menara lantai 10 (Fondasi) dan saingan ada di "Ujung Dunia"; bab 14 berisi 13 langkah | `guide/GuideChapters.java:237-249`, `menara/TowerRun.java:163-164` |
| D12 | SEDANG | Hadiah yatim: Sobekan Segel, Pahala, Lencana Pengembara, Kitab Formasi Kuno | `ShrineRun.java:117`, `CoreRun.java:182-186`, `HallRun.java:198` |
| D13 | RENDAH | Razia pasar gelap: penjaga maks Inti Emas, suap naik per ranah | `kota/PasarGelap.java:218,232` |
| D14 | RENDAH | Harga pasar gelap jauh di atas biaya resep untuk barang yang sama | `kota/Harga.java:99-101`, `recipe/dunia/*.json` |
| D15 | RENDAH | Istilah bentrok: Titah, Abadi Sejati vs Dewa Abadi, Saingan ganda, Bajak Langit vs Bajak Awan | lihat detail |
| D16 | RENDAH | Menara Seratus Lantai dan Makam Surgawi sama-sama "panjat lantai" tanpa rujukan silang | `alam/RealmSpecs.java:33`, `menara/LobbyRun.java:151-170` |
| D17 | SEDANG (RENCANA) | Catatan integrasi Sekte Langit (37) dengan Dunia | `docs/rancangan/37-sekte-langit.md:1128-1240` |

## Detail

### D1 — TINGGI: Penjara Iblis bisa dibuka pemain ranah apa pun, dan bisa lahir dekat spawn

**Bukti.**
- `dunia/reruntuhan/penjara/ShrineRun.java:106-125` (`pull`): tidak ada pemeriksaan ranah. Setiap pemain (juga Fana) bisa
  mencabut kelima jimat; pada jimat ke-5 `Prisons.breakSeal(...)` langsung dipanggil.
- `CoreRun.java:268`: darah raja iblis `2000 × (1 + 0.5 × (party − 1))`, tidak berskala ranah. DESIGN 34.5.6
  "Balance: Dao Union-Immortal Ascension".
- Kartu peringatan dan bantuan tidak menyebut ranah sama sekali: `dunia_reruntuhan.id_id.json:159-160`
  (`jimat.ui.cost/break`), `:70` (`help.penjara_iblis`).
- Struktur `penjara_iblis` memakai `minecraft:random_spread` tanpa `min_chunks` (D2), jadi pemain baru bisa menemukannya di
  dekat spawn. Segel juga turun sendiri (`sealDecayPerDay`, Gerhana Darah −1/jam), dan raja bangkit "saat seseorang berdiri
  di aula" (`Prisons` javadoc), jadi penambang Pemurnian Qi bisa tersandung di aula y ≈ −24.

**Kenapa memutus kesinambungan.** Satu-satunya fitur bertarget Penyatuan Dao di overworld tidak punya gerbang, padahal gua
leluhur, kapal, laut, naga dan Dunia Bawah semuanya punya. Pemain awal mendapat 5 Sobekan Segel gratis (lihat D12) dan bos
yang mustahil dilawan (DeathGuard menyisakan 1 HP, tidak ada pelajaran). Rasanya seperti dinding tanpa peringatan.

**Perbaikan.**
1. `ShrineRun.pull` dan `ShrineRun` klik jimat: tolak di bawah `Realm.DAO_UNION` (konfig baru `reruntuhan.prisonMinRealm`
   "dao_union"), pesan baru `kultivator.reruntuhan.penjara.jimat.ranah` ("Jimat ini menolak tanganmu; segelnya terlalu
   berat untuk ranah %s."), pola `GateRun.touch` (`gua/GateRun.java:74-82`).
2. `CoreRun` kebangkitan saat segel pecah karena waktu: bangkit hanya bila ada pemain ≥ Transformasi Dewa di aula; pemain di
   bawahnya mendapat baris peringatan + `kultivator:qi` merah (tanpa bos).
3. Kartu peringatan (`jimat.ui.*`) dan `help.penjara_iblis`: tambah "Disarankan: Penyatuan Dao ke atas".
4. Lihat D2 untuk jarak dari spawn.

### D2 — SEDANG: Aturan W4 (cincin 6 chunk di sekitar spawn) tidak berlaku untuk gua, penjara dan menara

**Bukti.** DESIGN 34.0 W4: "never inside the 6-chunk ring around world spawn". Kota dan kuil Dunia Bawah memakai
`kultivator:jauh_dari_pusat` (`min_chunks` 6), tetapi `resourcepacks/dunia_reruntuhan/.../structure_set/reruntuhan/
gua_kecil.json`, `gua_agung.json`, `gua_kuno.json`, `penjara/penjara_iblis.json`, `menara/menara_seratus.json` memakai
`minecraft:random_spread` tanpa `min_chunks`.

**Kenapa.** Dunia baru bisa memunculkan Gua Kuno (Transformasi Dewa) atau penjara iblis tepat di spawn; pemain baru
langsung bertemu konten akhir.

**Perbaikan.** Di `tools/gen_dunia_reruntuhan.py` ganti `type` lima set itu menjadi `kultivator:jauh_dari_pusat` dengan
`min_chunks` 6 (penjara dan Gua Kuno: 12), lalu jalankan ulang generator (set hanya memengaruhi chunk baru; aman untuk
save). Kode yang membaca tidak berubah (placement tetap turunan random spread, `locate` tetap bekerja).

### D3 — TINGGI: Sergapan kelompok Saingan tidak memeriksa keadaan "sibuk" atau tempat aman

**Bukti.** `dunia/kota/Saingan.java:514-515` hanya memeriksa `Towns.inTown`, `canSeeSky`, `isSoul`, kreatif dan penonton.
Bandingkan `musuh/Ambushes.java:193-230` (`refusal`): Peaceful, dimensi (hanya Overworld/Nether), sedang terbang/naik
kendaraan, `Alam.inRun`, `HeartDemons.inTrial`, `DuelRules.inDuel`, `Sparring.inDuel`, `Tribulation.isActive`,
`Breakthrough.isBusy`, 160 blok dari aula sendiri, wilayah sekte mana pun, desa. `Saingan.strike` (`:543-549`) langsung
memunculkan pemimpin + 3 anggota yang bermusuhan.

**Kenapa.** Sergapan dijadwalkan 60-300 detik setelah klaim harta, saat pemain sering langsung bermeditasi, menerobos atau
masuk alam rahasia. Kelompok saingan bisa mendarat di tengah Kesengsaraan Langit, di wilayah sekte sendiri, di dalam menara,
laut, Alam Abadi atau di atas kapal yang sedang terbang. Melanggar aturan 7b "tidak pernah saat bahaya nyata" dan rasa aman
yang dijanjikan wilayah sekte. Juga Peaceful tidak dihormati (Penyergapan lama menolak Peaceful).

**Perbaikan.** Jadikan `musuh/Ambushes.refusal(ServerPlayer)` publik (atau tambah `musuh/Musuh.ambushRefusal`) dan panggil
di `Saingan.tickAmbushes` sebelum `strike`: bila ada penolakan, jadwal ulang +200 tick seperti cabang `safe` sekarang (maks.
misalnya 6 kali, lalu batalkan dan turunkan dendam 5). Tambah juga `DuniaApi.routes().aboard(player)`.

### D4 — TINGGI: Penyergapan lama tetap terjadi di dalam Kota Kultivator

**Bukti.** `musuh/Ambushes.java:193-230` tidak mengenal kota fase 34; `Towns.inTown` (`dunia/kota/Towns.java:93`) hanya
dipakai oleh `Saingan.java:514`. Pengecualian `level.isVillage(...)` (`:227`) hanya kena di sekitar ranjang penginapan
(POI desa vanilla), tidak seluruh kota 130 × 130. Padahal `dunia_panduan.id_id.json:27` (c14.saingan) berkata "Kota adalah
tempat aman: sergapan tidak masuk kota", DESIGN 34.5.3 "towns are safe ground", dan `spawn_overrides` kota mengosongkan
monster.

**Kenapa.** Dua sistem penyergapan, dua aturan kota. Pemain yang percaya Panduan diserang 1-3 kultivator liar di alun-alun
saat lelang senja; penjaga kota tidak membela (penjaga hanya memusuhi yang ditandai kota).

**Perbaikan.** Tambah layanan kecil di `dunia/api` (mis. `DuniaApi.safeGround(ServerLevel, BlockPos)` dengan default
`false`, diisi modul K dengan `Towns.inTown` + Kota Abadi), panggil di `Ambushes.refusal` (kunci baru
`kultivator.musuh.ambush.refuse.town`). `musuh` hanya membaca API spine, jadi tidak ada impor silang modul. Pakai layanan
yang sama untuk D3 dan D5.

### D5 — TINGGI: Makhluk Alam Abadi muncul di Kota Abadi

**Bukti.** `abadi/Natives.java:211-257`: tiap 400 tick per pemain di Alam Abadi, 1-2 elite (25 % kultivator liar)
dimunculkan 24-40 blok dari pemain di `Heightmap MOTION_BLOCKING_NO_LEAVES` tanpa pemeriksaan kota. Kota Abadi melayang di
y 150-170 (`worldgen/structure/kota/kota_abadi.json:15`, `absolute 150`), jadi heightmap kolom kota = atap dan jalan kota.
DESIGN 34.2.4 menjadikan Kota Abadi "safe ground" (hanya `spawn_overrides` untuk spawn alami, yang tidak menahan Natives).

**Kenapa.** Kuliah Dao meminta pemain duduk 60 detik dekat penceramah; Tekanan Abadi menambah 3 % HP per pukulan. Kota yang
dimaksudkan sebagai tempat istirahat pasca-kenaikan malah menjadi titik spawn musuh elite, dan NPC kota bukan `Enemy`, jadi
tidak ada yang melawan.

**Perbaikan.** Di `Natives.groundSpot` tolak titik yang `DuniaApi.safeGround(level, pos)` (D4) atau
`DuniaAnchors.protectedNear(level, pos, 0)` dengan anchor `kota/*`; juga jangan spawn bila pemain sendiri di dalam kota.

### D6 — TINGGI (SEDANG DIBANGUN): Sekte NPC ada di tiga tempat yang tidak saling cocok

**Bukti.**
- Saingan (DESIGN 34.5.3): `rivalGroups` 6 (3-8), wilayah acak 900-5 100 blok dari pusat (`kota/SainganData.java:99-100`),
  nama `kultivator.kota.grup.*`.
- Peta (34.5.16): `npcDomains` 8, tetapi `PetaDomains.of` (`peta/PetaDomains.java:42-85`) hanya menggambar wilayah
  kelompok saingan (6) bila ada; tanpa saingan, 12 nama sendiri `kultivator.peta.domain.*`. Kota di dalam domain
  ditampilkan "dijaga" kelompok itu (`PetaSheet.java:61`), padahal kelompok yang sama menyergap pemain.
- Diplomasi (DESIGN 36.2.9, modul D, **SEDANG DIBANGUN**): 8 sekte (`jumlahSekte`), gerbang gunung di cincin 1 500-2 900
  blok menurut `GerbangPlan` (`sekte/diplomasi/GerbangPlan.java:11-12`), nama dari `GeneratorNama`, dan `rivalLink` hanya
  dipasangkan per indeks (`Simulasi.java:60-63`); sekte 7 dan 8 tanpa pasangan. DESIGN 36.2.9 menulis wilayahnya "selaras
  dengan wilayah kelompok saingan Dunia bila ada", catatan `docs/progres/sekte-D.md` mengakui posisinya murni dari seed.
- Gerbang sekte NPC tidak diterbitkan ke `DuniaSites` (tidak ada `DuniaSites`/`DuniaMarkers` di `sekte/**`), tidak ada
  jenis Gulungan Penunjuk `sekte_gerbang` (tag `tags/worldgen/structure/sekte_gerbang.json` ada, tapi tidak dipakai).
- Nama nyaris kembar: "Lembah Seribu Racun" ada di `kota.grup.4` dan `peta.domain.1`; generator Diplomasi menghasilkan
  bentuk seperti "Lembah Racun Seribu" (contoh DESIGN 36.2.9). Rancangan 37 menambah jenis keempat (sekte langit NPC, D17).

**Kenapa.** Peta menunjukkan domain "Keluarga Mo" di satu tempat, gerbang gunung Diplomasi berdiri di tempat lain dengan nama
lain, dan papan Diplomasi menyebut "berjalan bersama" kelompok yang wilayahnya jauh dari gerbangnya. Pemain tidak bisa
menyusun satu gambaran dunia; hadiah "temukan sekte lain" (c16.rival) tidak terbantu oleh peta.

**Perbaikan (untuk modul D 36 dan modul T 34).**
1. Satu sumber kebenaran: `GerbangPlan.build` menerima pusat wilayah dari `DuniaApi.rivals().region(id)` bila tersedia
   (worldgen hanya butuh seed: `SainganData.seed` sudah deterministik dari `worldSeed ^ 0x5A1A6A4L`, jadi pusat bisa
   dihitung murni dari seed oleh fungsi statis bersama, mis. `SainganData.regionOf(seed, i)`), dan `jumlahSekte` default
   sama dengan `rivalGroups` (atau `rivalGroups` default 8).
2. `AnggotaSekte`/`GerbangSekte` menerbitkan `DuniaSites.publish(level, "sekte/gerbang", pos, tingkat)` saat anchor gerbang
   dimuat; tambah jenis marker `sekte/gerbang` di `DuniaMarkers` dan `{"sekte_gerbang","sekte/gerbang"}` di
   `PetaItems.POINTER_KINDS`.
3. `PetaDomains.of`: bila Diplomasi aktif, ambil nama dan lambang dari `DiplomasiService` (nama sekte = nama domain), bukan
   dua nama berbeda.
4. Ganti nama lang `kultivator.peta.domain.1` agar tidak sama dengan `kultivator.kota.grup.4`.

### D7 — SEDANG: Penunjuk dan Kabar Angin mencatat gua tingkat apa pun sebagai "Gua Kecil"

**Bukti.** `peta/PetaItems.java:43-45` (`{"reruntuhan", "reruntuhan/gua_kecil"}`) dan `kota/KedaiTeh.java:265-273`
(`default -> "reruntuhan/gua_kecil"`) mencari tag `#kultivator:reruntuhan` (berisi kecil, agung, kuno), lalu
`DuniaSites.locate` (`DuniaSites.java:312-313`) mencari situs berjenis `reruntuhan/gua_kecil` di dekatnya; anchor Gua Kuno
menerbitkan jenisnya sendiri, jadi tidak cocok, dan situs **baru yang salah** (jenis kecil, tingkat 1) disimpan permanen di
SavedData `kultivator:dunia_sites`.

**Kenapa.** Pemain Pemurnian Qi membayar 1 NB atau memakai gulungan, berjalan ribuan blok ke "Gua Kecil" yang ternyata Gua
Kuno bergerbang Transformasi Dewa; peta memperlihatkan dua penanda untuk gua yang sama. Ini juga risiko save (situs palsu
menetap).

**Perbaikan.** Setelah `findNearestMapStructure`, baca struktur sebenarnya (`level.structureManager()
.getStructureWithPieceAt` atau tiga pencarian per tag tingkat) dan terbitkan jenis yang benar; lebih baik lagi pilih tag
tingkat sesuai ranah pemain (`RuinLooks.minRealm`). Tambah perintah `uji peta situs bersihkan` untuk menghapus situs
`reruntuhan/gua_kecil` yang tidak punya anchor dalam 96 blok (perbaikan save lama).

### D8 — SEDANG: Kota Abadi tidak bisa dicari

**Bukti.** `PetaItems.POINTER_KINDS` (`:43-45`) dan `KedaiTeh.RUMOUR_KINDS` (`:58`) tidak punya `kota_abadi`; tag
`tags/worldgen/structure/kota_abadi.json` ada tetapi hanya dipakai `uji kota_abadi cari` (op). Struktur hanya lahir di chunk
**baru** Alam Abadi (spasi 48 chunk), sedangkan `c14.kota_abadi.body` (`dunia_panduan.id_id.json:45`) hanya berkata "cari
distrik terapung Kota Abadi".

**Kenapa.** Pemain yang sudah lama di Alam Abadi (chunk di sekitar Pulau Kedatangan sudah ada) tidak tahu harus ke mana;
langkah Panduan bisa macet tanpa petunjuk.

**Perbaikan.** Tambah `{"kota_abadi", "kota/kota_abadi"}` ke `POINTER_KINDS` (aktif hanya di Alam Abadi, kunci lang
`kultivator.peta.pointer.kind.kota_abadi`), dan saat kenaikan pertama (`abadi/Ascend` atau `ArrivalIsland`) jalankan sekali
`DuniaSites.locate(level, kota_abadi, arrival, 64, "kota/kota_abadi", 4)` + `discover` sehingga peta langsung menunjukkannya;
tambahkan kalimat arah ke `c14.kota_abadi.body`.

### D9 — SEDANG: Kekuatan saingan di perlombaan harta tidak mengikuti tingkat harta

**Bukti.** `kota/Saingan.java:122-129` (`band`): `min(1 + hari/10, pemain terkuat 7 hari terakhir + 1)`; dipakai di
`:358-359` untuk kelompok yang tiba di harta. Tingkat harta dihitung dari pemain terkuat dalam 1 000 blok (DESIGN 34.5.9),
dan sergapan memakai `min(band, ranah pemain + 1)` (`:547`), tetapi perlombaan tidak.

**Kenapa.** Di server bersama, harta T1 milik pemain Pemurnian Qi didatangi pemimpin setingkat pemain terkuat server + 1.
Harta herba 100 tahun dijaga Transformasi Dewa: hadiah tak sebanding, dan pemain baru belajar bahwa harta lahir bukan
untuknya.

**Perbaikan.** Di `Saingan` saat spawn perlombaan: `band = min(band(server), realmOfTier(birth.tier()) + 1, ranah tertinggi
pemain dalam 128 blok + 1)`, dengan `realmOfTier` T1 Fondasi, T2 Jiwa Baru Lahir, T3 Penyatuan Dao, T4 Dewa Abadi.

### D10 — SEDANG: Kelompok saingan tidak terlihat oleh pemain

**Bukti.** Daftar kelompok, dendam dan "dikalahkan olehmu" hanya ada di `uji saingan daftar` (op;
`dunia_kota.id_id.json:561`). Hub DUNIA hanya mendaftarkan `duel_pedang`, `peta_dunia`, `menara_seratus` (ditambah empat
kartu bawaan); tidak ada kartu Kota/Saingan/Kapal/Naga. Advancement `dunia/rival_all` ("kalahkan keenam pemimpin") tidak bisa
dilacak pemain.

**Kenapa.** Fitur "dendam" yang menentukan sergapan tidak pernah diperlihatkan; pemain tidak tahu siapa yang masih memburunya
atau pemimpin mana yang tersisa.

**Perbaikan.** Kartu hub DUNIA "Saingan" (di `client/dunia/kota/KotaClient`) yang membuka halaman ringkas: nama, warna,
wilayah (arah + jarak, bila sudah ditemukan di peta), dendam padaku (bar 0-100), pemimpin sudah kalah ✓. Data cukup dari
`SainganData` lewat payload `KotaNet.Screen` yang sudah ada (tab baru). Alternatif minimum: tampilkan di kartu domain Peta.

### D11 — SEDANG: Urutan Panduan bab 14 dan menara yang langka

**Bukti.** `guide/GuideChapters.java:237-249`: bab 14 "Ujung Dunia" (deskripsi "Saingan, menara, Dunia Bawah, naga…",
sasaran Jiwa Baru Lahir-Dewa Abadi per DESIGN 34.9) berisi 13 langkah (aturan 34.9: 4-10), dimulai `c14.saingan` dan
`c14.menara` (`adv:dunia/tower_10`). Lantai 10 = Rank step `round(0.64 × 10) = 6` (`menara/TowerRun.java:163-164`), yaitu
Pembangunan Fondasi. Pagoda menara berspasi 160 chunk (± 2 500 blok), dan pemain Pemurnian Qi belum punya kapal.

**Kenapa.** Konten pita pertama menara ditujukan untuk awal permainan, tetapi Panduan baru menyebutnya setelah Kapal (Inti
Emas), dan strukturnya jarang. Hasilnya menara terasa "terlambat", lalu lantai 1-10 menjadi remeh saat ditemukan.

**Perbaikan.** Pindahkan `c14.menara` ke bab 13 (setelah `c13.gua`) dan `c13.kapal` tetap terakhir; pindahkan `c14.bajak`
ke bab 13 atau gabung dengan `c14.pelayaran` agar bab 14 ≤ 10 langkah (step id baru tidak menggeser centang lama: centang
disimpan per id). Turunkan spasi `menara_seratus` ke 120/48 (masih dalam ±25 % 34.2.1) dan tambahkan kalimat "pakai
Gulungan Penunjuk (Menara)" ke `c13.menara.body`.

### D12 — SEDANG: Hadiah yang tidak dipakai apa pun

**Bukti.**
- **Sobekan Segel**: satu-satunya referensi kode adalah pemberiannya (`penjara/ShrineRun.java:117`); tidak ada resep, tag
  atau pembeli. Tooltip `dunia_reruntuhan.id_id.json:9` tidak menyebut kegunaan. DESIGN 34.5.6 menyebutnya
  "heaven-seal-shard-like material".
- **Pahala**: `CoreRun.java:182-186` hanya menambah angka di `extra.reruntuhan.pahala`, ditampilkan di halaman menara
  (`Menara.java:379`), tidak membeli apa pun.
- **Lencana Pengembara**: hanya trofi (`dunia_kota.id_id.json:582`).
- **Kitab Formasi Kuno**: hadiah gua tertinggi (Transformasi Dewa-Penyatuan Dao, `gua/HallRun.java:198`) tetapi hanya buku
  lore empat halaman; kait diskon `formasi` di DESIGN 34.5.5 tidak dibuat.

**Kenapa.** Godaan Hong Xin tidak ada artinya bila hasilnya sampah; hadiah gua tertinggi lebih lemah dari gua menengah.

**Perbaikan.** Sobekan Segel: terima sebagai pengganti `blank_talisman` di Perkuat Segel (`CoreRun` reinforce: 1 Sobekan =
2 jimat kosong) dan/atau tambah ke tag bahan jimat; jual ke Makelar Gelap (`Harga.BASE` 0.6 × nilai). Pahala: tukar di
Jantung Segel (mis. 5 Pahala = 1 Giok Warisan bertema penyegelan) atau beri bonus renown sekte per 10. Lencana Pengembara:
kumpulkan 5 untuk diskusi Dao tambahan per hari. Kitab Formasi Kuno: bila `formasi` punya kait biaya, pakai; bila tidak,
tambahkan Giok Warisan kedua atau batu Supreme agar Gua Kuno > Gua Agung.

### D13 — RENDAH: Razia pasar gelap tidak berskala

**Bukti.** `kota/PasarGelap.java:218`: penjaga razia dibatasi Pembangunan Fondasi-Inti Emas; `:232` suap = 20 NB × ordinal
ranah pemain.
**Kenapa.** Pemain Jiwa Baru Lahir ke atas mengalahkan razia dengan mudah tetapi diminta suap makin mahal; risiko hilang,
biaya naik.
**Perbaikan.** `band = min(ranah pemain, Realm.DAO_UNION.ordinal())` di `raid`, atau suap tetap 20 NB × band penjaga.

### D14 — RENDAH: Harga pasar gelap tidak masuk akal dibanding resep

**Bukti.** `kota/Harga.java:99-101`: Selendang Pengikat Naga 800 NB, Dupa Arwah 300 NB, Mutiara Penunjuk Laut 900 NB (× 1.5
di pasar gelap). Resep `recipe/selendang_pengikat_naga.json` (± 160 NB + bahan), `recipe/dunia/dimensi_dupa_arwah.json`
(2 batu Menengah = 18 NB + kayu), `dimensi_mutiara_penunjuk_laut.json` (1 batu Menengah + bahan vanilla).
**Kenapa.** "Satu-satunya jalan pintas" pasar gelap tidak pernah layak dibeli; jalur Kota → Dunia Bawah/Laut terputus.
**Perbaikan.** Turunkan nilai dasar ke ± 2 × biaya resep (Dupa 40, Mutiara 60, Selendang 300) — koordinasikan dengan
audit ekonomi.

### D15 — RENDAH: Istilah yang bentrok

- "Titah": Segel Titah Langit (`pusakalangit`, Panduan c11.titah), grup cheat `titah`, dan Titah Utusan Kota Abadi
  (`dunia_kota.id_id.json:530-533`). Saran: Kota Abadi → "Perintah Utusan" / "Tugas Utusan".
- "Abadi Sejati" (`dunia_kota.id_id.json:585`, `:609`) padahal nama ranah `kultivator.realm.true_immortal` = "Dewa Abadi".
  Saran: ganti ke "Dewa Abadi" (atau pakai `%s` dari `Realm.displayName()`).
- "Saingan" dipakai untuk kelompok saingan Dunia (`c14.saingan`) dan hubungan murid (`c15.saingan` "Lahirnya persaingan",
  SEDANG DIBANGUN). Saran: bab 15 memakai "Rival Sesama Murid" atau "Persaingan Murid" di judul.
- `entity.kultivator.kapal_bajak` "Perahu Bajak Langit" (`dunia_kapal.id_id.json:111`) vs rencana 37 "Bajak Awan" untuk
  entitas yang sama (`KapalBajakEntity`). Saran: satu nama ("Bajak Langit") di 37.

### D16 — RENDAH: Dua sistem "panjat lantai" tanpa rujukan silang

**Bukti.** Makam Surgawi (`alam/RealmSpecs.java:33`, 9 lapis, Kenaikan Abadi, slot berinstans) dan Menara Seratus Lantai
(`menara/LobbyRun.java:151-170`, 100 lantai, slot berinstans, semua ranah).
**Kenapa.** Pemain Kenaikan Abadi melihat dua menara-ujian dengan aturan berbeda (DeathGuard, Luka Menara, cooldown) tanpa
penjelasan peran masing-masing.
**Perbaikan.** Satu kalimat di `help.menara_seratus` dan bantuan Makam Surgawi yang membedakan perannya (Makam = bekal
kenaikan; Menara = tangga ujian sepanjang hidup), dan buat batu peringkat menara menyebut lapis Makam yang sudah ditembus.

### D17 — SEDANG (RENCANA, rancangan 37 Sekte Langit): catatan integrasi dengan Dunia

**Bukti dan catatan** (`docs/rancangan/37-sekte-langit.md`):
- `:1128-1135` sekte langit NPC (maks 2) = jenis sekte NPC keempat; harus memakai sumber yang sama dengan D6 (Diplomasi) dan
  menerbitkan situs `DuniaSites` + marker peta, kalau tidak peta kembali tidak cocok.
- `:1085`, `:1138` "Bajak Awan" memakai `KapalBajakEntity` yang di Dunia bernama "Bajak Langit" (D15).
- `:1196-1240` config `langit` di `config/kultivator-sekte.json` dan grup chip CheatHelp `langit`, sementara Dunia sudah
  punya bagian config `langit` di `kultivator-dunia.json` dan `uji langit` (peristiwa langit). Saran: bagian
  `sekteLangit` dan grup chip `sekte_langit`.
- Faktor Qi situs `sekte_langit` (≤ 1.25) menumpuk dengan Kabut Qi, Hujan Meteor, Bulan Kembar lewat
  `SpiritSites.addSiteProvider`; tetap harus di bawah `CAP 4.0` / `Meditation.MULTIPLIER_CAP 3.5` (pastikan provider baru
  ikut dikali sebelum cap, bukan sesudah).
- Pulau sekte langit harus masuk `Halls.inTerritory` agar harta lahir (`TreasureBirths.java:244-254`), Penyergapan
  (`Ambushes.refusal`) dan sergapan Saingan (D3) menghormatinya; dan `Natives`/naga liar tidak boleh menganggap atap pulau
  sebagai tanah (pola D5).

## Risiko save/kompatibilitas

- D7 menulis situs salah ke `kultivator:dunia_sites` yang menetap; perlu pembersih sekali jalan.
- D2: mengubah tipe placement hanya berlaku di chunk baru (aman); struktur lama di dekat spawn tetap ada.
- D6: bila `rivalGroups` atau `jumlahSekte` diubah setelah dunia dibuat, `rivalLink` per indeks Diplomasi menunjuk id
  kelompok yang tidak ada (SainganData disemai sekali, Diplomasi sekali, terpisah). Simpan pasangan id, bukan indeks, dan
  abaikan tautan yang hilang dengan anggun.

## File yang disentuh audit ini

Hanya `docs/progres/audit-dunia.md` (checkpoint) dan laporan ini.
