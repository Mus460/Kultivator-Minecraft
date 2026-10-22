# Audit Kesinambungan: SEKTE & MURID (audit:sekte)

Tanggal: 2026-10-22. Audit HANYA-BACA (tidak ada file proyek yang diubah, Gradle tidak dijalankan).
Cakupan: aula sekte, jenjang, kontribusi, tugas, Lumbung, misi/ekspedisi, acara (turnamen, perang, menara), templat dan
komponen bangunan sekte, Formasi Sekte, sekte pemain (`/sekte`), serbuan, desa bawahan, murid (rekrut, bimbingan,
terobosan, teknik, jalan, bintang, bencana, pewaris, ikatan, Hu Fa, Jurus Pemusnah, suara), DESIGN 36 "Sekte yang
Hidup" dan rancangan 37 "Sekte Langit".

**Status sistem yang diaudit**
- DESIGN 36 (Sekte yang Hidup) **sudah selesai dan di-commit** (`40d06e1`, `docs/progres/sekte-finish.md` = DONE)
  ketika audit ini berjalan, jadi kodenya diperlakukan sebagai kode jadi, bukan setengah jadi.
- Satu bagian 36 masih **SEDANG DIBANGUN** di working tree yang belum di-commit: suara Jepang baru 36.6
  (`sounds.json`, `tools/gen_voices.py`, `lang-src/voice.*` berubah).
- DESIGN 37 (Sekte Langit) baru berupa rancangan (`docs/rancangan/37-sekte-langit.md`) dan **belum ada kodenya**. Untuk
  37 yang ditulis hanya catatan integrasi.

## Ringkasan

| Tingkat | Jumlah |
|---|---|
| KRITIS | 1 |
| TINGGI | 6 |
| SEDANG | 12 |
| RENDAH | 7 |
| **Total** | **26** |

Tiga temuan paling penting:
1. **K1**: sakelar modul di `config/kultivator-sekte.json` ikut mematikan pendaftaran item dan entitas (`inti_roh_gunung`,
   `dupa`, `gulungan_laporan`, entitas Penonton) serta komponen templat `sx_*`. Akibatnya barang di dunia bisa hilang dan
   server/klien yang config-nya berbeda tidak bisa tersambung.
2. **T1 + T2**: diplomasi sekte NPC sudah aktif sejak aula level 1 (tuntutan upeti, perang, "pertempuran data" yang
   memotong PK). Tantangan turnamen juga memunculkan **dua prompt** sekaligus, dan menerima lewat chat tetap dihukum −5
   standing.
3. **T3 + T4**: bab Kitab Panduan 15/16 (isi awal sampai menengah) baru menjadi "bab saat ini" setelah bab 14
   (Ujung Dunia). Langkah c15.penerus butuh pewaris ★9, yang baru bisa setelah Dewa Abadi Puncak, sehingga bab 16
   praktis tidak pernah tersorot.

## Tabel temuan

| ID | Tingkat | Jenis | Judul | Bukti utama |
|---|---|---|---|---|
| K1 | KRITIS | simpanan/kompat | Sakelar modul 36 menghapus item, entitas dan komponen dari registry | `sekte/SekteContent.java:56-79`; `kebun/Kebun.java:90-96`; `paviliun/Paviliun.java:66-72`; `turnamen/Turnamen.java:115-122`; bandingkan `misi/Misi.java:61` |
| T1 | TINGGI | duplikat/alur | Tantangan sekte NPC = dua prompt, hukuman ganda; KETUA juga memicu turnamen | `diplomasi/Simulasi.java:406-413`; `turnamen/Turnamen.java:126-130`; `turnamen/AntarSekte.java:44-61`; `diplomasi/Utusan.java:191-199,255-275` |
| T2 | TINGGI | lonjakan progresi | Diplomasi/perang sekte NPC aktif sejak aula lv1 (upeti, perang, kehilangan PK) | `diplomasi/Simulasi.java:210-253,307-328,344-358`; `diplomasi/Perang.java:241-287,178-192`; `musuh/SectRaids.java:144` |
| T3 | TINGGI | lonjakan progresi | Penyerahan Takhta butuh pewaris ★9 (setelah Dewa Abadi Puncak), padahal syarat yang ditulis "Nascent Soul" | `tetua/Generasi.java:59-62,170-190`; `disciple/path/Stars.java:37-41` |
| T4 | TINGGI | alur panduan | Bab 15/16 ada setelah bab end-game; c15.penerus mengunci bab 16 | `guide/GuideChapters.java:225-271`; `guide/GuideProgress.java:126-133`; `client/guide/GuideScreen.java:113,311` |
| T5 | TINGGI | duplikat | Lima jalur "misi/utusan" dengan aturan berbeda (Ekspedisi, Misi Cerita, Utus Perekrut, Utusan Diplomasi, Misi Utusan) | `misi/Misi.java:31-32`; `misi/TemplatMisi.java:17-42`; `sect/event/Envoys.java:6-16`; `diplomasi/Aksi.java:60,134`; `misi/Kepulangan.java:282-298` |
| T6 | TINGGI | kontradiksi | Sekte pemain `/sekte` vs Aula Sekte: dua cara "mendirikan sekte", dua nama, dua "Ketua Sekte", pemain disebut "murid" | `lang-src/screen.id_id.json:170`; `lang-src/sects.id_id.json` (`help.create/invite`, `create.done`); `sect/SectCommands.java:97-121`; `client/screen/HubCardsPage.java:183-195` |
| S1 | SEDANG | UI/orphan | Hub SEKTE berisi 16-17 kartu dalam grid tanpa gulir, meluap di 320×240 (37 menambah kartu ke-18) | `client/screen/HubCardsPage.java:183-195,213-216,234-256` |
| S2 | SEDANG | bantuan basi | Ensiklopedia tidak punya topik 36; help.turnamen/misi/calon/serbuan sudah tidak sesuai | `client/screen/HelpPage.java:39-53`; `lang-src/screen.id_id.json` (help.turnamen, help.misi, help.calon, help.serbuan) |
| S3 | SEDANG | duplikat | Turnamen Elit memakai 8 "sekte saingan" statis, bukan sekte NPC D atau kelompok saingan Dunia | `sect/event/Tournament.java:507-518`; `sect/event/Rivals.java:27-46`; `diplomasi/SekteNpc.java:56` |
| S4 | SEDANG | simpanan | Luapan Lumbung hilang bila pemilik offline (Tanah Terlarang, Bahtera Purba) | `sect/event/ForbiddenGround.java:160-163`; `sect/event/PrimordialArk.java:131-136`; bandingkan `musuh/SectRaids.java:980-990`, `sekte/misi/Kepulangan.java:239` |
| S5 | SEDANG | terputus | Murid Membangkang hanya ditolak oleh sistem baru; ekspedisi lama, utusan perekrut dan turnamen tetap menerimanya | `kebun/Kebun.java:374`; `misi/TimMisi.java:204`; `paviliun/Tempat.java:180`; `tetua/Tetua.java:183`; tidak ada di `sect/event/Expeditions.java`, `Envoys.java`, `Tournament.java:486-492` |
| S6 | SEDANG | umpan balik | Serangan sekte NPC mengumumkan "serangan siap" walau `SectRaids.callRaid` menolak | `diplomasi/Perang.java:240-255`; `musuh/SectRaids.java:217-225` |
| S7 | SEDANG | simpanan/kompat | Aktivitas 13-20 dipulihkan dari save tanpa memeriksa modul, sehingga murid tertahan (Merajuk/Tambang) | `disciple/DiscipleEntity.java:1499-1505,462-465`; `kepribadian/Kepribadian.java:195-198,355-385` |
| S8 | SEDANG | progresi/alur | Templat 1 tidak mendapat satu pun komponen 36 dan langkah panduan terkait tidak menjelaskannya | `kebun/KebunKomponen.java:91-93`; `paviliun/PaviliunKomponen.java:97-100`; `tangga/BatuHatiKomponen.java:87-91`; `turnamen/TribunKomponen.java:170-172`; `sect/build/flow/Founding.java:154` |
| S9 | SEDANG | SEDANG DIBANGUN | Suara baru 36.6 sedang dibuat, tetapi kode 36 masih memanggil kunci lama, jadi baris baru akan menganggur | `docs/progres/sekte-voices.md`; working tree `sounds.json`; panggilan `VoiceDisciples.line` di `sekte/**` (hanya greet/thanks/loyal/…) |
| S10 | SEDANG | terminologi | "Tetua Agung" dipakai untuk jenjang SectRanks dan juga jabatan E; Wakil berjenjang Tetua "diangkat menjadi Tetua Agung" | `tetua/JabatanTetua.java:74-80`; `api/Jabatan.java:16`; `lang-src/sekte_tetua.id_id.json` (`memory.appointed.agung`) |
| S11 | SEDANG | duplikat | Dua kebun: pos tugas kebun lama (5 blok dari prasasti) dan petak herba W, dengan aturan dan nama yang berbeda | `sect/event/Duties.java:49-56`; `kebun/Kebun.java`, `kebun/Pertumbuhan.java:271` |
| S12 | SEDANG | alur | Dua jalur penerimaan tantangan T (chat T vs Utusan D) dan `Musim.pending` tidak dibersihkan saat diterima lewat D | `turnamen/AntarSekte.java:44-75`; `diplomasi/Utusan.java:191-199` |
| R1 | RENDAH | desain≠kode | Persekutuan: "diskon 10% Contribution" (DESIGN 36.2.9) tidak ada di kode | `docs/DESIGN.md:23026`; tidak ada di `sect/hall/Contribution.java`, `sekte/diplomasi/*` |
| R2 | RENDAH | UI | Empat kartu SEKTE memakai ikon `Glyphs.SEAL` yang sama; kartu `sekte_*` tidak terkunci walau aula belum ada | `client/sekte/diplomasi/DiplomasiClient.java:33`; `seragam/SeragamClient.java:63`; `turnamen/TurnamenClient.java:43`; `HubCardsPage.java:194` |
| R3 | RENDAH | terminologi | help.serbuan masih "sebuah sekte iblis" padahal penyerbu bisa sekte NPC bernama (aliran apa saja yang berperang) | `lang-src/screen.id_id.json` (help.serbuan); `diplomasi/Perang.java:241-249,302-320` |
| R4 | RENDAH | terminologi | Tiga akar perintah untuk "sekte": `/sekte`, `/kultivasi aula`, `/kultivasi perguruan` ("perguruan" tidak dipakai di UI) | `sect/SectCommands.java:97`; `sect/build/flow/BuildCommands.java:178`; `sekte/SekteCommands.java:29-37` |
| R5 | RENDAH | duplikat | Misi "Lelang Kota" membayar PK secara abstrak, terpisah dari Balai Lelang kota (batu roh) dan Lelang Alam Abadi | `misi/TemplatMisi.java:36`; `misi/TimMisi.java:54,128,286`; `dunia/kota/BalaiLelang.java`; `abadi/Auction.java` |
| R6 | RENDAH | panduan | Panduan bab 16 tidak punya langkah perang (DESIGN: c16.perang); `diplomasi_menang` tidak dipakai panduan | `guide/GuideChapters.java:261-271`; `docs/DESIGN.md` 36.2.9 "Panduan: c16.rival, c16.sekutu, c16.perang" |
| R7 | RENDAH | API untuk 37 | `DiplomasiService` tidak punya metode "temui/kontak", padahal 37.7.6 bergantung padanya | `sekte/api/DiplomasiService.java:21-52`; `docs/rancangan/37-sekte-langit.md:1131-1134` |

---

## Detail temuan

### K1 (KRITIS): Sakelar modul 36 menghapus item, entitas, dan komponen dari registry

**Bukti.** `SekteContent.init` langsung `return` bila `umum.aktif == false` (`SekteContent.java:56-58`), dan setiap
modul melompati seluruh `init()` bila sakelarnya mati: `Kebun.init` (`Kebun.java:90-96`) baru memanggil
`KebunItems.init()` (item `inti_roh_gunung`, `KebunItems.java:41`) dan `KebunKomponen.register()` sesudah pemeriksaan
itu. Pola yang sama ada di `Paviliun.init` (`Paviliun.java:66-72`: `Dupa.init()`, `PaviliunKomponen.register()`) dan
`Turnamen.init` (`Turnamen.java:115-122`: `TurnamenEntities.init()` yang mendaftarkan tipe entitas Penonton,
`TribunKomponen.register()`). Hal serupa berlaku untuk `Tangga` (`BatuHatiKomponen`) dan `Seragam`
(`PanjiKomponen`). Hanya modul X yang sengaja mendaftarkan item dan payload sebelum pemeriksaan
(`Misi.java:61`, komentarnya: "registries and channels must match both sides"), tetapi bila `umum.aktif = false`,
`Misi.init()` pun tidak pernah dipanggil.

**Kenapa memutus kesinambungan.** DESIGN 36.0.8 menjanjikan bahwa setiap modul "bisa dimatikan per modul". Kenyataannya:
(a) Dupa, Inti Roh Gunung, dan Gulungan Laporan yang sudah ada di inventaris, peti, atau Lumbung menjadi id yang tidak
dikenal saat dunia dimuat, lalu hilang. (b) Di server dedicated, bila config klien berbeda dari server, sinkronisasi
registry Fabric menolak sambungan karena entri item atau entitas hilang. (c) Spesifikasi templat kehilangan komponen
`sx_*` yang mungkin sudah dibangun, padahal mesin bangun membandingkan situs dengan rencana yang berubah.

**Perbaikan.** Untuk setiap modul (`Kebun`, `Paviliun`, `Turnamen`, `Tangga`, `Seragam`, `Misi`) pisahkan
`registerContent()` (item, entitas, payload, `*Komponen.register()`) yang **selalu** dipanggil dari
`SekteContent.init` sebelum pemeriksaan `umum.aktif`, dari `init()` (listener, tick, perintah) yang tetap patuh
sakelar. Komponen templat tetap terdaftar. Bila modul mati, cukup jangan menjalankan logika runtime-nya: komponen
tetap bisa dibangun sebagai hiasan. Tambahkan ke `SekteContent` komentar aturan "registry tidak pernah mengikuti
sakelar". Aturan yang sama wajib untuk 37 (`langit.aktif`, 37.8.7).

### T1 (TINGGI): Tantangan sekte NPC memunculkan dua prompt dan hukuman ganda; KETUA ikut memicu turnamen

**Bukti.** Peristiwa `TANTANG` membuat tawaran Utusan **dan** menembakkan `AksiSekte.TANTANGAN`
(`Simulasi.java:406-409`). Peristiwa `KETUA` (Ketua Sekte NPC menantang ke aula) juga menembakkan `TANTANGAN`
(`Simulasi.java:410-413`). Listener T mengubah setiap `TANTANGAN` menjadi tantangan turnamen antar-sekte dengan tombol
chat Terima/Tolak (`Turnamen.java:126-130`, `AntarSekte.java:44-61`). Jika pemain menerima lewat chat T
(`/kultivasi perguruan turnamen terima`), tawaran Utusan D tetap terbuka. Saat kedaluwarsa, `Utusan.expire` menghitungnya
sebagai **ditolak** (`Utusan.java:255-275`, `kind == TANTANG` lalu `refuse` −5 standing). Jika pemain menolak lewat T
lalu juga mengabaikan Utusan, ia kena −3 (`AntarSekte.REFUSE_COST`) **dan** −5.

**Kenapa memutus kesinambungan.** Satu peristiwa cerita muncul sebagai dua permintaan dengan teks berbeda. Pemain
yang patuh malah dihukum, dan kunjungan Ketua Sekte (pertarungan bos) ikut membawa undangan turnamen yang tidak
nyambung.

**Perbaikan.** Pilih satu pintu. Usulan: D tetap pemilik tawaran (`Utusan.offer` TANTANG). `Simulasi` tidak lagi
menembakkan `TANTANGAN` untuk `TANTANG`/`KETUA`; cukup `Diplomasi.fire(..., AksiSekte.UTUSAN)` atau aksi baru
`KETUA_DATANG`. Lalu `AntarSekte.challenge` hanya dipanggil dari tes. Alternatifnya, bila T tetap memegang prompt:
`Utusan.offer(TANTANG)` tidak dipakai, dan `AntarSekte.accept/refuse` memanggil `DiplomasiService.adjust` dengan alasan
yang sama (+0/−5) serta menghapus tawaran D lewat API baru `DiplomasiService.answerChallenge(owner, sect, accepted)`.
Bagaimanapun, `Musim.setPending(owner, null, 0)` harus dipanggil di `Turnamen.hostGuests` (lihat S12).

### T2 (TINGGI): Diplomasi dan perang sekte NPC menekan aula sejak level 1

**Bukti.** `Simulasi.dailyNext` hanya mensyaratkan `hall.active()` (`Simulasi.java:210-236`). `daily` menjalankan
0-4 peristiwa per hari (`peristiwaPerHari` 2, `DiplomasiConfig.java:15,35`). `pickSect` memilih dari **semua** sekte
dunia, termasuk yang belum pernah ditemui dan bertingkat tinggi (`Simulasi.java:307-328`). Untuk aula baru,
`strong = sect.strength() > kekuatanAula` hampir selalu benar, sehingga tabel BERMUSUHAN/DINGIN membuka `TUNTUT`
(`Simulasi.java:349,357`) dengan harga `tier × 25` PK (`Utusan.java:203`). Menolak atau membiarkannya kedaluwarsa
memberi −15 dan bisa langsung `Perang.nyatakan` (`Utusan.java:231-236`). Pada aula < lv3, perang dan serangan menjadi
`pertempuranData` yang memotong kas sampai 150 PK per kekalahan (`Perang.java:241-287`). Tiga kekalahan berarti
`kalah()`: standing −40, upeti paksa, dan potongan sampai 500 PK (`Perang.java:178-192`). Serbuan iblis lama sengaja
baru dimulai di aula lv3 (`SectRaids.MIN_HALL_LEVEL`, `SectRaids.java:144`).

**Kenapa memutus kesinambungan.** Pemain yang baru menaruh prasasti (setara Inti Emas di panduan bab 5) langsung dikejar
sekte tingkat 7-9 yang tidak dikenalnya, sebelum punya murid kuat, PK, atau penjelasan apa pun. Ini dinding di awal,
dan bertentangan dengan pola lama yang menunda ancaman sampai aula lv3.

**Perbaikan.** Di `Simulasi.daily`/`event`: (1) jangan jalankan peristiwa negatif (`TUNTUT`, `SERANGAN`, `PERANG`,
`SERGAP`, `MEREKRUT`) sebelum `Halls.level(owner) >= SectRaids.MIN_HALL_LEVEL`. Sebelum itu hanya `UTUSAN`, `HADIAH`,
dan `TANTANG` dengan bobot kecil. (2) `pickSect` hanya memilih sekte yang `r.ditemui` atau yang `tier <= Halls.level + 2`
(selaras dengan aturan pemain "tuntut bila aula ≥ tingkat mereka + 2"). (3) Jumlah peristiwa harian dimulai 0-1 dan
naik ke `peristiwaPerHari` dari aula lv3. Angka lama di 36 tidak berubah; ini hanya gerbang baru, dan bisa ditambah
field `DiplomasiConfig.mulaiAulaLevel` (bawaan 3).

### T3 (TINGGI): Penyerahan Takhta butuh ★9 pewaris, sedangkan syarat yang ditulis "Nascent Soul"

**Bukti.** `Generasi.requirements` meminta `HEIR_REALM = NASCENT_SOUL` **dan** `HEIR_STARS = 9`
(`Generasi.java:59-62,183-184`). Bintang murid baru mulai di **Puncak Dewa Abadi** (`Stars.java:37-41`). Syarat ranah
NS praktis tidak berarti apa-apa, karena ★9 mengharuskan Dewa Abadi Puncak. `sekte-finish.md` mencatatnya sebagai
"keputusan pemimpin dibiarkan" ("9 heir stars ... very late game").

**Kenapa memutus kesinambungan.** Fitur "Ketua Generasi Kedua" diperkenalkan di bab 15 bersama fitur menengah, ditulis
"pewaris yang cukup kuat", dan layar Balai Tetua menampilkan "Nascent Soul" sebagai syarat. Pemain mengira fitur ini
dekat, padahal jaraknya beberapa ranah. Ini juga mengunci bab panduan (T4).

**Perbaikan.** Pilih salah satu. (a) Turunkan `HEIR_STARS` menjadi 0 dan biarkan syarat ranah, misalnya `DEITY_TRANSFORMATION`
atau `NASCENT_SOUL` + `Heir` lengkap, ditambah aula lv5 dan usia sekte (yang sudah ada). Sembilan Segel tetap bonus
end-game. (b) Bila ★9 tetap dipakai, ganti `HEIR_REALM` ke `TRUE_IMMORTAL`, ubah teks `kultivator.guide.c15.penerus.body`
dan `sekte.tetua.req.*` agar jujur ("pewaris ★9, setelah Dewa Abadi"), lalu pindahkan langkah `c15.penerus` ke bab
akhir (lihat T4).

### T4 (TINGGI): Bab panduan 15/16 ada setelah bab end-game, dan c15.penerus mengunci bab 16

**Bukti.** "Bab saat ini" adalah bab pertama yang masih punya langkah belum selesai (`GuideProgress.currentChapter`,
`GuideProgress.java:126-133`). Layar panduan membuka dan menyorot bab itu (`GuideScreen.java:113,311`). Bab 13-14
(Dunia/Ujung Dunia: Yama, Raja Naga, Menara lantai 100, `GuideChapters.java:225-249`) dan bab 11 (★9 pemain) ada lebih
dulu. Bab 15 berisi langkah awal (watak, sahabat, tangga, kitab, altar, tetua) tetapi ditutup `c15.penerus`
(`GuideChapters.java:260`) yang butuh T3. Bab 16 (lambang, bendera, turnamen, misi, kebun, sekutu) baru tersorot
setelah itu.

**Kenapa memutus kesinambungan.** Pemain menengah yang sudah punya aula tidak pernah diarahkan ke fitur 36 oleh panduan.
Fitur besar ini hanya bisa ditemukan lewat kartu hub.

**Perbaikan.** Urutan langkah disimpan per id (`c15.*`), jadi memindah langkah aman. (1) Pindahkan `c15.penerus` ke
akhir bab 16, atau ke bab 11 bila ★9 dipertahankan. (2) Agar 15/16 tersorot di waktu yang tepat, ubah `currentChapter`
supaya mengabaikan bab yang langkah otomatisnya terkunci ranah (`rank:`, `stars:`, `at:`), atau tambahkan
`Chapter.unlockAt(Realm)`, misalnya bab 15/16 terbuka begitu `hall` ada (langkah c5.place). Selama bab 1-14 belum
selesai, bab "berikutnya yang terbuka" dengan langkah termurah yang ditampilkan. (3) Di teks bab 5/6 tambahkan satu
langkah manual "Murid yang Hidup: lihat bab 15".

### T5 (TINGGI): Lima jalur misi/utusan dengan aturan berbeda

**Bukti.**
- `Expeditions` (papan misi harian lama, tim 1-3, PK, bagian bar, ketenaran, rampasan). Teksnya ada di help.misi.
- Misi Cerita X: "beside the existing expedition board" (`Misi.java:31-32`), tim 1-4, 12 templat yang memakai tabel
  rampasan `Expeditions.LOOT_*` yang sama (`TemplatMisi.java:17-42`).
- `Envoys` "Utus Perekrut" (1 murid, 1 hari, tiap 5 hari, 1-3 calon; `Envoys.java:6-16`), sedangkan Kepulangan X juga
  membawa pemuda (`misi_bawa`).
- Aksi Diplomasi "Kirim Utusan" (1 hari, Tetua Urusan Luar atau Inti Emas+, standing +5; `Aksi.java:60,134`, `Utusan.canSend`).
- Templat misi `UTUSAN` "Utusan ke Sekte" (1-9 tingkat, 2 hari, standing `grade×4−3`; `Kepulangan.java:282-298`).

Tiga di antaranya bernama "Utusan". Murid bisa dikirim ke Misi Cerita dan ekspedisi lama dengan aturan Membangkang
yang berbeda (S5).

**Kenapa memutus kesinambungan.** Pemain melihat dua papan misi (tablet Misi di aula dan kartu hub "Misi Cerita")
yang hasilnya hampir sama, dan tiga cara mengirim utusan ke sekte yang sama dengan angka berlainan.

**Perbaikan (rencana penggabungan).**
1. Satu papan: tablet Misi di `HallScreen` menampilkan dua tab, "Harian" (Expeditions, angka tetap) dan "Cerita" (X).
   Kartu hub `sekte_misi_cerita` membuka tablet yang sama di tab Cerita. Teks help.misi diperbarui.
2. Satu "Utusan Diplomasi": aksi D "Kirim Utusan" membuat misi X templat `UTUSAN` ke sekte itu (memakai
   `MisiService` dan laporan tertulis), dengan cooldown `UTUSAN_JEDA` milik D. Rumus standing satu saja (pakai D:
   +5/+3, dikali mutu laporan). `Utusan.send` tinggal pembungkus.
3. "Utus Perekrut" diberi nama berbeda yang konsisten, "Utus Pencari Bakat", dan muncul di tab Harian. Hasil pemuda
   dari X tetap lewat `Halls.addCandidates` seperti Envoys.

### T6 (TINGGI): Sekte pemain `/sekte` bertentangan dengan Aula Sekte

**Bukti.** help.sects: "Dirikan sekte dengan /sekte create lalu undang murid." (`screen.id_id.json:170`).
`sects.id_id.json`: "/sect create <nama> – dirikan sektemu sendiri dan jadilah Ketua Sekte", "/sect invite <pemain> –
undang seorang murid". Panduan bab 5 dan kartu hub "Dirikan" mengajarkan cara lain, yaitu Prasasti Aula
(`HubCardsPage.java:183-187`, `GuideChapters` c5.place `/kultivasi aula nama`). Kedua sistem punya nama sendiri dan
tidak saling tahu. Satu-satunya kaitan hanyalah kehadiran anggota di `BuildEngine.java:635`. Tidak ada kartu hub untuk
sekte pemain, dan sekte NPC (D) juga dipimpin "Ketua Sekte".

**Kenapa memutus kesinambungan.** Kata "sekte", "murid", dan "Ketua Sekte" masing-masing berarti dua hal. Pemain yang
membaca ensiklopedia bisa mendirikan sekte yang salah, dan menyebut pemain lain "murid" bertabrakan dengan murid NPC.

**Perbaikan.** (1) Ganti istilah sekte pemain menjadi **"Persaudaraan"** atau **"Saudara Sekte"** di `sects.*.json` dan
help.sects. Anggotanya "saudara", bukan "murid". (2) Tulis ulang help.sects: "Sekte didirikan dengan Prasasti Aula
(lihat Aula). `/sekte` mengumpulkan **pemain lain** sebagai saudara sekte agar boleh membangun dan bermeditasi
bersama." (3) Opsional: `/sekte create` memakai nama aula bila pemain punya aula. Bila pemain mendirikan aula sementara
sudah memimpin `/sekte`, tawarkan menyamakan nama. (4) Tambahkan kartu hub SEKTE "Saudara Sekte" yang membuka
`/sekte info`.

### S1 (SEDANG): Hub SEKTE meluap

**Bukti.** Kartu bawaan berjumlah 8 (+1 "dirikan" tanpa aula) (`HubCardsPage.java:183-195`), ditambah 8 kartu modul 36
(`sekte_diplomasi_rival`, `sekte_hubungan_jaring`, `sekte_kebun_tambang`, `sekte_misi_cerita`, `sekte_paviliun`,
`sekte_seragam_lambang`, `sekte_tetua_balai`, `sekte_turnamen_papan`), sehingga totalnya 16-17. `layoutCards` mengaku
"no scrolling at 320 × 240" tetapi memaksa tinggi minimum 20 px (`HubCardsPage.java:234-256`). Pada lebar < 330
dipakai 2 kolom, jadi 9 baris × 20 + 8 × 5 = 220 px ditambah HEAD 14 dan footer 12. Itu melebihi kotak konten pada
skala GUI kecil. 37.7.9 menambah kartu ke-18 (`sekte_langit`).

**Perbaikan.** Kelompokkan kartu modul 36 di dalam sub-halaman. Contohnya kartu "Kehidupan Sekte" yang membuka grid kedua
(Watak dan Hubungan, Tetua, Paviliun dan Leluhur, Misi Cerita, Turnamen, Kebun dan Tambang, Lambang, Diplomasi). Cara
lain: `layoutCards` diberi gulir bila `room/rows < 20`. Beri juga `lock` = `hallLock` untuk kartu yang butuh aula (R2).

### S2 (SEDANG): Ensiklopedia tidak mengenal 36, dan topik lama sudah basi

**Bukti.** Daftar `HelpPage.TOPICS` (`HelpPage.java:39-53`) tidak memuat topik watak/kesetiaan, hubungan, tetua,
paviliun/leluhur, diplomasi, kebun/tambang, atau lambang. Isi topik yang sudah usang:
- help.turnamen: "gelanggang prasasti", "Laga berakhir ... setelah 60 detik", dan "jagoan sekte saingan". Padahal T
  memindah laga ke arena dengan Duel Pedang 90 dtk, tribun, dan tamu sekte NPC.
- help.misi: hanya papan lama, tidak ada Misi Cerita.
- help.calon: "tiga tingkat pendakian" tanpa Tangga Seribu atau Batu Hati.
- help.serbuan: lihat R3.

**Perbaikan.** Tambahkan topik `sekte_hidup` (ringkasan 36 dengan tautan ke kartu hub) dan topik per modul di
`lang-src/sekte_*.json` (pola 34.6: setiap modul menulis topiknya, lalu `HelpPage.TOPICS` ditambah satu baris
"DESIGN 36"). Perbarui teks help.turnamen/misi/calon di `lang-src/screen.*.json` (en dan id, kunci sama).

### S3 (SEDANG): Turnamen Elit memakai sekte saingan statis

**Bukti.** Jagoan Elit dibuat dengan `Rivals.champion(..., random.nextInt(Rivals.SECT_COUNT))`, yaitu 8 nama
`sectevent.rival_sect.0-7` ("Sekte Lembah Awan Biru" dan seterusnya) (`Tournament.java:507-510`,
`Rivals.java:27-46`). Turnamen antar-sekte justru memakai sekte NPC D (`Tournament.java:511-518`). Sekte NPC D sudah
diselaraskan dengan kelompok saingan Dunia (`SekteNpc.rivalLink`, `SekteNpc.java:56`). Jadi ada tiga daftar "sekte
saingan".

**Perbaikan.** Bila `SekteApi.diplomasi().sects()` tidak kosong, `Tournament` memilih jagoan Elit dari sekte NPC yang
sudah ditemui (nama dan lambang D), dan hasilnya memakai `adjust(±STANDING/2)`. Nama statis hanya menjadi cadangan saat
D mati.

### S4 (SEDANG): Luapan Lumbung hilang saat pemilik offline

**Bukti.** `ForbiddenGround.java:160-163` dan `PrimordialArk.java:131-136`: `rest` hanya diberikan bila
`player != null`. Kalau tidak, barang dibuang. Pola yang benar sudah ada di `SectRaids.store`
(`SectRaids.java:980-990`: beri ke pemain atau jatuhkan di prasasti) dan `LumbungOverflow.store` (dipakai `Duties`,
`Kepulangan.java:239`, `Pertumbuhan.java:210`).

**Perbaikan.** Ganti kedua tempat itu dengan `LumbungOverflow.store(owner, stack)` (Peti Galian). Periksa juga
`AwayTeams.java:203-210` (Soul Tablet saat offline).

### S5 (SEDANG): Membangkang hanya dihormati sistem baru

**Bukti.** `HatiService.defiant` dicek di `Kebun.java:374`, `TimMisi.java:204`, `Tempat.java:180`, `Tetua.java:183`,
dan `GiliranSosial.java:230`. Pemeriksaan itu tidak ada di `Expeditions`, `Envoys`, `ForbiddenGround`, `VoidRealm`,
atau seleksi peserta `Tournament.start` (`Tournament.java:486-492`, hanya `excluded`).

**Kenapa memutus kesinambungan.** Murid yang "menolak semua perintah" tetap bisa dikirim ekspedisi lama atau masuk
turnamen, sementara misi baru menolaknya.

**Perbaikan.** Satu helper `DiscipleHooks.refuses(entity)` (atau `SekteApi.hati().defiant`) dipanggil di pemilih
tim `Expeditions`/`Envoys`/`ForbiddenGround`/`VoidRealm`/`PrimordialArk` dan di `Tournament.candidates`, dengan pesan
`kultivator.sekte.kepribadian.tolak.tugas` yang sudah ada.

### S6 (SEDANG): Pengumuman serangan palsu

**Bukti.** `Perang.serangan` memanggil `SectRaids.callRaid(...)` tanpa melihat hasilnya, lalu selalu menulis kronik,
log, dan chat "serangan siap" serta menembakkan `AksiSekte.SERANGAN` (`Perang.java:240-255`). `callRaid` menolak bila
serbuan dimatikan di config, jadwal lebih dekat, atau ada serbuan pending/live (`SectRaids.java:217-225`).

**Perbaikan.** `if (!SectRaids.callRaid(...)) { pertempuranData(...); return; }`, atau minimal jangan mengumumkan.
Hal yang sama berlaku di `Perang.java:231-233`.

### S7 (SEDANG): Aktivitas 13-20 tertahan setelah modul mati atau dimuat ulang

**Bukti.** `readAdditionalSaveData` memulihkan aktivitas 13-20 (`sekteHolding`) dari save tanpa memeriksa apakah
modulnya aktif (`DiscipleEntity.java:1499-1505,462-465`). `Kepribadian.tick20` langsung `return` bila
`!on()` (`Kepribadian.java:195-198`), padahal hanya tick itu yang membebaskan `ACT_SULK` dan mengembalikan mode lama
(`Kepribadian.java:381-395`). Murid yang sedang merajuk saat `kepribadian.aktif` dimatikan tetap berstatus STAY dan
merajuk selamanya. Hal serupa terjadi pada `ACT_MINE`, `ACT_TEND`, `ACT_STUDY`, dan `ACT_OFFICE` bila modulnya dimatikan
(lihat K1).

**Perbaikan.** Saat memuat, pulihkan aktivitas 13-20 hanya bila `SekteContent.on(modulPemilik)`. Kalau tidak,
`ACTIVITY_NONE`. Di `Kepribadian.tick20`, sebelum `if (!on())`, bebaskan `ACT_SULK` dan pulihkan `modeLama`.

### S8 (SEDANG): Templat 1 tanpa komponen 36

**Bukti.** Kebun dan Tambang hanya untuk templat ≥ 2 (`KebunKomponen.java:91-93`). Altar dan Makam untuk 2-9
(`PaviliunKomponen.java:97-100`), Batu Hati untuk 2-9 (`BatuHatiKomponen.java:87-91`), tribun untuk 6-9
(`TribunKomponen.java:170-172`). Templat 1 dipilih pemain fana (`Founding.java:154`, gerbang ranah `template − 1`).
Hanya tambang yang punya pesan "templat 4-9" (`sekte_kebun.id_id.json`, `ui.belum_tambang`).

**Perbaikan.** Di setiap layar 36 (Paviliun, Kebun, Tangga, Turnamen), bila komponen tidak ada di templat situs,
tampilkan satu baris "Butuh templat N+ (Naik templat di halaman Bangun)" dengan tombol yang membuka `Halls.PAGE_BANGUN`.
Langkah panduan terkait (c15.tangga/kitab/leluhur, c16.bendera/kebun/tambang) diberi hint yang sama.

### S9 (SEDANG, SEDANG DIBANGUN): Suara 36 baru tidak akan terdengar

**Bukti.** `docs/progres/sekte-voices.md` mengusulkan 30 kunci (`sulk`, `rebel`, `mourn`, `study`, `harvest`,
`boss.sect_master`, …), dan working tree sedang menambahkannya ke `sounds.json` (diff menambah `voice.boss.sect_master`,
`voice.disciple.f1.abandoned`, …). Namun panggilan di `sekte/**` hanya memakai kunci lama (`greet`, `thanks`, `loyal`,
`farewell`, `tired`, `careful`, `victory`, …, hasil hitung `grep VoiceDisciples.line`).

**Perbaikan.** Setelah generator suara selesai, ganti panggilan sesuai tabel di `sekte-voices.md`, misalnya
`Kepribadian` sulk ke `"sulk"`, `Upacara` funeral ke `"mourn"`, dan `Pertumbuhan` panen ke `"harvest"`. Pakai cadangan
ke kunci lama bila kunci baru belum ada di `sounds.json`, dengan pola E `report` (`BalaiTetua`).

### S10 (SEDANG): "Tetua Agung" berarti dua hal

**Bukti.** Jenjang `SectRanks.TETUA_AGUNG` ("Tetua Agung", `hall.rank.tetua_agung`) dan jabatan `Jabatan.TETUA_AGUNG`
dipakai bersamaan. Murid berjenjang Tetua bisa mendapat kursi jabatan Tetua Agung sebagai Wakil
(`JabatanTetua.java:74-80`), dan ingatannya berbunyi "Diangkat menjadi Tetua Agung" (`sekte_tetua.id_id.json`).

**Perbaikan.** Ganti nama jabatan menjadi **"Tetua Pengatur"** (atau "Ketua Balai Tetua") di `lang-src/sekte_tetua.*`.
Id `agung` tetap untuk kompatibilitas simpanan.

### S11 (SEDANG): Dua kebun

**Bukti.** Pos tugas "Kebun Seratus Obat" lama ada 5 blok dari prasasti, menghasilkan herba lewat waktu
(`Duties.java:49-56`). Petak herba W dibangun sebagai komponen, ditanami dari Lumbung, dan dirawat murid `ACT_TEND`
(`Pertumbuhan.java:271`). Keduanya disebut "kebun" dan memakai sumber yang sama (Kebun Seratus Obat).

**Perbaikan.** Di papan Kebun W, tampilkan juga pos kebun lama sebagai "Bedeng Tugas (pos 2)". Atau ubah nama pos lama
menjadi "Ladang Tugas", dan beri tooltip yang menunjuk perbedaan keduanya. Angka tidak berubah.

### S12 (SEDANG): Status tantangan T tidak dibersihkan

**Bukti.** `AntarSekte.challenge` menyimpan `Musim.setPending` (`AntarSekte.java:51`), yang hanya dibersihkan oleh
`accept`/`refuse` versi T (`AntarSekte.java:65-74`). Penerimaan lewat Utusan D memanggil `hostGuests` → `AntarSekte.host`
tanpa membersihkan pending (`Utusan.java:191-199`). Tombol chat T yang lama masih bisa diklik dan menjalankan turnamen
tamu **kedua**.

**Perbaikan.** Panggil `Musim.setPending(owner, null, 0L)` di dalam `AntarSekte.host` saat berhasil. Bagian ini ikut
selesai bila T1 diperbaiki.

### R1-R7 (RENDAH)

- **R1** Diskon 10% Contribution untuk sekutu disebut di DESIGN 36.2.9 (`DESIGN.md:23026`) tetapi tidak ada di kode.
  Pilih: hapus dari DESIGN, atau terapkan di harga Paviliun Yue Lu (`Contribution`) bila ada `Relasi.PAKTA_SEKUTU`.
- **R2** Empat kartu SEKTE berikon `Glyphs.SEAL`. Kartu `sekte_*` memakai `lock = null`, jadi bisa dibuka tanpa aula
  dan menampilkan layar kosong. Pakai glyph berbeda dan `hallLock`.
- **R3** help.serbuan menyebut "sebuah sekte iblis menyerbu". Dengan D, penyerbu bisa sekte NPC bernama yang sedang
  berperang (`Perang.java:241-249`). Perbarui teks, dan cek apakah kantong rampasan "Sekte Iblis" pantas untuk penyerbu
  aliran Lurus.
- **R4** Ada tiga akar perintah sekte. Tambahkan alias `/kultivasi sekte` → `perguruan` atau sebaliknya, dan sebut
  "perguruan" di help agar pemain tahu itu sistem 36.
- **R5** Misi "Lelang Kota" (PK, `TimMisi.java:54,286`) terpisah dari Balai Lelang kota dan Lelang Alam Abadi. Beri
  nama "Utus ke Lelang Kota" dan tautkan hasilnya ke kota terdekat `DuniaApi` agar ceritanya nyambung.
- **R6** DESIGN menyebut `c16.perang`, tetapi kode tidak punya langkah itu. Tambahkan `step(15, "perang", null,
  "adv:sekte/diplomasi_menang")` sebagai langkah opsional (manual) di akhir bab 16.
- **R7** `DiplomasiService` (`api/DiplomasiService.java:21-52`) belum punya `meet(owner, sectId)`. 37.7.6 menunggu
  metode ini. Tambahkan `default void meet(UUID owner, String sectId) {}` yang diisi `Diplomasi.meet` sebelum 37G
  mulai.

---

## Rencana penggabungan sistem lama dan baru (ringkas)

| Sistem lama | Sistem 36 | Usulan |
|---|---|---|
| `Expeditions` (papan harian) | Misi Cerita X | Satu tablet, dua tab (T5) |
| `Envoys` Utus Perekrut | Kepulangan X membawa pemuda | Ganti nama "Utus Pencari Bakat", tetap terpisah, tampil di tab Harian |
| — | Aksi D "Kirim Utusan" + misi `UTUSAN` | Satu jalur: aksi D membuat misi X (T5) |
| `Tournament` Elit (`Rivals` statis) | Turnamen antar-sekte T/D | Elit memakai sekte NPC D (S3) |
| Tantangan T (chat) | Utusan D TANTANG | Satu prompt (T1/S12) |
| `Duties` pos kebun | Petak herba W | Satu papan Kebun (S11) |
| `Candidates` pendakian 3 tingkat | Tangga Seribu G | Sudah digabung lewat hook; perbarui help.calon (S2) |
| `SectRaids` iblis | Perang D lewat `Director` | Sudah digabung; perbaiki umpan balik (S6) dan help (R3) |
| `/sekte` sekte pemain | Aula + 36 | Ganti istilah menjadi "Saudara Sekte" dan tambahkan kartu hub (T6) |
| Jenjang Tetua Agung | Jabatan Tetua Agung | Ganti nama jabatan (S10) |

## Catatan integrasi untuk 37 (Sekte Langit, belum ada kode)

1. Ikuti aturan K1: item, entitas (Feri, KapalBajak, Pijakan), payload, dan komponen `aw_*` didaftarkan tanpa
   memandang `langit.aktif`. 37.8.7 berjanji "tanpa menghapus data", dan itu hanya benar bila registry tidak ikut mati.
2. Kartu hub `sekte_langit` (37.7.9) memperparah S1. Masukkan ke sub-halaman SEKTE.
3. Bab panduan langit (37.7.8) jangan ditambahkan sebagai bab 17 di ujung daftar (masalah T4). Gunakan mekanisme
   "bab terbuka berdasarkan ranah/aula".
4. 37.7.6 bergantung pada kontak publik Diplomasi (R7) dan menambah penyerbuan langit lewat `SectRaids`. Perbaiki S6
   lebih dulu agar "Bajak Awan" tidak mengumumkan serangan yang tidak terjadi.
5. Kepulangan misi X lewat gerbang (`MisiConfig.kembaliLewatGerbang`) harus memakai titik kedatangan langit
   (`Halls.addArrivalSpotProvider` atau jangkar feri). Feri misi 37.5.5 menyebut `Expeditions` dan X. Bila T5
   digabung, cukup satu kait.
6. 37 sudah menangani komponen `sx_*` 36 di templat 11-19 dengan benar (37.4.5, 37.4.9 butir 6), jadi tidak perlu
   perubahan.

## Yang sudah dicek dan BAIK

- Ke-20 advancement `sekte/*` punya JSON dan semuanya diberikan oleh kode (`grep Advancements.grant`).
- Semua field config modul 36 dibaca kode (tidak ada sakelar mati).
- Komponen 36 hanya dibangun `BuildPlacer` lewat `TemplateExtensions`. Tidak ada `setBlock`, LightningBolt, atau cuaca
  atau waktu di `sekte/**` (sesuai self-review `sekte-finish.md`).
- Layar klien diplomasi/misi hanya berupa layar. Renderer modul lain memakai `EffectQuality`/`FxBudget`.
- Tangga G sudah digabung lewat hook di `Candidates` (angka lama tetap). Serbuan D digabung lewat `SectRaids.Director`.
- Kepergian murid: `Kesetiaan.bound` melindungi Segel Jiwa dan pewaris (`Kesetiaan.java:44-46`), termasuk Ketua Generasi
  Kedua. Hu Fa adalah peran guru, bukan murid, jadi catatan sementara sebelumnya ("Hu Fa bisa pergi") **dicabut**.
- Jurus Pemusnah otomatis selalu aman blok (`DestroyerArts.java:41-46`), juga dalam perang D dan kunjungan Ketua NPC.
