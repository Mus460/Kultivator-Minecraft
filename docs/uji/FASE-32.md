# Checklist uji Fase 32 — Film Kosmik 3D

Persiapan: dunia uji dengan cheat aktif, Kamera sinematik **Spektakuler**, "Film kosmik 3D: Penuh", kualitas Normal.
Rancangannya di DESIGN bagian 32. Pratinjau: `/sinematik kosmik3d orbit|surga [detik] [tingkat]`,
`/sinematik kosmik3d beat <orbit|surga> <beat> [detik] [tingkat]`, `/sinematik kosmik3d info`,
`/sinematik film titah_pedang|titah_tiang|titah_bintang|titah_gunung [act n]`, `/sinematik pusaka film <skill> [detik] [tingkat]`.
Pratinjau panggung paling lama 20 detik (batas kamera 400 tick per adegan).

**Selalu periksa:** tidak ada error di log, tidak ada kunci bahasa mentah (coba Bahasa Indonesia dan English), tidak ada
blok yang berubah, tidak ada `LightningBolt`, cuaca, waktu dan tick-rate tidak berubah; setelah setiap film kamera, FOV dan
roll kembali normal.

**Dasar (Kamera Spektakuler, "Film kosmik 3D: Penuh", kualitas Normal)**
1. `/sinematik kosmik3d orbit`: layar masuk ke angkasa lewat celup hitam; terlihat planet 3D bulat dengan laut, benua,
   es kutub, awan yang berputar sedikit lebih cepat, cahaya tepi atmosfer, sisi malam dengan titik lampu kota, bulan
   berkawah, matahari dengan korona, bintang dan pita Bima Sakti. Tidak ada tepi chunk, langit vanilla, awan vanilla,
   hujan atau air yang menembus.
2. Biome warna: uji di hutan, gurun dan salju. Daratan di sekitar "rumah" di planet mengikuti warna rumput dan air biome
   setempat (setelah ±0,5 detik pemanggangan); di Nether/End planet memakai warna bawaan.
3. `/sinematik kosmik3d surga`: Gerbang Langit Selatan dengan empat pilar berbelit naga, atap tiga lapis, Istana
   Lingxiao, jembatan giok, pulau melayang, laut awan bergerak, sinar emas, kelopak berjatuhan, sembilan cincin langit di
   bawah istana. Baris nama dan Sumber tercetak di chat.
4. `/sinematik film titah_pedang`: pedang tampak di angkasa di atas planet, cincin rune menyala satu per satu, matahari
   terbit di tepi planet, pedang berbalik lalu menukik; tembakan dari orbit menunjukkan jejak api dan selubung plasma
   menembus atmosfer dan dek awan; lalu potongan awan ke tunggangan POV dan langit terbelah; dampak tepat pada klimaks.
5. `/sinematik film titah_tiang`: film dibuka di Surga; lembing api naik dari awan, gerbang terbuka, Tiang langit keluar
   dari istana dan dilepas, jatuh melewati sembilan cincin dan laut awan, lalu potongan ke dunia nyata sampai dampak.
6. `/sinematik film titah_bintang`: tujuh inti bintang Biduk Utara menyala berurutan di angkasa di atas sisi malam
   planet, garisnya tergambar, lalu tujuh sinar menembus atmosfer; montase bintang jatuh dan klimaks tetap seperti biasa.
7. `/sinematik film titah_gunung`: segel giok naik melewati cincin ke gerbang surga; setelah dampak, potongan ke orbit
   menunjukkan sel-sel tanah terangkat melayang di depan planet; jenderal yang terangkat tetap terbanting tepat waktunya.

**Waktu dan klimaks**
8. Pakai segel sungguhan (bukan perintah uji) pada Kecepatan 1, 2 dan 0,5: dampak selalu terjadi tepat saat server
   memukul; pada Kecepatan cepat tembakan panggung memendek dulu (tetap ≥ 0,5 detik), pada Kecepatan lambat film mulai
   lebih lambat (kamera sendiri dulu).
9. Klimaks datang lebih awal saat masih di panggung: langsung potong ke dampak di bawah bingkai dampak, panggung hilang di
   bingkai yang sama.
10. Tingkat 1, 5 dan 9 (`/sinematik kosmik3d orbit 8 9`, `/sinematik film titah_pedang` setelah atur Tingkat): pedang,
    tiang dan segel makin besar dan kamera ikut menjauh; planet dan istana tetap; cincin rune bertambah di tingkat 5 dan 8.

**Keamanan kamera**
11. Tekan Backspace di tengah tembakan panggung: celup hitam singkat lalu kembali ke mata (tanpa melayang dari udara).
    Tekan dua kali: seluruh film berhenti.
12. Gerakkan kunci gerak / mouse besar di panggung: tembakan berakhir dengan aman, kamera kembali normal.
13. Dipukul mob saat di panggung, atau jatuh / masuk air / terbakar: kamera langsung kembali, panggung hilang seketika;
    tidak ada FOV, roll atau posisi kamera yang tertinggal.
14. Mati di tengah film, keluar dunia, pindah dimensi: kembali normal; masuk lagi tidak ada sisa panggung.
15. Ada musuh dalam 24 blok, darah < 50 %, atau di dalam gua sempit / di bawah atap rendah: babak atau tembakan panggung
    tidak main (cadangan Langit Kosmik lama atau kamera sendiri), film tidak pernah menunggu.
16. Kebutaan / Kegelapan, kabut tebal (Nether) atau jarak render sangat kecil: panggung diganti cadangan. Berdiri di
    gunung tinggi tepat di bawah lapisan awan vanilla: panggung mencari titik lebih tinggi atau memakai cadangan, awan
    tidak pernah tampak di dalam angkasa.

**Pengaturan**
17. "Film kosmik 3D: Ringan": lapisan lebih sedikit, tekstur lebih kecil, tanpa pemanggangan biome; "Mati": film persis
    seperti sebelum fase ini (Langit Kosmik 2D).
18. Kamera Penuh: Film Pusaka tetap, tetapi bagian "Istana" kini panggung 3D tenang (pedang di atas planet / gerbang
    surga terbuka / Biduk menyala / segel naik), pelan, tanpa sapuan. Singkat dan Mati: tidak ada panggung.
19. "Kurangi gerak": tidak ada Spektakuler; "Perjalanan langit: Mati" dan pilihan Film per titah tetap berlaku.
20. Nama pemain lain di dekat titik kamera tidak melayang di angkasa saat panggung tampil, dan kembali normal setelahnya.
    Saat hujan atau salju, tetesannya tidak jatuh di angkasa selama panggung tampil (cuaca sendiri tidak berubah, suaranya
    hanya diredam film).

**Performa dan kompatibilitas (laptop lemah, Minimal/Otomatis, jarak render 8)**
21. Sodium + Iris dengan paket shader: dome menutupi dunia, tidak ada awan/kabut paket di depan angkasa, planet tidak
    berbayang aneh (jika aneh, catat untuk `PACK_EMISSIVE_BODIES`); jumlah lapisan berkurang sesuai profil paket.
22. Minimal: planet, dome, matahari dan cahaya tepi saja; FPS tidak turun di bawah 30 lebih dari sesaat; penjaga bingkai
    menurunkan panggung di tembakan berikutnya tanpa kedip.
23. `/sinematik kosmik3d info` menunjukkan quad dan ms per bingkai di bawah anggaran; tanpa film, biaya nol.
24. Tidak ada blok berubah, tidak ada petir vanilla, cuaca, waktu atau kecepatan tick yang berubah; pemain lain tidak
    melihat panggung (hanya kamera sendiri).
25. Suara: angkasa sepi (dengung rendah), gong/lonceng/whoosh sesuai daftar, suara "langit" berbahasa Jepang dari server
    tetap terdengar dan tidak dobel.
26. Teks: setiap kunci `kosmik*` ada di en_us dan id_id; semua Sumber lama tetap sama; semua tekstur tampak benar (tanpa
    ungu-hitam), tepi cahaya lembut; model istana rapi dari segala arah, tidak ada wajah hilang atau berkedip.

**Periksa di dalam game (tercatat di DESIGN 32)**
- Kecerahan planet dan bulan rata (normal +Y, 32.1.4).
- Planet di bawah paket shader (`KosmikTypes.PACK_EMISSIVE_BODIES`).
- Arah lilitan mesh: benda yang hilang pada uji pertama berarti lilitannya terbalik (`StageFrame.sphere` / `dome`).
