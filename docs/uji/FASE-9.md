# Checklist uji Fase 9 — Petualangan Dewa Abadi

Persiapan: dunia uji dengan cheat aktif, mode Bertahan kecuali disebut lain, tingkat kesulitan **bukan** Peaceful. Aturan
dan resep ada di [PANDUAN 56](../PANDUAN.md#56-petualangan-dewa-abadi-fase-9), rancangannya di DESIGN bagian 26.

Perintah ditulis singkat: `uji ...` = `/kultivasi uji ...`, `debug ...` = `/kultivasi debug ...`. Bahan cepat: `uji kit`
(kotak shulker "Fase 9": 64 Batu Asal Abadi, 2 Jimat Lintas Alam, 3 Gulungan Gunung dan Laut, material purba, mutiara,
serpihan segel, Token Perluasan Tanah, 9 Esensi Makam + 1 Batu Roh Agung, Mata Lilin Zhulong). Pemain uji:
`uji ranah true_immortal` (Dewa Abadi Puncak) lalu `uji bintang 9`. Tanpa syarat apa pun: `uji abadi masuk`.

**Selalu periksa:** tidak ada error di log, tidak ada kunci bahasa mentah (coba Bahasa Indonesia dan English), tidak ada
blok yang berubah oleh fitur fase 9, tidak ada `LightningBolt`, cuaca, waktu dan tick-rate tidak berubah, penduduk desa,
hewan, peliharaan dan pemain kreatif tidak pernah diserang. Seluruh Fase 9 **belum pernah diuji di game** (hanya
dikompilasi).

## A. Alam Abadi
1. Memuat dunia: layar "Experimental settings" **tidak** muncul.
2. `uji abadi naik`: langit lima warna, 9 sambaran, dua gelombang utusan, pusaran 15 detik. Keluar lingkaran 8 blok =
   gagal. Sambaran yang akan membunuh = gagal, bukan mati, Totem tidak terpakai. Utusan yang tersisa hilang saat ritual
   selesai. `uji abadi naik gagal`: Luka Kenaikan.
3. Berhasil: tiba di pulau kedatangan `kultivator:alam_abadi` (pulau melayang, tanpa hujan, struktur, spawner atau monster
   alami), mendapat 1 jimat + 16 Batu, kartu judul "ALAM ABADI". `uji abadi pulau` menampilkan titiknya.
4. Jimat Lintas Alam: channel 5 detik, pulang ke titik asal, lalu kembali ke posisi terakhir. Pemain yang belum naik
   ditolak. Murid Inti+ (maks. 3) ikut; murid yang tertinggal tersapu pulang saat pemain keluar lewat portal, mati, atau
   keluar game.
5. `uji abadi tepi`: didorong balik di batas. `uji abadi void`: kembali ke tanah terakhir dengan −10% HP, tidak mati.
   Keluar game di Alam Abadi lalu masuk lagi: tetap di sana dengan aman. Mati di Alam Abadi: respawn normal di dunia biasa.
6. Menambang batu dengan tangan di Alam Abadi: kadang Batu Asal Abadi, maks. 16 per hari. `grep setBlock` di paket
   `abadi`, `purba`, `batin` kosong.
7. `uji abadi konversi 0` lalu `100`: kerusakan ke makhluk Alam Abadi naik ×0,6 → ×1,0. Meditasi di sana XP ×3.
   Memadatkan bintang dengan 10 Batu di tas: biaya −10% dan Batu terpakai.
8. `uji abadi pribumi laba|tulang|babi|penjaga|liar [3]`: Prajurit Tulang tidak terbakar siang hari dan helmnya tidak
   jatuh; Babi Taring Emas tidak jadi zoglin. Alami: ≤ 4 di sekitar, ≤ 12 di dunia, tidak di atas kehampaan.

## B. Binatang Purba
9. `uji purba kunpeng`: Kun muncul dengan gelombang bertelegraf, Peng menukik, angin puyuh menarik tanpa merusak blok,
   memecah Mata Angin membuka celah. `debug purba info` menunjukkan fase dan celah.
10. `uji purba zhulong`: Mata Tertutup memberi Darkness, jam dunia tidak berubah. Napas dingin membekukan, napas panas
    hanya membakar entitas.
11. `uji purba taotie`: Pelahap yang mencapai kepala menyembuhkannya; teknik saat mulut terbuka menyembuhkan setengahnya.
12. `uji purba enam` lalu bunuh Kun-Peng: `kunpeng_art` dipelajari dan pecahan habis (jika sudah tahu, pecahan tetap).
13. Pukulan mematikan dari binatang: 1 HP dengan Luka Purba. Setelah 3 hadiah ulang sehari, hanya material 50%
    (`uji purba reset` untuk mengulang). Resep gulungan: 7 Batu + buku + material menghasilkan jenis yang benar.

## C. Bencana dan Murka
14. Pada ★5 kualitas Normal, memadatkan ★6 berhenti dengan "Bencana ke-1 menanti". `uji bencana 1 lulus` lalu ★6
    terpadatkan. `uji bencana 1 gagal`: Luka Bencana dan 50% biaya kembali. Segel Penangkal (3 serpihan): sambaran ×0,85.
15. Dunia lama dengan ★20: bencana 1–2 "Terlewati", bintang tidak berkurang, bisa diambil untuk faktor T.
16. `uji murka`: tiga gelombang utusan lalu Panglima. Murid dan formasi tidak melukai Panglima. `uji murka kalah`: 1 HP
    dan Luka Langit 10 menit. `uji murka jadwal` mencetak hari murka berikutnya.
17. Murka tidak terjadi saat AFK, Peaceful, serbuan, alam rahasia atau kesengsaraan; 2 kali terlewat tanpa hukuman.

## D. Dunia Batin
18. `uji batin lahir besar`: 9 petak. Tanam herba lalu `uji batin hari 4`: umur +100 (+ Kesuburan). Panen umur ≥ 1.000
    memberi herba kedua umur 100.
19. Manusia Batu: setor 64 bijih sekali, lalu bijih jadi batu roh tiap hari. Ancient debris ditolak; bijih tidak bisa
    diambil lagi.
20. `uji batin bencana meteor`: meteor bertelegraf bisa dihancurkan di udara; yang mendarat membakar petak, bukan blok.
21. `uji batin bencana petir`: Bayangan Petir menerima ×2 di lingkaran Gunung Dang Hun. `uji batin bencana data`: peluang
    sesuai Perkokoh, kalah hanya separuh kerugian.

## E. Jurus Ciptaan
22. `uji ciptaan 1 api es ledakan 1.0`: "Jurus Ciptaan I" dengan nama sendiri di Kitab dan bilah aksi. Kerusakan lewat
    batas bos. `uji teknik audit` tetap lulus.
23. Nama dengan `§` atau > 24 karakter ditolak. `/kultivasi ciptaan hapus 1` mengosongkan slot loadout dan penguasaan.
    Ritual yang terputus mengembalikan semua biaya.

## F. Lelang
24. `uji lelang sesi` (di Alam Abadi): 5 barang, saingan menawar tiap 4 detik, terjual setelah 8 detik tanpa tawaran.
    Batu diambil hanya saat menang. Klik dengan harga basi ditolak. Menang tanpa Batu cukup: barang ke saingan, pemain
    tidak bisa menawar sampai sesi selesai.
25. Titipan terjual ≤ 50% harga dasar lot. `uji lelang kejar`: kawanan kultivator liar dengan peringatan 3 detik.

## G. Jalan, kewajiban, keamanan
26. `uji jalan bab 11` … `15`: langkah terisi dari pencapaian, hadiah diklaim sekali. Kewajiban harian baru (makhluk
    Alam Abadi, tambang Batu, panen batin, menawar) maju. Topik bantuan baru muncul di tablet Bantuan.
27. Pertarungan purba + 6 makhluk Alam Abadi: server tetap dekat 20 TPS di laptop; tidak ada chunk dipaksa termuat.
28. Kode server tidak merujuk kelas client (server khusus menyala tanpa error).
