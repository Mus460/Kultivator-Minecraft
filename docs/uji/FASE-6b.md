# Checklist uji Fase 6b — Pembangunan Sekte

Persiapan: dunia uji baru (Kreatif, cheat aktif, dataran luas atau Superflat), kamu operator, bahasa Indonesia, jarak
pandang ≥ 8 untuk Templat 7–9. Pasang Prasasti Aula Sekte (`/give @s kultivator:sect_hall_stele`) di tanah datar. Aturan
dan angka ada di [PANDUAN 20b](../PANDUAN.md#20b-pembangunan-sekte), rancangannya di DESIGN bagian 21.
Perintah pemain: `/kultivasi aula bangun ...`; debug: `/kultivasi debug aula bangun ...`; uji: `/kultivasi uji sekte ...`
(di bawah ditulis singkat `uji sekte ...`).

**Mempercepat:** `uji sekte sumber penuh` mengisi Gudang Bahan Sekte (sisa tagihan + 10% dan semua barang khusus).
`uji sekte tingkat <n> instan` = 512 langkah per tick dengan satu upacara di akhir; `cepat` = ×20 dengan semua upacara;
`uji sekte kecepatan <1-50>` mengalikan laju sesi. Murid uji: `/kultivasi debug murid summon`. Durasi ekspedisi tidak ikut
`/time add`; pakai `uji sekte misi selesai` atau `/tick sprint`.

**Selalu periksa di setiap poin:** tidak ada blok yang berubah di luar tapak (cek koordinat tepi dengan F3), tidak ada api,
petir, atau ledakan dari mod, dan tidak ada spam error di log.

## A. Inti (DESIGN 21.25)
1. **Bangun instan dan pulihkan.** `uji sekte templat 3` → [Konfirmasi] → [Ya, mulai bangun] → `uji sekte tingkat 9
   instan`. Selesai dalam beberapa detik dengan upacara akhir (pilar emas, 9 lonceng, kelopak, toast, judul "Sekte … ·
   Tingkat 9"); Tingkat Aula = min(tingkat ketenaran, 9). `uji sekte reset` memulihkan tanah persis seperti semula,
   sedikit demi sedikit tiap tick tanpa lag besar; templat kosong setelah log undo habis.
2. **Alur nyata.** Tablet Bangun → *Pilih Templat* → Templat 2, glasir Giok → *Lihat Tapak*: dalam 1–2 detik hologram
   (tirai giok setinggi 3 blok, tiang emas di sudut, poros emas berlabel "Gerbang", kotak per tingkat dengan nama
   Indonesia), chat "Pratinjau Tapak · … [Putar] [Konfirmasi] [Batal]", HUD "Tapak terpindai %". Esc lalu berjalan
   keliling: hologram dan HUD bertahan 2 menit. [Putar] memindah gerbang. Centang "Aku paham", tahan segel giok 3 detik →
   Batu Pertama (garis tapak berkilat, lonceng), murid di wilayah membungkuk, pencapaian *Batu Pertama*, baris Catatan.
   `uji sekte sumber 200` → T1 tumbuh sekitar 9 blok/detik bergelombang tiap 4 tick dengan suara dan percik; pasangan
   cermin tumbuh simetris; tiap bangunan naik dari lantai, tiang, lalu atap (cincin tepi atap dulu, bubungan terakhir).
3. **Blok buatan pemain dilindungi.** Papan kayu di dalam kotak bagian → pratinjau memperingatkan "kolom buatan pemain";
   saat dibangun: X merah, "Terhalang", chat dengan [Periksa ulang] [Lewati bagian ini] [Selesaikan tanpa blok terhalang],
   antrean jalan terus. Singkirkan papan → bagian lanjut (pemeriksaan ulang tiap 30 detik atau [Periksa ulang]). Coba juga
   kedua tombol lainnya.
4. **Cairan.** Dirikan di samping kolam alami: tepi galian menjadi dinding penahan, air tidak membanjir. Kolam rencana
   terisi air diam; air terjun jatuh ke kolamnya sendiri dan tidak bocor.
5. **Pengembalian.** Mode Bertahan: pecahkan lentera sekte → tidak ada item, actionbar "+1 Logam & Pelita kembali ke Gudang
   Bahan Sekte", kepulan giok. TNT di dekat tembok (`/summon tnt ~ ~ ~`) → tanpa drop, Gudang dikreditkan.
   `aula bangun perbaiki` mengisi sel yang hilang karena ledakan dan melewati pintu yang kamu buka sendiri.
6. **Jeda dan tabungan.** Jalan 200 blok menjauh saat sebuah bagian sedang dibangun → "Jeda: pemilik jauh", tabungan
   bertambah (`uji sekte info`). Kembali → baris laju "×2 tabungan". Alt-tab di pemain tunggal → tidak ada yang dibangun
   dan tidak ada yang ditabung.
7. **Misi dan ekspedisi.** Baris Misi Tingkat sama dengan tagihan tingkat itu; `uji sekte misi` → tawaran sesuai
   kekurangan; kirim 3 murid ekspedisi Batu lalu `uji sekte misi selesai` → unit, PK, dan bagian bar datang.
8. **Pekerja.** 2 pekerja berjalan ke bagian yang dibangun, mengayun dengan debu dan benang qi emas; baris laju naik 50%.
9. **Integrasi aula.** Aula yang sudah terpindai sampai batas 6 (Paviliun Kitab, Formasi Agung, sasaran dibangun sendiri;
   cek `/kultivasi debug aula level`) mengonfirmasi templat → Tingkat Aula tidak turun. Fitur aula mengikuti tingkat
   (Paviliun T3, Aula Latihan T7, Menara T9). Cakram T5 terdaftar dengan serpihan kecubung → Formasi Agung. Ruang menara T9
   terhitung lantai (`/kultivasi aula info`).
10. **Naik templat.** `uji sekte naik 5` pada Templat 3 yang selesai → bangunan lama tetap berdiri sampai tempatnya dipakai,
    Tingkat Aula tidak turun.
11. **Pameran.** `uji sekte pamer 9` di tanah datar → `uji sekte pamer konfirmasi` → `uji sekte pamer hapus` memulihkan tanah
    persis (bertahap).
12. **Audit.** `uji sekte audit` (laporan menyusul tiap 2 detik): 81 emisi tanpa pelanggaran (0 di luar, 0 tak dikenal atau
    terlarang, 0 bentuk tangga, 0 "asymmetric bays", jangkar dalam jangkauan; "air terbuka" hanya kolom air terjun);
    `uji sekte bandingkan` menampilkan 9 tapak emas bersarang; `uji sekte info 9` tampil.

## B. Mendirikan dan pratinjau
13. Pasang Prasasti pertama: chat sekali "[Pilih Templat Sekte] [Bangun Sendiri]". Tanpa aula, `uji sekte templat 1`
    memberi sebuah Prasasti dan petunjuk.
14. Layar Templat: 9 plakat giok dengan sketsa tinta; tombol 1–9 memilih; manik di bawah diagram atau gulir di diagram
    mengubah tingkat yang tampil; koin Tinta/Giok/Langit; angka tapak, tinggi, bagian, tagihan, barang khusus, waktu,
    bonus, ranah. Teks muat di 320 × 240 (GUI Scale maksimum).
15. Jalur chat: `/kultivasi aula bangun pratinjau 3 timur tinta` (gerbang menghadap timur) → `aula bangun konfirmasi` →
    baris persetujuan + [Ya, mulai bangun]. Sebelum blok pertama: `aula bangun ganti` membuka layar Templat lagi tanpa
    biaya; sesudahnya ditolak dengan petunjuk Naik/Bongkar/Pindah/Lepaskan.
16. Medan: di lereng bukit `uji sekte periksa 3` → peringatan Indonesia (selisih tinggi, gali/timbun, air, pohon), tanpa
    mendirikan. `uji sekte templat 3 utara giok paksa` → merah kecuraman/tanah/air menjadi kuning. Setelah dibangun: dinding
    penahan batu rapi, landai rumput/lumut 1 : 1 di tepi plot, tidak ada dinding tanah telanjang, tidak ada air keluar
    kolam.
17. Penghalang di pratinjau: peti berisi barang dan papan buatanmu di petak bangunan → "wadah berisi barang" (merah; kuning
    bila *Kosongkan* aktif) dan "kolom buatan pemain" (kuning). Tapak di atas tambang terbengkalai (spawner/peti 40 blok di
    bawah) tetap bisa dikonfirmasi.
18. Tapak terpindai < 100% → konfirmasi mati, HUD menunjuk arah bagian yang belum terpindai. `sectBuildMaxTemplate` = 3 di
    `config/kultivator.json` → Templat 4 merah "Server membatasi templat…".

## C. Setor, Gudang, dan pembangunan otomatis
19. Pegang 64 cobblestone, jongkok + klik kanan Prasasti → actionbar "+64 Batu · … (tingkat ini)", butir mengalir ke
    Prasasti. Klik kanan biasa tetap membuka aula; jongkok tangan kosong tetap "pindahkan aula". Berlian (Emas & Giok)
    diterima; pedang atau tongkat tidak diambil.
20. Panel Bahan: batang bambu per kategori (tingkat ini, garis tipis tingkat berikutnya), barang khusus dengan ikon
    punya/butuh, kisi tas per kategori; klik = satu tumpuk, shift-klik = semua; *Yang dibutuhkan* hanya mengambil
    kekurangan; *Ambil dari Lumbung* memindah dari Lumbung Sekte.
21. Tanpa bahan: Ikhtisar "‹bagian› masih kurang:" dengan batang, HUD dekat Prasasti "Masih kurang: 32 Batu, …", chat
    (paling sering tiap 10 menit) "Menunggu bahan: … [Setor yang dibutuhkan]". Setor cukup → bagian berikutnya mulai sendiri
    ("Mulai dibangun: …") tanpa tombol apa pun.
22. Barang khusus: rak buku diterima hanya sebanyak yang masih dibutuhkan templat; sisanya "Templat tidak butuh … lagi".
23. Panel Galian: Peti Galian satu tumpuk per klik; sisa Batu/Tanah/Kayu di atas seluruh sisa tagihan bisa diambil sebagai
    cobblestone/tanah/papan ek; koin *Pakai hasil galian* mati → hasil galian masuk Peti Galian sebagai barang.
24. Bijih berlian di dalam galian → masuk Peti Galian sebagai item, bukan unit Emas & Giok.
25. Tapak berhutan: kanopi dibersihkan dulu, lalu batangnya dikreditkan ke Gudang (tidak tersisa tunggul, bagian tidak
    menunggu).
26. **Tidak ada bahan gratis.** Satu bagian terhalang satu papan; tunggu 2 menit: Gudang **tidak** bertambah terus. Pecahkan
    satu blok sekte (dapat refund), tunggu 1 menit: blok itu **tidak** dipasang lagi dan tidak ada refund kedua.
27. **Hutang reservasi.** Lewati bagian yang tertunda (chunk tidak termuat) atau yang menunggu → Gudang kembali tepat sebesar
    bagian yang belum dipasang. Naik Templat dan Bongkar mengembalikan semua reservasi yang tertahan.
28. **Simpan dan muat.** Keluar ke menu saat sebuah bagian setengah jadi (`uji sekte tingkat 9`), masuk lagi → bagian lanjut
    dari tempat yang sama, sel terhalang tetap tercatat, tidak ada blok ganda atau hilang, Gudang tidak berubah.
29. **Jarak dan chunk.** Terbang lebih dari 160 blok dari Prasasti → pembangunan berhenti, lanjut saat kembali. Pergi lebih
    jauh dari jarak pandang (masih dalam 160 blok) sampai chunk Prasasti tidak termuat → status "Jeda: Prasasti tidak di
    tempatnya", tidak ada yang dibangun.

## D. Misi, ekspedisi, dan pekerja
30. Panel Misi: "Misi Tingkat n (dibayar sekali)" dengan batang dan PK per baris; baris yang tertutup → chat "Misi Tingkat
    selesai: … +PK ke kas sekte"; tingkat berikutnya tampil "Persiapan".
31. `uji sekte misi` → 3–5 tawaran. *Terima* Setoran, setor kategori itu → batang maju; selesai → "+PK ke kas sekte ·
    Berkah Pengrajin +X". PK harian dari misi pembangunan paling banyak 150. Ambil surplus Batu lalu setor kembali → tawaran
    **tidak** selesai karenanya. Ekspedisi Berkah: hasil kategori itu ×1,5 hari ini.
32. Ekspedisi Pengumpulan: 2–3 murid bebas di dekat aula → pilih kategori Batu, 3 murid, *Kirim regu* → murid menghilang
    dengan kabut. Kategori Genteng butuh Murid Luar, Logam juga Pemurnian Qi, Emas & Giok butuh Murid Dalam.
    `uji sekte misi selesai` → murid kembali di samping Prasasti, chat hasil, bahan bertambah, pencapaian *Ekspedisi Bahan
    Pertama*. Selama pergi, Jenjang menulis "mengumpulkan bahan".
33. Pekerja: panel Pekerja (klik murid bebas, maks 4) atau `uji sekte pekerja`. Saat "Berjalan" mereka ke titik kerja dan
    mengayun; macet 10 detik → melompat dengan kabut. Saat menunggu bahan mereka bermeditasi di pelataran (cincin sekitar 3
    blok dari Prasasti). 4 pekerja → pencapaian *Seribu Tangan*.

## E. Keamanan blok dan pengembalian
34. Peti berisi barang dan papan buatanmu di petak bangunan: setelah dibangun peti dan papan tetap utuh, bangunan yang
    terkena menunggu (`uji sekte info`).
35. Peti berisi barang (dan kotak shulker) di sel air kolam sekte, lalu dipecah → pecah cara vanilla, isi utuh, Gudang tidak
    berubah. Prismarine biasa di kolam lalu dipecah → drop vanilla, tidak ada unit Emas & Giok.
36. Tangga kayu terendam buatan pemain di tepi kolam tidak diganti batu. Pemain yang berenang di sel yang akan disegel tidak
    terkurung (bagian menunggu).
37. Wind charge dan Wind Burst gada ke dinding sekte → tidak ada blok yang hilang.
38. Berdiri di area kerja → sel itu menunggu; setelah 10 kali "Keluarlah dari area kerja". Tidak ada makhluk yang
    terkurung atau jatuh. Armor stand atau bingkai di jalan → dihitung terhalang setelah 10 kali.
39. Spawn: `/time set midnight`, `/difficulty normal`, tunggu di pelataran → tidak ada zombi yang muncul di atas sel sekte,
    tetapi tetap muncul di luar tapak.
40. Mode Kehancuran (bila diizinkan): jurus penghancur yang bentuknya menyentuh tapak + 16 blok ditolak.

## F. Naik, bongkar, pindah, lepaskan
41. `aula bangun naik 5` → pratinjau naik (tanpa Putar) → konfirmasi → chat "Tingkat n dijamin…". Saat rencana baru
    mencapai tingkat lama → "Naik Templat selesai" dan pencapaian *Melampaui Tapak Lama*.
42. Bongkar: koin *Bongkar* dan *Lepaskan* butuh dua klik (bingkai berdenyut merah). `aula bangun bongkar bangunan` →
    [Ya, bongkar] → urutan terbalik dengan refund, bentuk lahan tetap; `bongkar total` juga lantai batu dan tembok; tingkat
    turun bertahap; di akhir aula kembali memindai.
43. Pindah: Prasasti kedua 20 blok jauhnya, jongkok-klik tangan kosong → dialog "[Pindah & bongkar] [Pindah & tinggalkan]
    [Batal]". *Tinggalkan* → aula pindah, layar Templat terbuka untuk tapak baru, tingkat lama dijamin 10 hari, blok lama
    pecah biasa tanpa refund.
44. *Pindah & bongkar*, dirikan templat baru, restart → tapak lama tetap dibongkar saat kamu dekat; tapak baru tidak
    terganggu (pecahkan blok tapak baru → refund ke Gudangmu).
45. `aula bangun lepaskan` → [Ya, lepaskan templat] dalam 30 detik → bangunan tetap berdiri, aula kembali memindai.
46. Templat 9 selesai, `aula bangun perbaiki` → "Memeriksa N bagian…", server tidak membeku, hasil datang beberapa detik
    kemudian.
47. Tambang Prasasti → aula tertidur, pembangunan berhenti dengan pesan; pasang lagi di sel yang sama → lanjut.

## G. Integrasi aula
48. `/kultivasi aula info` dan tablet Aula: baris "Sekte Kelas Dua · Tingkat n/9 · ‹status›" menggantikan daftar centang.
    `/kultivasi debug aula bangun tingkat-set 7` lalu `/kultivasi debug aula level` → batas bangunan = max(batas beku, 7);
    `uji sekte info` menulis batas beku dan tingkat dijamin.
49. Radius wilayah = max(20 + 4L, radius templat); batas murid +1 untuk Templat 5–9 di T9; +1 calon untuk Templat 6–9 sejak
    T7; calon datang di poros di depan Prasasti.
50. Pos tugas: setelah T2, beri murid tugas ternak → ia berdiri di depan kandang Ayam Ekor Roh (Templat 4 sekitar (−23, 6),
    Templat 5 (31, 4), Templat 6 (−28, −7) dari Prasasti), bukan di sel 5 blok.

## H. LAN
51. Buka ke LAN, pemain kedua bergabung dengan `/sekte`, klik Prasasti pemilik → tablet Bangun baca saja (tombol pemilik
    mati, setor aktif). Setor → terima kasih di chat sekte (maks 1×/menit), panel Penyumbang muncul, pencapaian *Penyumbang
    Sekte*. Kehadirannya dalam 160 blok menjalankan pembangunan.
52. Nyalakan *Bongkar & simpan* di pemain tunggal (tanpa op), lalu buka ke LAN → izin itu tidak berlaku lagi. Pemain kedua
    memecah blok sekte → refund ke Gudang pemilik, Catatan "… dibongkar oleh ‹pemain›".

## I. Perintah uji
53. `uji sekte pamer 3 tingkat 9 utara` → `pamer konfirmasi` → `pamer daftar` → `pamer hapus` mengembalikan tanah persis.
    Pameran ketiga ditolak "Paling banyak 2 pameran sekaligus".
54. `uji sekte hologram 3 6` → tata letak sampai Tingkat 6, tidak bisa dikonfirmasi.
55. `uji sekte info` (tapakmu: status, Gudang, batas) dan `uji sekte info 2` (bagian, blok, langkah, menit, tagihan per
    tingkat).
56. `uji sekte tingkat 3` lalu `uji sekte tingkat 6 cepat` → Tingkat 4–9 dibongkar lalu dibangun ulang; tiap tingkat tampak
    selesai, tidak ada atap setengah jadi.
57. `uji sekte kamera 1`, `2`, `3` → gerbang, taman/air ke titik menjulang, udara dari tenggara (tapakmu atau pameran
    terdekat).
58. `uji sekte upacara 5`; `uji sekte kecepatan 10`; `/kultivasi debug aula bangun bank 24000`, `komponen selesai`,
    `rencana ulang`.

## J. Keindahan per templat
Bangun dengan `uji sekte pamer <t> tingkat 9` + `pamer konfirmasi` (atau `templat` + `tingkat 9 instan`), siang dan malam.
59. **Setiap tingkat tampak selesai:** untuk t = 1..8 jalankan `pamer hapus semua`, `pamer 6 tingkat <t>`, `pamer
    konfirmasi`. Tidak ada tangga menggantung tanpa teras, lubang di dek, atau tanaman melayang (Tingkat 5 menampilkan
    sungai jurang di lahan belakang yang masih alami). Ulangi acak untuk templat lain.
60. **Templat 1:** atap melengkung dengan ujung sudut terangkat, bubungan berlampu froglight, lentera, Paviliun Kitab (14 rak
    + podium), cakram Bagua, sasaran, ruang bawah Langit Membara. Pohon prem tetap di lorong antara Paviliun Kitab dan
    dapur, tidak menembus atap; dua ding kecil di samping tangga aula; cakram dan poros bersih.
61. **Templat 2:** teras belakang +3, aula utama di poros, paifang kayu merah; pagoda meruncing (lantai 1 lebar); kuil
    leluhur di samping pagoda menghadap timur; sasaran di halaman pelataran; paviliun lonceng di halaman timur; sumur
    berkerangka.
62. **Templat 3:** serambi beratap kiri-kanan, kolam teratai dengan paviliun tepi air, gerbang gunung, menara sudut; dinding
    kepala kuda menjulang di atas bubungan; tembok T4 sampai ke sudut; paifang T6 4 blok di depan atap aula; 24 rak dan
    tangga ke lantai atas bisa dipanjat; dapur tidak menempel ke atap asrama.
63. **Templat 4:** tembok merah menempel ke gerbang, 4 menara jaga xieshan; tangga tengah selebar 7 dengan 3 bordes ke aula
    utama atap ganda di +8; teras atas lebih lebar dari aula utama; air terjun kecil ke kolam teratai; lonceng di (14, 13)
    dan genderang di (−14, 13) utuh; pagoda T8 berlantai, atap lebar, lebih rendah dari aula utama, tidak menabrak asrama
    dalam.
64. **Templat 5:** teras marmer +2/+4/+5 dengan tangga naga; dua ding di dek +2 (satu berasap tinggi di atas jerami);
    pancuran air dari dinding teras +2 ke kolam barat; Pagoda Kaca Tujuh Harta (Giok, dinding putih) di pelataran barat laut
    +3; serambi beratap di kedua sisi pelataran; Paviliun Kitab dan asrama di plotnya; menara lonceng/genderang di halaman
    luar; paviliun menara Langit Membara terlihat; pohon prem di tepi kolam; bambu di belakang aula berdiri di rumput (tidak
    rontok bila blok sebelahnya dipukul).
65. **Templat 6:** Puncak Dalam di barat laut berupa gunung dengan tebing di sisi jurang (timur) dan lereng berhutan; Aula
    Murid Dalam 2 lantai dengan kursi guru dan 3 bantal merah; jembatan galeri beratap naik dari teras +6; air terjun dari
    tebing ke kolam kecil; teras lautan awan di timur laut dengan paviliun dua atap, dek kayu bertiang tinggi, paviliun yang
    benar-benar segi enam di luar tebing, dan jembatan langit ke teras +9; trigram cakram utuh. Paviliun Kitab 48 rak dan 2
    podium (T3); panggung laga + 10 sasaran (T7); menara 6 ruang (T9).
66. **Templat 7:** tangga pelataran → halaman luar tersambung pada kedalaman berapa pun (uji di lereng dengan `tingkat 1
    instan`); serambi barat tidak terkubur bukit gua; bukit Gua Surgawi dengan 6 ruang menara; batu melayang berantai dengan
    kepala kura-kura.
67. **Templat 8** (`pamer 8 tingkat 1`, lalu 4, 6, 9): T1 kompleks kecil rapi (Aula Pendiri, 2 asrama, chuihuamen, 8
    gentong, pinus pusaka). Lengkung menara gerbang 3 lantai terbuka dengan lentera gantung, 2 gerbang samping, tanjakan
    kuda ke jalan di atas tembok, parit berair tanpa bocor dengan 3 jembatan. Teras marmer +3 → tangga naga ke +9; aula
    utama 9 ruang di depan, Aula Tengah beratap piramida di tengah, Aula Leluhur Agung di belakang (T8). Pagoda Kaca
    Sembilan Harta di timur laut; taman kekaisaran di barat laut (danau, paviliun pulau dengan kursi Qi tingkat 3, bukit
    batu dengan paviliun puncak). `uji sekte info 8`: 48 bagian.
68. **Templat 9** (`pamer 9 tingkat 9`): tangga sembilan tahap dari batu penghalang ke aula utama +13; jalan di belakang ke
    halaman rumput Aula Leluhur Agung; tangga marmer ke puncak +18 dan pagoda (ujung sekitar +96). Masuk pagoda di lantai 4
    lewat jembatan langit dari pulau besar (Aula Dao Langit biru walau glasir Tinta). 2 jembatan galeri dari Puncak Dalam
    dan Puncak Tetua, 3 jembatan lengkung di atas sungai, air terjun dari Puncak Awan Harum ke danau dan dari Puncak Dalam ke
    sungai, tanpa bocor. `uji sekte info 9`: 50 bagian.
69. **Menara Langit Membara** Templat 8 dan 9: paviliun di sisi timur pelataran, tangga spiral tanpa pintu, 7 ruang dengan
    api unggun jiwa menyala di atas crying obsidian; `aula info` menghitung menara (7 lantai).
70. **Glasir:** `uji sekte templat 8 utara giok` dan `uji sekte templat 9 selatan langit`, lalu `tingkat 9 instan`: atap aula
    utama mengikuti glasir, Pagoda Kaca tetap Giok, Aula Dao Langit tetap biru; pagoda biasa (Templat 2–4) mengikuti glasir;
    di Tinta atap pangkat C/D bertepi kuning dan tampak lebih tinggi pangkatnya dari atap bertepi giok.
71. **Rotasi:** `uji sekte pamer 5 tingkat 9 timur` → trigram Bagua tetap menurut arah kompas sebenarnya, papan nama benar,
    air terjun masuk kolam, tidak ada blok di luar tapak.
72. **Lereng:** dunia normal berbukit, `uji sekte templat 9` + `uji sekte tingkat 9 cepat` → tangga pelataran ke halaman luar
    dan gerbang gunung ke jalan masuk tersambung tanpa lubang.
73. **Malam:** lentera menandai poros dan tepi atap, jendela bercahaya, tidak ada monster di atas blok sekte, daun pohon sekte
    tidak membusuk. `uji sekte kamera 1/2/3` memberi tiga sudut pandang yang enak di setiap templat.

## K. Performa dan tampilan
74. `uji sekte kecepatan 50` saat membangun Templat 3: MSPT rata-rata (F3) tetap di bawah 40 ms, laju turun sendiri saat
    server berat, status "Jeda: server sibuk" bila ≥ 40.
75. Kualitas Efek Minimal: kabut kerja dan lautan awan mati, partikel berkurang. F1 menyembunyikan HUD pembangunan. Lautan
    awan tampil di lembah Templat 6–9 sejak Tingkat 8.

## L. Sumber, teks, dan pencapaian
76. Tooltip templat, topik Bantuan "Pembangunan Sekte", dan pesan upacara memuat baris **Sumber** yang sama dengan DESIGN 21
    (lihat PANDUAN 20b), tanpa kanon baru.
77. Ganti bahasa ke English lalu Indonesia: tidak ada kunci mentah (`kultivator.sectbuild...`, `kultivator.blueprint...`) di
    tablet, layar Templat, chat, HUD, label hologram, atau pencapaian; tanda % tampil benar.
78. 10 pencapaian baru di tab Kultivasi: Batu Pertama, Formasi Agung Berdiri, Sekte Lengkap, Pagoda Kaca, Sekte Agung
    Berdiri, Istana Langit, Melampaui Tapak Lama, Seribu Tangan, Ekspedisi Bahan Pertama, Penyumbang Sekte. Pameran dan tapak
    uji tidak memberi pencapaian atau baris server.

## Belum ada / berbeda dari rancangan
* **Belum ada:** penyeimbangan tagihan. Rencana sekarang sekitar 60–75% dari sasaran DESIGN 21.11, dan beberapa tingkat jauh
  dari sasarannya.
* **Belum ada:** "Bongkar sisa lama" setelah Naik Templat (bangunan lama yang tidak bertabrakan tetap berdiri), dan baris
  "Dipertahankan N · Dibongkar M · Biaya ±X" di layar naik templat.
* **Belum ada:** ganti glasir setelah blok pertama (sekarang hanya lewat Ganti Templat sebelum blok pertama).
* **Belum ada:** X biru untuk air yang akan ditimbun di hologram; kabut pulau di sekitar pulau melayang; sketsa templat
  yang sebenarnya sebelum pratinjau (sekarang sketsa perkiraan per gaya).
* **Belum ada:** regu Ekspedisi Pengumpulan di daftar "murid yang sedang pergi" tablet Aula; jimat "Pilih Templat" di tablet
  Aula; tampilan palu untuk pekerja.
* **Belum ada:** pemeriksaan audit garis pandang, kepadatan, bingkai pemandangan, dan sel gelap; anggaran warna hanya
  dilaporkan. Sudut pandang kamera khusus per templat (sekarang tiga sudut tetap).
* **Berbeda:** desa dikenali lewat bangunan dunia per chunk; block entity dan rongga hanya dicari di dekat permukaan.
  Gelombang pemasangan tidak dikelompokkan per bagian 16³.
* **Berbeda:** serambi samping Templat 2 dan serambi penghubung Templat 3 tidak ada; Templat 3 memakai rangka lonceng dan
  genderang; jembatan batu pijakan Templat 5 lurus dan beberapa letak Templat 5 bergeser; Templat 8 menaruh aula utama dan
  Aula Tengah di teras T6 dan Aula Leluhur Agung sebagai aula ketiga di T8; Templat 9 memakai dua dinding penghalang di
  kiri-kanan gerbang gunung, tiga jembatan lengkung bulan, dan ketinggian puncak tetap.
* **Berbeda (nama):** di game Templat 7 dan 8 bernama "Gua Surgawi, Tanah Berkah" dan "Garis Abadi Kuno" (DESIGN: "Gua Surga
  Tanah Berkah", "Warisan Kuno Abadi").
* **Belum diuji di dalam game:** semua poin di atas baru dikompilasi dan diuji kering di luar game.
