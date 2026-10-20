# Checklist uji Fase 10b — Tingkat Titah 1-9 dan pahat cepat

Persiapan: dunia uji dengan cheat aktif, mode Bertahan kecuali disebut lain. Rancangannya di DESIGN bagian 29.16.

Perintah ditulis singkat: `uji ...` = `/kultivasi uji ...`. Pemain uji: `uji ranah true_immortal`, lalu
`uji pusaka_langit beri`. Untuk memahat butuh `uji teknik kehancuran nyala` dan `/kultivasi kehancuran nyala`
(saklar sendiri, "Hancurkan tanah" = Ikut). **Cadangkan dunia uji dulu**: tingkat 6-9 memahat permanen.

Perintah uji baru: `uji pusaka_langit <pedang|tiang|bintang|gunung> [kekuatan] [kecepatan] [kerusakan] [tingkat]`,
`uji pusaka_langit tingkat <1-9>`, `uji pusaka_langit tingkat_tabel [titah]`,
`uji pusaka_langit pahat_cepat <jari-jari 8-512> [kedalaman 4-128] [permanen]`, `uji pusaka_langit pahat_cepat info`,
`uji pusaka_langit pahat_cepat batal`. Pemain: `/kultivasi titah tingkat <1-9> [titah]`, `/kultivasi titah ya <kode>`,
`/kultivasi titah batal`, `/kultivasi kehancuran batal`, `/kultivasi kehancuran pulihkan`.

**Selalu periksa:** tidak ada error di log, tidak ada kunci bahasa mentah (coba Bahasa Indonesia dan English), tidak ada
blok yang berubah tanpa Mode Kehancuran, tidak ada `LightningBolt`, cuaca, waktu dan tick-rate tidak berubah, tidak ada
item yang jatuh dari blok. Fase ini **belum pernah diuji di game** (hanya ditulis; belum dikompilasi).

## Daftar uji

**Menu dan gerbang**
1. Menu Titah Langit punya slider "Tingkat 1-9" di atas Kekuatan. Tetap muat di layar 320×240 (skala GUI 3).
   Nilainya tersimpan setelah relog. Dunia lama tanpa nilai tingkat terbaca sebagai Tingkat 1.
2. Batas tingkat:
   - Dewa Abadi Awal/Tengah/Akhir maksimal 3, Puncak 6, ★27+ 8, ★45+ 9;
   - tingkat terkunci tampil abu-abu dengan segel merah dan alasannya;
   - `/kultivasi titah tingkat 9` di bawah syarat dipotong ke batas dan diberi tahu;
   - config `titahMaxTier 5` membatasi semua pemain ke 5.
3. Tingkat 1 sama persis dengan sebelum fase ini: ukuran, kerusakan, jeda, kawah dan monumen blok.
4. Readout menunjukkan R, jangkauan luka, jari-jari pahat, panjang pedang/tiang dan jeda. Jeda bertambah ×√tingkat
   (tingkat 9 = ×3).

**Visual tanpa Mode Kehancuran**
5. `uji pusaka_langit pedang 1 1 100 9` tanpa Kehancuran:
   - tidak ada satu blok pun yang berubah (cek F3 dan tangkapan layar sebelum/sesudah);
   - pedang jatuh terlihat sangat besar, lalu tertancap kira-kira 960 b (Kekuatan ×4: sekitar 1,9 km);
   - tidak ada konfirmasi permanen.
6. Monumen raksasa:
   - terlihat dari jauh di luar jarak render sebagai siluet/impostor, berukuran benar, tidak berkedip saat berjalan;
   - di dekatnya model asli;
   - masih ada setelah keluar-masuk dunia dan restart;
   - pindah dimensi lalu kembali: impostor muncul lagi.
7. Cincin, sigil, garis belahan dan kisi Gunung di tingkat 6-9 tetap terlihat sebagai busur di cakrawala. Tidak ada
   lonjakan partikel.

**Kerusakan**
8. Tingkat 5 Tiang: musuh pada 200 b terkena gelombang; tingkat 1 tidak. Kerusakan per musuh tidak lebih besar dari
   tingkat 1 (bos tetap dibatasi). Hewan, penduduk desa, peliharaan dan pemain lain tidak pernah terluka.
9. Musuh di chunk yang tidak termuat tidak terkena dan tidak ada chunk yang dimuat paksa. Uji dengan render distance 4
   dan `/forceload query` sebelum/sesudah.
10. 300 zombie di area: kerusakan menyebar beberapa tick, tanpa lag besar dan tanpa spam partikel HIT.

**Pahat cepat (Mode Kehancuran nyala + Hancurkan tanah = Ikut)**
11. Tingkat 2-5:
    - kawah besar terbentuk chunk demi chunk dari tengah ke luar;
    - bar bos menunjukkan persen, lalu pesan "Kawah selesai … Pulihkan 10 mnt";
    - tidak ada blok jatuh sebagai item;
    - kawah lebur (magma/basal) hanya di lantai dan dinding, tidak di bawah pemain.
12. Pulihkan (sebelum restart, dalam 10 menit) mengembalikan tanah tingkat 2-5 sepenuhnya, termasuk lantai kawah lebur.
    Bagian yang chunk-nya tidak termuat menunggu dan bisa dipulihkan lagi.
13. Tingkat 6-9:
    - muncul pesan merah di chat dengan [Ya, hancurkan permanen] [Batal] [Turunkan ke tingkat 5];
    - tanpa klik dalam 15 detik tidak terjadi apa-apa dan qi tidak terpakai;
    - setelah "Ya": kawah tercatat permanen di log server (WARN) dan di `/kultivasi kehancuran status`;
    - Pulihkan berkata tidak bisa dipulihkan.
14. Zona terlindung di dalam kawah tetap berdiri sebagai pilar, tidak menolak seluruh titah: aula sekte, situs
    konstruksi, rumah gua, desa, pemain lain, spawn, kolom aman kastor. Kastor tidak jatuh.
15. Air dan lava:
    - tidak ada air/lava melayang atau mengalir keluar;
    - danau di dalam kawah tetap dengan dinding 1 blok;
    - tidak ada lubang tertutup di bawah blok yang dibiarkan.
16. Lantai kawah tidak pernah menembus bedrock (paling rendah minY + 6). Bedrock, obsidian, portal, spawner, peti dan
    blok `#kultivator:indestructible` tetap utuh.
17. Kawah dipangkas ke tanah termuat: render distance 6 lalu tingkat 9 → pesan "Kawah dipangkas … R {a} b". Tepi kawah
    tetap berbentuk bibir kawah, bukan tebing kotak.
18. `/kultivasi kehancuran batal` menghentikan pahat dalam satu tick. Chunk terakhir tetap konsisten (relog tidak
    menunjukkan blok hantu). Mematikan Mode Kehancuran juga menghentikannya.
19. Restart server di tengah pahat: tidak ada tiket chunk tersisa (`/forceload query`, F3 "chunks"). Dunia terbuka
    normal.

**Performa (laptop lemah, Minimal/Otomatis, render distance 8)**
20. Pahat tingkat 9: MSPT rata-rata < 45 dan pahat berhenti sendiri saat MSPT tinggi. FPS tidak turun < 30 lebih dari
    sesaat. Kawah dalam area termuat selesai < 90 detik.
21. Film Penuh tingkat 9:
    - semua potongan kamera ≤ 4 detik, total ≤ 12 detik;
    - kamera tidak bergerak lebih cepat dari biasanya (F4 diam, pedang melintas);
    - Backspace melewati;
    - dentuman datang terlambat sesuai jarak.
22. Sodium + Iris (shader pack): monumen jauh tampil sebagai siluet tanpa bayangan aneh, tidak berkedip. Chunk yang
    dipahat dimuat ulang benar di klien (tanpa lubang cahaya hitam).

**Uji perintah**
23. `uji pusaka_langit pahat_cepat 64` lalu `pahat_cepat info` menunjukkan fase, chunk, blok, ms/tick dan antrean
    cahaya. Pulihkan mengembalikannya. `pahat_cepat 256 permanen` meminta konfirmasi.
24. Teks: setiap kunci `titah_tingkat.*` dan `titah_tingkat_client.*` ada di en_us dan id_id. Semua Sumber tetap sama.
