# Audit Ekonomi & Item — laporan (audit:ekonomi)

Tanggal: 2026-10-22. Audit BACA-SAJA: tidak ada file proyek yang diubah selain laporan ini dan `docs/progres/audit-ekonomi.md`.
Gradle tidak dijalankan. Semua bukti sudah dicek ke kode (`src/main/java/dev/kultivator/...`, `src/main/resources/data/...`,
`lang-src/...`). Temuan Sekte yang Hidup (DESIGN 36, paket `dev.kultivator.sekte.**`) ditandai **SEDANG DIBANGUN**; catatan
rancangan Sekte Langit (`docs/rancangan/37-sekte-langit.md`) ditandai **RANCANGAN 37**.

Cakupan: batu roh dan Nilai Batu (NB), herba, inti binatang, pil & alkimia, jimat, relik peti, tempa & pusaka natal, cincin
penyimpanan, Segel Titah Langit, Batu Asal Abadi, esensi, semua toko/lelang/pasar gelap/Lelang Abadi/Balai Lelang/Pasar Abadi/
Pedagang Pulau/Paviliun Yue Lu, mata uang (batu roh, PK kas, Nama Baik, Reputasi Gelap, ketenaran), tabel hadiah (Kitab
Panduan, tugas harian, Papan Misi, Titah, Ekspedisi, Misi Cerita, tambang sekte, harta langit).

## Ringkasan jumlah

| Tingkat | Jumlah |
|---|---|
| KRITIS | 1 |
| TINGGI | 4 |
| SEDANG | 11 |
| RENDAH | 7 |

## Tabel temuan

| ID | Tingkat | Ringkas | Bukti utama |
|---|---|---|---|
| K1 | KRITIS | Cincin penyimpanan berisi bisa dijual ke toko / dititipkan ke Balai Lelang, seluruh isinya hilang | `dunia/kota/Toko.java:104-113,176-201`; `dunia/kota/BalaiLelang.java:463-483`; `dunia/kota/Harga.java:76-80` |
| T1 | TINGGI | Titipan Balai Lelang kota hanya di memori; server berhenti = barang lenyap tanpa bayaran | `dunia/kota/Kota.java:72-75`; `BalaiLelang.java:138-142,389-407` |
| T2 | TINGGI | Memadatkan bintang otomatis memakan Esensi Energi Makam (dan Batu Asal) yang justru dibutuhkan Kenaikan | `goals/Goals.java:82-83,143-158`; `abadi/Ascend.java:67,215`; `cultivation/ImmortalPerks.java:118`; `abadi/OriginStones.java:66-67` |
| T3 | TINGGI | Harga herba linear umur: herba 1000 tahun cuma ±22 NB di Paviliun Pil (band 6+), menghancurkan nilai hadiah herba milenium | `harta/SpiritHerbs.java:188-190`; `Harga.java:124-126,174,209-215`; `KotaAbadi.java:74`; `abadi/Auction.java:98` |
| T4 | TINGGI | Paviliun Yue Lu (PK) 8-22x lebih mahal dari toko kota; Pil Pembersih Sumsum 900 PK di Aula lv 6 padahal 80 NB atau resep vanilla tanpa gerbang | `sect/hall/Contribution.java:59-72`; `Harga.java:49-64`; `recipe/marrow_cleansing_pill.json` |
| S1 | SEDANG | NB Batu Agung 600 tapi resep 9:1 membuatnya = 9 Tinggi = 720 NB (+20% arbitrase); MISSION_PK sama | `dunia/kota/NilaiBatu.java:21`; `harta/SpiritStones.java:72`; `recipe/spirit_stone_*` |
| S2 | SEDANG | Kantor Misi menilai pil rata per mutu (5/10/20/40 PK): pil 4 NB = 10 PK, pil 2.000 NB juga 10 PK | `Contribution.java:153-160`; `Harga.java:49-64` |
| S3 | SEDANG | Dua lelang dengan aturan titipan berbeda (stack vs 1 buah, whitelist vs semua barang berharga, simpan vs memori) | `BalaiLelang.java:34-43,463-483`; `abadi/Auction.java:50-62,497-530`; `tags/item/auction_sellable.json` |
| S4 | SEDANG | Pasar Abadi tanpa batas stok; rasio Batu Asal:NB acak (22-642 NB/Batu); Pil Abadi 14 Batu dijual ke toko kota ±3.400 NB | `dunia/kota/KotaAbadi.java:73-82,270-319`; `Harga.java:64`; `Toko.java:104-121` |
| S5 | SEDANG | Titah Utusan: jenis "batu" setor 16 dapat 24 (hadiah gratis), jenis "mutiara" setor 2 (40 Batu) dapat 30 (rugi) | `KotaAbadi.java:63-68,361-380` |
| S6 | SEDANG | Mutiara Penunjuk Laut 12 NB di Pedagang Pulau, 900 NB di tabel harga, 1.350 NB di Pasar Gelap | `dunia/dimensi/laut/SeaPeople.java:85`; `Harga.java:101,206` |
| S7 | SEDANG | Pil untuk murid tidak sejalan dengan pemain: Pil Sumsum murid selalu sukses; 4 pil fase-7 ditolak murid "for now" padahal dijual toko | `disciple/DiscipleCultivation.java:730-736`; `item/MarrowCleansing.java:54`; `harta/Harta.java:214-222`; `alchemy/PillKind.java:131` |
| S8 | SEDANG | Inflasi NB di akhir permainan: Papan Misi x3 per band (19.440 NB di band 8) tanpa sink NB; hadiah harian tetap 1 Batu Rendah | `dunia/kota/PapanMisi.java:123-140`; `goals/DailyChores.java:274` |
| S9 | SEDANG | Istilah ganda: "Papan Misi" (kota & event sekte), "Kantor Misi" (bukan misi), "Titah" x3, "Pusaka Natal" x2 sistem tak terhubung | lang `kota.tab.misi`, `sectevent.ui.board`, `hall.ui.kantor`, `kota.tab.titah`; `disciple/power/NatalTreasure.java`; `tempa/NatalTreasureSelf.java` |
| S10 | SEDANG (SEDANG DIBANGUN) | Kas PK Sekte Hidup: banyak sink baru besar, faucet baru melewati batas harian, tak ada ringkasan/pembaca kas | `sekte/kebun/TambangRoh.java:82-97`; `sekte/kebun/KebunItems.java:38,67`; `sekte/diplomasi/DiplomasiPayloads.java:42`; `Contribution.java:56` |
| S11 | SEDANG (RANCANGAN 37) | Sesaji A9 (2 Agung = 1.200 NB) lebih murah dari A7-A8 (24 Tinggi = 1.920 NB); "Titah" keempat; trofi tanpa guna | `docs/rancangan/37-sekte-langit.md:285-290,1088-1089` |
| R1 | RENDAH | Item yatim: Lencana Pengembara, Sobekan Segel, Kitab Formasi Kuno (lore saja), Segel Titah kedua | `dunia/kota/KotaItems.java:45`; `dunia/reruntuhan/RItems.java:20-34`; `pusakalangit/HeavenDecreeSealItem.java:23-26` |
| R2 | RENDAH | "batu roh Tertinggi" dipakai, padahal nama item "Batu Roh Agung" | `lang-src/abadi.id_id.json:43,49,209` |
| R3 | RENDAH | Tabel NB dan logika bayar diduplikasi 3x; konstanta bahan bakar meditasi dobel | `NilaiBatu.java:21,55-100`; `dunia/dimensi/DimensiData.java:27,96-120`; `dunia/reruntuhan/RRewards.java:38`; `SpiritStones.java:67-68`; `cultivation/Meditation.java:103-104` |
| R4 | RENDAH | Pasar Gelap membeli tanpa batas harian (toko 16/hari) | `PasarGelap.java:192-205`; `Toko.java:182-186` |
| R5 | RENDAH (SEDANG DIBANGUN) | Hadiah ke murid: Batu Rendah = 2 kepuasan, Batu Agung = 6 (biaya 600x); barang lenyap | `sekte/kepribadian/Kepribadian.java:713-763` |
| R6 | RENDAH | Bahan bakar meditasi: Agung 30x biaya per detik untuk faktor 2x; resep 9:1 membuat Rendah selalu lebih hemat | `cultivation/Meditation.java:103-104,659` |
| R7 | RENDAH | Nama mirip yang membingungkan: Dupa / Dupa Arwah; Esensi Abadi / Mutiara Esensi Abadi / Esensi Energi Makam | `sekte/paviliun/Dupa.java:23`; `dunia/dimensi/DimensiItems.java:36`; lang `item.kultivator.*` |

---

## Detail

### K1 — KRITIS: cincin penyimpanan berisi bisa dijual atau dititipkan, isinya hilang

**Bukti.**
- `Toko.buys` (`dunia/kota/Toko.java:104-113`) menerima `id.startsWith("storage_")` di Paviliun Pusaka; `Toko.sell`
  (`Toko.java:176-201`) langsung `stack.shrink(count)` lalu memberi `buyPrice` (0,35-0,40 x base: kantong 7-8 NB, cincin
  Rendah 21-24 NB). Tidak ada pemeriksaan isi `DataComponents.CONTAINER`.
- `Harga.value` memberi nilai > 0 untuk `storage_pouch`/`storage_ring_*` (`Harga.java:76-80`), jadi
  `BalaiLelang.consign` (`BalaiLelang.java:463-483`) juga menerima cincin di tangan dan menyalin stack-nya ke memori (lihat
  T1: bisa hilang total).
- Isi cincin disimpan di komponen `CONTAINER` (`tempa/StorageRingItem.java:304-305`), ikut hilang bersama itemnya.
- Kontras: Lelang Abadi memakai whitelist `#kultivator:auction_sellable` tanpa cincin (`tags/item/auction_sellable.json`).

**Kenapa memutus.** Satu klik "Jual" di tab toko dengan cincin di tangan (cincin adalah barang yang sering dipegang) membuang
sampai puluhan tumpuk barang tanpa peringatan. `RingProtection` bersusah payah melindungi cincin dari api, lava, void
(`tempa/RingProtection.java`), tetapi toko kota menjadi lubang yang tak terlindungi. Ini kehilangan data pemain yang tak bisa
dibalik.

**Perbaikan.**
1. `Toko.buys`: tolak cincin yang isinya tidak kosong (`stack.get(DataComponents.CONTAINER)` dengan
   `nonEmptyItemCopyStream().findAny().isPresent()`), juga tolak cincin yang sudah terikat (`StorageRingItem.bondOwner(stack) != null`).
   Tampilkan pesan baru `kultivator.kota.toko.cincin_berisi` ("Kosongkan cincin dulu") di `lang-src/kota.en_us.json` + `.id_id.json`.
2. `BalaiLelang.consign`: pemeriksaan yang sama; lebih baik ganti ke whitelist seperti Lelang Abadi (lihat S3).
3. `Toko.view` (`Toko.java:224-230`): jangan isi `hand_price` untuk cincin berisi, supaya tombol Jual tidak muncul.

### T1 — TINGGI: titipan Balai Lelang hilang saat server berhenti

**Bukti.** `BalaiLelang.SESSIONS` adalah `HashMap` biasa (`BalaiLelang.java:138`); `consign` mengambil barang dari tangan
(`hand.shrink(hand.getCount())`, `BalaiLelang.java:479-481`) dan menyimpannya hanya di `session.consigned`.
`Kota.java:72-75` mendaftarkan `SERVER_STOPPED -> BalaiLelang.clear()` yang membuang semua sesi tanpa memanggil `finish`
(`BalaiLelang.java:389-407`, satu-satunya tempat titipan dibayar). Di pemain tunggal, keluar dunia saat senja (sesi
12.000-14.000) setelah menitip = barang hilang. Lelang Abadi justru menyimpan titipan di `extra` pemain dan membayarnya saat
masuk kembali (`abadi/Auction.java:520-571`).

**Kenapa memutus.** Dua lelang yang "aturannya sama" (javadoc `BalaiLelang.java:34-43` menyebut "These are the rules of
Lelang Abadi") berperilaku berbeda di titik paling sensitif: keselamatan barang.

**Perbaikan.** Di `BalaiLelang`: saat `consign`, tulis entri `{town, day, item (ItemStack.CODEC), value}` ke
`KotaData.player(uuid).getList("titipan")` lalu `touch()`; `finish` membayar dan menghapus entrinya; pada `SERVER_STOPPED`
panggil `forceClose`-versi-bayar (atau biarkan entri tetap dan bayar di kunjungan berikutnya seperti `owed`,
`BalaiLelang.java:402-405`). Minimal: ganti `Kota.java:75` menjadi pemanggilan `BalaiLelang.forceClose(server)` sebelum
`clear()` (pemain offline masuk `owed`).

### T2 — TINGGI: pemadatan bintang memakan bahan Kenaikan

**Bukti.** `Goals.init` mendaftarkan diskon biaya bintang 2% per Esensi Energi Makam yang dibawa (maks 10) dan
`addCondenseListener` yang **mengambil** esensi itu setiap kali bintang dipadatkan (`goals/Goals.java:63-64,82-83,143-158`).
Kenaikan Alam butuh tepat 9 `tomb_essence` dan ★9 (`abadi/Ascend.java:67,198,215`; `ImmortalPerks.GATE_I_STARS = 9`,
`cultivation/ImmortalPerks.java:118`). Pemain yang mengumpulkan 9 esensi lalu memadatkan bintang ke-9 kehilangan semuanya
dan gerbang Kenaikan kembali tertutup. Hal serupa: `OriginStones.java:66-67` memakan sampai 10 Batu Asal per bintang,
bersaing dengan tabungan untuk tawaran Lelang Abadi, resep Segel Titah (`recipe/heaven_decree_seal.json`) dan Titah Utusan.
Esensi juga dipakai `BeastEvolution` (3) dan resep alkimia (`alchemy/AlchemyRecipes.java:268`).

**Kenapa memutus.** Sebuah bonus pasif diam-diam menghapus bahan yang dijelaskan Kitab Panduan sebagai syarat langkah
berikutnya (`goals/Guide.java:569` bahkan memberi 3 esensi sebagai hadiah bab 9). Pesan baru muncul **setelah** esensi hilang.

**Perbaikan.** (a) `Goals`: jangan pakai esensi bila `!Abadi.ascended(player)` dan esensi ≤ `Ascend.OFFER_ESSENCE` (sisakan 9);
atau (b) jadikan diskon opsional: tombol "Pakai Esensi/Batu Asal untuk diskon" di layar pemadatan bintang
(`ImmortalPerks.condense(player, all)` sudah tahu konteksnya), default mati sebelum Kenaikan; (c) tampilkan di pratinjau biaya
bintang "akan memakai N Esensi / N Batu Asal". Tambah kunci lang di fragment `goals`/`abadi` (en_us + id_id).

### T3 — TINGGI: herba berumur dihargai linear, herba milenium jadi murah

**Bukti.** `SpiritHerbs.missionValue(age) = 1 + age/100` (`harta/SpiritHerbs.java:188-190`) dan `Harga.value` herba =
missionValue x 2 (`Harga.java:124-126`). Paviliun Pil menjual herba band 6+ berumur 500-1.000 tahun (`Harga.java:174,209-215`)
seharga 11-22 NB, sementara:
- Pasar Abadi menjual herba 1.000 tahun 8 Batu Asal (`KotaAbadi.java:74`);
- Lelang Abadi membuka herba 1.000-1.500 tahun dengan dasar 25 Batu Asal (`abadi/Auction.java:98`);
- Harta langit T3 menjadikan "a millennium herb" hadiah inti (`dunia/langit/TreasureRewards.java` javadoc);
- herba 1.000 tahun memberi bonus mutu maksimum di tungku dan tempa (`SpiritHerbs.java:88-90,182-184`; `tempa/Forging.java` `HERB_YEARS/HERB_CAP`).

**Kenapa memutus.** Barang yang dalam lore dan di sistem lain "langka sekali" menjadi komoditas murah di kota begitu pemain
mencapai Penyatuan Dao; hadiah harta langit T3 dan lot Lelang Abadi terasa tidak berharga (lompatan progresi ke bawah).

**Perbaikan.** Di `Harga.value` pakai kurva non-linear khusus kota, mis. `max(1, round(2 x (1 + (age/100)^1.6)))`
(100 th = 4, 300 th ≈ 12, 1.000 th ≈ 82) **atau** potong band herba toko: `herbMax` maksimum 300 tahun
(`Harga.java:213-215`), herba 500+ hanya dari Balai Lelang/harta/Pasar Abadi. Kantor Misi (`Contribution.unitValue`) boleh
tetap linear karena dibatasi 300 PK/hari.

### T4 — TINGGI: Paviliun Yue Lu tidak sejalan dengan toko kota

**Bukti.** Rasio resmi 1 Batu Rendah = 0,5 PK = 1 NB (`Harga.java:26-29`), jadi 1 PK = 2 NB. Yue Lu (`Contribution.java:59-72`)
vs toko (`Harga.java:49-64`):

| Barang | Yue Lu | setara NB | Toko kota |
|---|---|---|---|
| Pil Penyembuh | 40 PK | 80 | 4 NB (20x) |
| Pil Pengumpul Qi | 60 PK | 120 | 4 NB (30x) |
| Pil Fondasi | 250 PK | 500 | 60 NB (8x) |
| Pil Inti Emas (Aula lv 4) | 800 PK | 1.600 | 400 NB (4x) |
| Pil Pembersih Sumsum (Aula lv 6) | 900 PK | 1.800 | 80 NB (22x) |
| Pil Penangkal Petir (Aula lv 3) | 600 PK | 1.200 | 2.000 NB (0,6x) |

Pil Pembersih Sumsum juga bisa dibuat di meja kerja dari barang vanilla tanpa gerbang ranah
(`recipe/marrow_cleansing_pill.json`: semangka berkilau, air mata ghast, apel emas, ametis, tepung tulang), dan toko kota
menjualnya mulai band 1. Mengunci barang yang sama di Aula level 6 dengan 900 PK membuat "hadiah" kenaikan level Aula
tidak berarti.

**Kenapa memutus.** Pemain diberi tahu Aula level 6 membuka sesuatu yang istimewa, padahal barang itu sudah ada di kota sejak
Pemurnian Qi. Yue Lu menjadi toko mati kecuali Ceramah, Undangan, Utusan dan Slip Teknik.

**Perbaikan.** Selaraskan `Contribution.Ware` ke Harga/2 x (1,5-2 sebagai "harga sekte"): Penyembuh 6, Pengumpul Qi 6,
Fondasi 60, Inti Emas 300, Penangkal 1.000; ganti MARROW dengan barang yang tidak dijual kota (mis. Pil Pembersih Sumsum mutu
SEMPURNA, atau `soul_nurturing_wood`), atau beri Pil Sumsum dari Yue Lu efek tambahan untuk murid. Alternatif: tutup jalur toko
kota untuk Pil Sumsum (hapus dari `Harga.PILLS`, `Harga.java:166`) dan beri resep vanilla gerbang ranah. Perbarui teks bantuan
`kultivator.screen.help.kontribusi`.

### S1 — SEDANG: nilai Batu Agung tidak konsisten dengan resep 9:1

**Bukti.** `NilaiBatu.VALUE = {1, 9, 80, 600}` (`NilaiBatu.java:21`), `MISSION_PK = {0.5, 4.5, 40, 300}`
(`SpiritStones.java:72`), tetapi resep tukar dua arah 9:1 (`recipe/spirit_stone_high_from_supreme.json` 1 Agung -> 9 Tinggi;
`spirit_stone_supreme_from_high.json` 9 Tinggi -> 1 Agung). 1 Agung = 9 Tinggi = 720 NB; 1 Tinggi = 9 Menengah = 81 NB.
`NilaiBatu.give` memberi kembalian "terbesar dulu" (`NilaiBatu.java:103-117`), jadi setiap pembayaran >= 600 NB berupa Batu
Agung yang bisa dipecah jadi 720 NB (+20%). Di Kantor Misi, menyetor 9 Tinggi = 360 PK, 1 Agung = 300 PK.

**Perbaikan.** Pilih satu: (a) `VALUE = {1, 9, 81, 729}` dan `MISSION_PK = {0.5, 4.5, 40.5, 364.5}` (konsisten penuh), lalu
sesuaikan harga di `Harga` yang berpatokan Agung; atau (b) pertahankan angka dan ubah resep pecah Agung menjadi 1 -> 7 Tinggi +
4 Menengah + 6 Rendah (setara 600) — resep shapeless tak bisa menghasilkan dua jenis, jadi (a) lebih bersih. Salinan tabel di
`DimensiData.java:27` dan `RRewards.java:38` ikut diubah (lihat R3).

### S2 — SEDANG: Kantor Misi menilai pil rata per mutu

**Bukti.** `Contribution.unitValue` memberi pil apa pun 5/10/20/40 PK menurut mutu (`Contribution.java:153-160`). Pil
Penyembuh Menengah dari toko (4 NB) = 10 PK = 20 NB setara (keuntungan 5x, dibatasi 300 PK/hari, `Contribution.java:56`);
Pil Penangkal Petir (2.000 NB) atau Pil Abadi (9.000 NB) juga hanya 10 PK.

**Perbaikan.** Pakai `Harga.base(PillKind.id) / 2 x faktor mutu` (Rendah 0,6 / Menengah 1 / Tinggi 1,5 / Sempurna 2,5),
dengan batas bawah 1 PK. `Harga` ada di paket `dunia.kota` (server), jadi aman dipanggil dari `sect.hall`. Perbarui tooltip
`kultivator.harta.tooltip.mission` bila pil juga diberi tooltip nilai.

### S3 — SEDANG: dua lelang, aturan berbeda

**Bukti.**

| Aspek | Lelang Abadi (`abadi/Auction.java`) | Balai Lelang kota (`dunia/kota/BalaiLelang.java`) |
|---|---|---|
| Barang titipan | whitelist tag `auction_sellable` + tabel `VALUES` (`:83-84,111-115,504-509`) | apa pun dengan `Harga.value > 0`, termasuk cincin (`:475-477`) |
| Jumlah | 1 buah per titipan (`hand.shrink(1)`, `:522`) | seluruh stack (`hand.shrink(hand.getCount())`, `:480`) |
| Nilai | per barang, ≤ setengah dasar lot barang itu | nilai stack dicap `meanBase/2` sesi (`:391-395`) — stack 16 barang dibayar seperti 1 |
| Penyimpanan | `extra` pemain, dibayar saat login (`:532-571`) | memori saja (T1) |
| Batas rival | 0,6-1,6 x dasar (`:69-70`) | 0,6-1,5 x dasar (javadoc `:38`) |

Selain itu titipan kota bisa jadi untung kecil: beli di toko saat faktor hari 0,9 dengan diskon Nama Baik 10% (0,81 x base,
`Toko.java:65,73-74`), titip, dibayar rata-rata 1,0 x base (`BalaiLelang.java:395`).

**Kenapa memutus.** Pemain belajar aturan di satu lelang lalu kehilangan satu tumpuk penuh di lelang lain.

**Perbaikan.** Satukan aturan dalam satu kelas bantu (mis. `dunia/kota/Titipan` dipakai keduanya): 1 buah per titipan,
whitelist (tag baru `#kultivator:kota_sellable` atau pakai `Toko.buys` gabungan pil/pusaka), nilai per buah dicap `min(value,
meanBase/2)`, bayaran 0,6-1,0 x (bukan 0,7-1,3) agar tidak ada lingkar beli-titip, dan simpanan persisten (T1).

### S4 — SEDANG: Pasar Abadi tanpa batas stok, rasio Batu Asal:NB acak

**Bukti.** `KotaAbadi.offers` membuat 5 tawaran per 2 hari tanpa hitungan stok (`KotaAbadi.java:270-297`); `buy` tidak
mengurangi apa pun (`:298-319`). Javadoc menulis "priced so the 16-a-day mining cap ... buys a rare item in about a week"
(`:44-45`), tapi Pil Abadi 14 Batu (`:75`) bisa dibeli berulang. Toko Paviliun Pil membeli semua `*_pill` (`Toko.java:109-110`)
pada 0,35-0,40 x `Harga.base("immortal_pill") = 9.000` (`Harga.java:64`) = 3.150-3.600 NB per pil, 16 transaksi/hari, pil
bertumpuk 16 (`alchemy/AlchemyItems.java:33`). Rasio barang yang sama di dua mata uang:

| Barang | NB (`Harga`) | Batu Asal | NB per Batu |
|---|---|---|---|
| Kristal Es Warisan | 900 | 40 (Lelang Abadi) | 22 |
| 2 Batu Roh Agung | 1.200 | 30 (Lelang Abadi) | 40 |
| Kayu Pemelihara Jiwa | 1.500 | 25 | 60 |
| Giok Segel Jiwa | 2.000 | 18 (Pasar Abadi) | 111 |
| Token Perluasan Tanah | 15.000 | 80-110 | 136-187 |
| Mutiara Esensi Abadi | 4.000 | 20 | 200 |
| Benih Api Surgawi | 20.000 | 60 | 333 |
| Pil Abadi | 9.000 | 14 | 642 |

**Perbaikan.** (a) Stok per pemain per periode di `KotaAbadi` (simpan `abadi_beli.<index>` di `KotaData.player`, 1-2 per
tawaran per 2 hari); (b) toko kota tidak membeli barang fase 9 (tolak `immortal_pill`, `immortal_essence_pearl`, dll. di
`Toko.buys`) atau turunkan `Harga.BASE("immortal_pill")`; (c) tetapkan satu kurs acuan di DESIGN 34.5 (mis. 1 Batu Asal ≈ 150
NB) dan sesuaikan `Auction.ROWS` / `KotaAbadi.MARKET` / `Harga.BASE` agar urutan nilai relatif sama.

### S5 — SEDANG: imbalan Titah Utusan tidak konsisten

**Bukti.** `EDICT_NEED = {16, 2, 1, 12}`, `EDICT_PAY = {24, 30, 60, 20}` (`KotaAbadi.java:67-68`). Jenis 0 "batu": setor 16 Batu
Asal, terima 24 (+8 gratis, tanpa tantangan — javadoc `:48-49` menjanjikan "pointing at a phase-9 challenge"). Jenis 1
"mutiara": setor 2 Mutiara Esensi Abadi (Pasar Abadi 20 Batu masing-masing, `:76`) terima 30 (rugi 10). Jenis dipilih
`floorMod(week*31 + uuid.hashCode)` (`:356`), jadi pemain tertentu terus kebagian jenis rugi pada minggu tertentu.

**Perbaikan.** Jenis "batu" diganti tantangan nyata (mis. tambang 16 Batu Asal minggu ini — hitung dari `OriginStones` harian)
atau imbalan non-Batu (Persik ekstra, +PK, gelar); jenis "mutiara" bayar 50. Samakan dengan javadoc dan
`kultivator.kota.titah.*`.

### S6 — SEDANG: harga Mutiara Penunjuk Laut bertentangan

**Bukti.** Pedagang Pulau menjual 1 buah 12 NB (`dunia/dimensi/laut/SeaPeople.java:85`); `Harga.BASE` 900 NB
(`Harga.java:101`); Pasar Gelap menjualnya 1,5 x = 1.350 NB tanpa gerbang (`Harga.java:206`, `PasarGelap.java:52`). Bisa
dititipkan di Balai Lelang kota sampai cap sesi (`BalaiLelang.java:391-395`).

**Perbaikan.** Pilih satu harga: naikkan harga pedagang ke ±600-900 NB (dengan syarat langkah misi laut) atau turunkan
`Harga.BASE` ke 20 dan keluarkan dari daftar `SHADOW`.

### S7 — SEDANG: pil untuk murid tidak sejalan dengan pemain

**Bukti.**
- Pemain: Pil Pembersih Sumsum naik akar dengan peluang 100/85/55/30% menurut tingkat (`item/MarrowCleansing.java:54,80-81`).
  Murid: selalu naik satu tingkat (`disciple/DiscipleCultivation.java:730-736`). Satu pil 80 NB di toko = akar Surgawi murid
  dalam 3-4 pil.
- Empat pil fase 7 (Wawasan Dao, Pembersih Racun, Pencerahan, Penempa Tulang) ditolak murid "for now"
  (`harta/Harta.java:214-222`, `alchemy/PillKind.java:130-133`) padahal dijual toko dan dihadiahkan; Sekte Hidup menambah
  "hadiah pil" ke murid (`sekte/kepribadian/Kepribadian.java:727-731`) yang lalu gagal diam-diam untuk pil ini.

**Perbaikan.** `DiscipleCultivation.applyMarrow`: pakai `MarrowCleansing.STEP_CHANCE` dan `quality.multiplier()` yang sama.
Untuk pil fase 7: tulis efek murid sederhana di `DiscipleCultivation` (Pembersih Racun = hapus toksisitas pil murid, Pencerahan =
+pemahaman, Penempa Tulang = +nyawa permanen kecil, Wawasan Dao = +pemahaman Dao murid) lalu hapus `refuseNewPills`. Sampai
itu ada, `Kepribadian.giftValue` harus mengembalikan 0 untuk pil `phase7()` agar tombol Hadiah menjelaskan penolakannya.

### S8 — SEDANG: NB menumpuk di akhir permainan, hadiah harian tidak berskala

**Bukti.** Papan Misi: 6-12 NB (band ≤ 1), 15-30 (band 2), 40-80 (band 3), lalu x3 per band (`PapanMisi.java:123-140`):
band 8 = 9.720-19.440 NB per misi, 3 misi/hari (`KotaConfig.bountiesPerDay`). Barang NB termahal yang bisa dibeli: cincin
Tinggi 2.500, pusaka mutu 4 2.400, Pil Penangkal 2.000 (`Harga.java:62,79,112`); tak ada sink NB baru di band 6-8 (Lelang Abadi
dan Pasar Abadi memakai Batu Asal). Sebaliknya "selesaikan semua tugas harian" memberi 1 Batu Rendah di ranah apa pun
(`goals/DailyChores.java:274`).

**Perbaikan.** (a) Papan Misi: x2 per band dan cap pada band 6 (atau bayar Batu Asal di Alam Abadi); (b) tambah sink NB akhir:
"Penukar Batu" di Kota Abadi (NB -> Batu Asal, kurs tetap, batas mingguan) — mengaitkan dua ekonomi tanpa lingkar; (c)
`DailyChores.completeDay`: batu menurut ranah (Rendah x2 di Fana-Fondasi, Menengah di Inti Emas-Jiwa Baru Lahir, Tinggi di
Dewa-Penyatuan Dao, Batu Asal 1 di Abadi).

### S9 — SEDANG: istilah ganda untuk hal berbeda

**Bukti dan masalah.**
- "Papan Misi": papan kota (`kultivator.kota.tab.misi` = "Papan Misi", `kota.misi.judul` = "Papan Misi Kota") dan papan
  event sekte (`kultivator.sectevent.ui.board` = "Papan Misi (T* %s)"), ditambah "Papan Misi Cerita" Sekte Hidup.
- "Kantor Misi" (`kultivator.hall.ui.kantor`) bukan tempat misi melainkan kantor persembahan; tooltip herba/batu/inti
  "Kantor Misi: %s PK per buah" (`kultivator.harta.tooltip.mission`) muncul sebelum pemain punya sekte.
- "Titah": Segel Titah Langit (`pusakalangit/*`, DESIGN 29), "Titah Utusan" mingguan Kota Abadi (`kultivator.kota.tab.titah`),
  dan rancangan 37 "Utusan Istana Langit ... membacakan titah" (`37-sekte-langit.md:1088`).
- "Pusaka Natal": milik murid, lipatan virtual berbiaya PK (`disciple/power/NatalTreasure.java`, lang `dpower.natal.*`) dan
  milik pemain, pusaka tempaan yang diserap (`tempa/NatalTreasureSelf.java`, `spine8.tab.pusaka` = "Pusaka Natal"). Dua
  sistem tidak saling kenal: pusaka hasil tempa tidak bisa menjadi Pusaka Natal murid, dan Keahlian Tempa tidak memengaruhi
  lipatan murid.

**Perbaikan.** Ganti nama: papan event sekte -> "Papan Ekspedisi"; "Kantor Misi" -> "Kantor Persembahan" (tooltip
`harta.tooltip.mission` hanya tampil bila `Halls.hallOf` ada, atau ganti ke "Nilai persembahan sekte"); Titah Utusan -> "Perintah
Utusan" atau "Tugas Istana Giok"; Pusaka Natal murid -> "Pusaka Natal Murid" di semua kunci `dpower.natal.*`, dan tambahkan
jalur "Persembahkan pusaka tempaan Misterius+ sebagai bentuk awal" di `NatalTreasure` (memberi lipatan awal = mutu pusaka) agar
tempa ikut melayani murid. Ubah teks en_us + id_id dengan kunci identik.

### S10 — SEDANG (SEDANG DIBANGUN): ekonomi PK kas Sekte Hidup

**Bukti.** Faucet lama yang dibatasi: persembahan 300 PK/hari (`Contribution.java:56`). Sink baru Sekte Hidup: tambang
`200 x tingkat` (total ke tingkat 9 = 9.000 PK, `sekte/kebun/TambangRoh.java:82-86`, `KebunConfig.tambangPK`), hadiah diplomasi
60/150/300 PK (`sekte/diplomasi/DiplomasiPayloads.java:42`), misi lelang 30 x tingkat (`sekte/misi/TimMisi.java:54,286-289`),
Ujian Hati 200 PK, seragam 100 PK, Abadikan 50 PK (DESIGN 36.2.4/36.2.5/36.2.8). Faucet baru tanpa batas harian: Inti Roh
Gunung +300 PK (`sekte/kebun/KebunItems.java:38,67`), upeti/perang/simulasi diplomasi (`sekte/diplomasi/Aksi.java:186`,
`Perang.java:161,263`, `Simulasi.java:262,270,400`), misi +4 x tingkat x nilai (`sekte/misi/Kepulangan.java:165-170`). Lipatan
Pusaka Natal murid juga memakan `n x 40` PK (`disciple/power/NatalTreasure.java:31`). Halls tidak punya pembaca kas publik
sehingga `TambangRoh.poolHas` memeriksa dengan "ambil lalu kembalikan" (`TambangRoh.java:88-97`).

**Kenapa memutus.** Pemain tidak punya satu tempat untuk melihat ke mana PK pergi; kenaikan tambang tingkat tinggi butuh
berminggu-minggu persembahan, sementara hasil tambang (batu roh) hanya bisa jadi PK lewat jalur yang dibatasi 300/hari yang sama.

**Perbaikan (untuk tim Sekte Hidup).** Tambah `Halls.pool(UUID)` (baca saja) dan ganti probe; tambahkan ringkasan "PK masuk /
keluar 7 hari" di kartu hub SEKTE (sumber dari `Halls.log` yang sudah ada); pertimbangkan `tambangPK` 120 atau kurva
`100 x tingkat^1.3`; hasil tambang batu roh dihitung terpisah dari cap persembahan (mis. setoran Lumbung -> kas otomatis dengan
cap sendiri).

### S11 — SEDANG (RANCANGAN 37): catatan Sekte Langit

**Bukti.** `docs/rancangan/37-sekte-langit.md:285-290`: A4-A6 8 Tinggi (640 NB), A7-A8 24 Tinggi (1.920 NB), A9 2 Agung
(1.200 NB) — templat tertinggi lebih murah dalam batu roh. `:1088`: Utusan Istana Langit memberi "titah" (nama ketiga/keempat
"Titah", lihat S9) dengan opsi "setor 8 batu roh HIGH" untuk 500 renown — konversi batu -> ketenaran baru di luar Kantor
Misi. `:1089`: trofi `kultivator:cangkang_kura_langit` "bahan altar" belum punya resep/pemakaian yang disebut.

**Perbaikan rancangan.** A9: 4 Agung (atau 36 Tinggi); utusan istana menyebut tugasnya "Perintah Istana Langit"; tulis
pemakaian nyata cangkang (mis. bahan Altar Leluhur 36.2.5 atau tempa pusaka Langit+), dan masukkan ke `Harga.BASE` bila boleh
dijual.

### R1 — RENDAH: item yatim

**Bukti.** `lencana_pengembara` hanya diberikan (`KotaDuel.java:304`, `Pengembara.java:561`) dan dipakai kriteria advancement;
`sobekan_segel` hanya diberikan (`dunia/reruntuhan/penjara/ShrineRun.java:117`); `kitab_formasi_kuno` hanya bisa dibaca
(javadoc `RItems.java:20-24` mengakui tak ada kait diskon formasi). Segel Titah kedua tidak memberi apa pun
(`HeavenDecreeSealItem.java:23-26`) tetapi resepnya bisa dibuat berulang dengan Batu Asal.

**Perbaikan.** Lencana: Pasar Gelap/Saingan menerima 3 lencana sebagai "surat jalan" (diskon 10%) atau tukar di Kedai Teh;
Sobekan Segel: katalis jimat (`talisman/Inscribe.java`, +5% sukses) atau dibeli Pasar Gelap (`PasarGelap.buys`, `Harga.BASE`
40); Kitab Formasi Kuno: tambah kait diskon pola di `formasi` atau jadikan syarat formasi tertentu; Segel Titah: tolak craft
kedua (cek `TitahItems.OWNER` di inventaris) atau beri tooltip "hanya satu yang berfungsi".

### R2 — RENDAH: "batu roh Tertinggi"

**Bukti.** `lang-src/abadi.id_id.json:43,49,209` memakai "batu roh Tertinggi"; nama item `item.kultivator.spirit_stone_supreme`
= "Batu Roh Agung". **Perbaikan.** Ganti ketiga kalimat menjadi "Batu Roh Agung" (en_us tidak berubah).

### R3 — RENDAH: duplikasi tabel NB dan konstanta bahan bakar

**Bukti.** `NilaiBatu.VALUE` (`NilaiBatu.java:21`), `DimensiData.NB` + `pay/giveNb` sendiri (`DimensiData.java:27,96-133`),
`RRewards.NB` (`dunia/reruntuhan/RRewards.java:38`). `SpiritStones.FUEL_SECONDS/FUEL_XP` (`SpiritStones.java:67-68`, dibaca
tooltip `SpiritStoneItem.java:60-61`) vs `Meditation.FUEL_SECONDS/FUEL_FACTOR` (`cultivation/Meditation.java:103-104`, dipakai
logika). **Perbaikan.** `DimensiData`/`RRewards` memanggil `NilaiBatu.pay/give/VALUE`; tooltip membaca konstanta `Meditation`
(atau sebaliknya) — satu sumber angka.

### R4 — RENDAH: Pasar Gelap tanpa batas jual harian

**Bukti.** `PasarGelap.sell` (`PasarGelap.java:192-205`) tidak memakai `dailyCount`, sedangkan `Toko.sell` membatasi 16/hari
(`Toko.java:182-186`). Barang yang dibeli (kantong rampasan, panji, kantong saingan) terbatas jumlahnya, jadi dampak kecil.
**Perbaikan.** Pakai `KotaData.dailyCount(..., "gjual")` dengan batas sama atau tulis di bantuan bahwa makelar membeli tanpa batas.

### R5 — RENDAH (SEDANG DIBANGUN): nilai hadiah ke murid

**Bukti.** `Kepribadian.giftValue` (`sekte/kepribadian/Kepribadian.java:751-763`): Batu Rendah 2, Menengah 3, Tinggi 5, Agung 6;
herba 2-6; batu/herba lalu `shrink(1)` dan hilang (`:731-733`). Biaya per poin kepuasan: Rendah 0,5 NB, Agung 100 NB.
**Perbaikan.** Skala nilai dengan log NB (`2 + round(log9(NB))` -> 2/3/4/5) **dan** salurkan barang: batu ke qi/XP murid
(`DiscipleCultivation` sudah punya bagian `qiPart`), herba ke Lumbung, supaya hadiah tidak hilang begitu saja.

### R6 — RENDAH: bahan bakar meditasi tinggi tidak sepadan

**Bukti.** `FUEL_SECONDS {30,150,300,600}`, faktor `{1.5,2.0,2.5,3.0}` (`Meditation.java:103-104`). NB per detik: Rendah 0,033,
Menengah 0,06, Tinggi 0,27, Agung 1,0 — Agung 30x lebih mahal per detik untuk faktor 2x; dengan resep 9:1 satu Agung = 729
Rendah = 6 jam bahan bakar x1,5. **Perbaikan.** Detik per batu ikut nilai (mis. `{30, 240, 1800, 12000}`) atau tulis di
tooltip bahwa batu tinggi untuk kecepatan, bukan hemat.

### R7 — RENDAH: nama mirip

**Bukti.** `dupa` (altar leluhur, `sekte/paviliun/Dupa.java:23`) vs `dupa_arwah` (perjalanan jiwa, `dunia/dimensi/DimensiItems.java:36`);
"Esensi Abadi" (meter konversi Batu Asal, `abadi/OriginStones.java`) vs "Mutiara Esensi Abadi" (`immortal_essence_pearl`,
penghitung dunia batin, `batin/BatinItems.java:60-78`) vs "Esensi Energi Makam" (`tomb_essence`). **Perbaikan.** Altar menerima
Dupa Arwah sebagai dupa istimewa (+kepuasan ganda), dan tooltip ketiga "esensi" menyebut perbedaannya dalam satu baris
(kunci `.use` baru, en_us + id_id).

---

## Yang sudah tersambung baik (tidak perlu diubah)

- Misi Cerita Sekte Hidup memakai tabel jarahan `Expeditions.lootTable` yang sama (`sekte/misi/Kepulangan.java:202-222`), jadi
  hadiah dua sistem misi sekte konsisten.
- Lelang Abadi: titipan persisten, whitelist, nilai ≤ setengah dasar lot (`abadi/Auction.java:83-115,497-571`) — tidak ada
  lingkar beli-jual. Ini pola yang patut ditiru Balai Lelang kota (S3, T1).
- Toko kota: harga beli ≤ setengah harga jual (`Toko.java:115-121`) dan stok harian bersama — tidak ada lingkar beli-jual di
  dalam toko itu sendiri.
- Gudang/kembalian batu selalu memakai batu nyata (`NilaiBatu.pay/give`), tidak ada batu tercipta dari nol selain S1.
- Pembayaran Kenaikan dan Ritual memakai satu sumber item (`Ascend.ESSENCE_ID`), hanya bentrok dengan diskon bintang (T2).

## Urutan perbaikan yang disarankan

1. K1 (cegah hilangnya isi cincin) dan T1 (titipan persisten) — keduanya keselamatan barang, perubahan kecil di `Toko`,
   `BalaiLelang`, `Kota`.
2. T2 (sisakan 9 esensi / toggle diskon) — membuka kembali jalur Kenaikan.
3. T3, T4, S2, S4, S6 — satu lintasan penyelarasan angka di `Harga`, `Contribution`, `KotaAbadi`, `SeaPeople`, `SpiritHerbs`.
4. S1, S5, S8, S9 — konsistensi mata uang, hadiah dan istilah (lang en_us + id_id kunci identik).
5. S10/R5 dikirim ke tim Sekte Hidup; S11 ke perancang Sekte Langit.
