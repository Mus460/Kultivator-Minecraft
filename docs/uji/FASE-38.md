# Checklist uji Fase 38 — Kota Besar, Tempat dan Kota Bertema (final F3)

Ditulis F2, dirapikan F3 setelah gelombang detail W3 (build B3 hijau, harness worldgen hijau). Sumber: DESIGN 38.E.5 dan
38.T.15.2 (`docs/rancangan/38-kota-besar-dan-tempat.md`). Belum ada yang diuji di game. Semua perintah uji di bawah `/kultivasi uji ...` (op). **Selalu dunia baru** (tidak ada uji
dunia lama atau migrasi). Pemain memakai Tectonic + Terralith: ulangi butir 1 dan 38.T-1 sekali dengan kedua mod itu.

**Selalu periksa:** tidak ada error di log, tidak ada kunci bahasa mentah (Bahasa Indonesia dan English), blok hanya
berubah lewat WorldBreaker/BuildPlacer, tidak ada `LightningBolt`, cuaca, waktu dan tick-rate tidak berubah, Efek Minimal
tetap ringan.

## 38 — Ibu kota, kampung, kompleks, gua, jalan, uang, kehidupan

1. Dunia baru: `uji tempat rencana` menyebut ibu kota pertama 1800-3200 blok dari pusat dengan status "layak"; `uji
   kota_besar cari` membawa ke sana; ke-11 distrik ada, tidak ada desa vanilla atau kota lain di dalam tembok. Bila tidak
   ada yang lahir, `kota_besar cari` menyebut alasan tiap kandidat cincin pertama.
2. Terbang di atas ibu kota: jalan antar-lot rapi, tembok dan menara bersambung di empat sisi, Gerbang Selatan Agung
   menghadap Lapangan Kafilah, jalan Kekaisaran keluar dari gerbang.
3. Pukul 22.00 (`uji kota_besar jam_malam` menyebut 22.00-07.00) genderang berbunyi dan gerbang menutup (daun berputar,
   bukan blok); ketuk daun gerbang, penjaga membuka (2 NB); pukul 07.00 lonceng membuka semua gerbang. (F3 membetulkan
   tick genderang: dulu 22000 = 04.00.)
4. Lukai warga di depan penjaga: Dicurigai lalu Buronan (poster wajah pemain di papan); bayar denda di Yamen; ulangi dan
   pilih Serahkan Diri: masuk sel, hitung mundur, murid tidak ikut, keluar bersih.
5. Rumah Uang: setor, tarik (biaya 1 %), Surat Wesel dibuat lalu ditukar; gadai pusaka lalu tebus.
6. Judi batu: 10 batu Kerikil, animasi belah, hasil wajar; `uji ekonomi judi 100000` -> EV buta 0.86-0.90, strategi
   terbaik <= 1.02 (hanya emas sekitar 1.007). `uji ekonomi simulasi semua 30` -> 9/9 pita OK.
7. Paviliun Seribu Bilah: pratinjau 3D berputar, stat dibanding pedang di tangan; beli jian; Duel Pedang mengenalinya
   sebagai PEDANG. Buka dan tutup setiap layar tempat berulang kali (toko, gilda, teleport, dialog): tidak crash (F2
   memindahkan hook lembar pertama dari konstruktor ke `init()`).
8. Pesan pusaka mutu 3 ke Empu Ou Ye, tunggu 3 hari dunia (restart server di tengah): pesanan tetap ada.
9. Lelang Agung: 8 lot, Gu Lianshan ikut menawar; titip barang, hentikan server saat lelang, masuk lagi: titipan dibayar
   atau dikembalikan.
10. Gilda Kultivator: misi C `begal`, penunjuk ke kemah begal nyata; tikungan moral muncul (20 %).
11. Buku Kas: sewa lapak, isi barang, tunjuk murid penjaga; esok hari ada penjualan; kolom PK dan Batu terpisah; kafilah
    ke kota lain pulang dengan laba <= 30 %.
12. Kehidupan: `uji hidup jam 5|7|12|18|22` di hunian: menyapu, kerja, makan, teh, tidur (kasur tidak berubah status);
    anak bermain, tetua main catur, pedagang berseru.
13. Hujan alami: payung kertas; badai: anak bersembunyi; Gerhana Darah: jalan kosong, penjaga ganda; petir alami di dekat
    warga: warga terkejut (reaksi saja).
14. Festival Lentera (purnama): lentera langit naik; teka-teki benar memberi Jimat Lentera dan Nama Baik +1.
15. Gelombang Binatang: gerbang tertutup, monster dari luar tembok saja, tanpa creeper/enderman, tidak ada blok runtuh;
    menang -> hadiah Kas Kota.
16. Turnamen Agung: juara sekte NPC bernama; babak pemain lewat Duel Pedang; tribun penuh impostor.
17. Pertemuan Pil: lima alkemis saingan punya wajah, kelamin dan nama yang sama di bangku dan di papan skor.
18. Alur "Bayangan di Balik Takhta": bab 1-7 (`uji kota_besar alur <bab>`), tiga cabang bab 5 memberi bab 6-7 berbeda.
19. Kampung: lonceng, Plakat Pelindung Sekte bisa diikrarkan, Festival Panen hari 12; desa klan: Tuan Muda menantang
    pemain berranah rendah.
20. Kompleks sekte: NETRAL hanya halaman; sekutu boleh tanding latih; perang -> murid bermusuhan.
21. Gua Pertapa: tiga ujian lintas hari, teknik diajarkan, pertapa pergi, kursi bisa jadi Gua Kediaman.
22. Makam Warisan: penghalang menolak ranah rendah; uji Hati/Kekuatan/Pemahaman; murid menunggu di luar.
23. Puncak Petir: tribulasi di sana tercatat di Batu Catatan; kerusakan x0.90 di log uji.
24. Jalan dari ibu kota ke kota terdekat: ubin, jembatan di sungai, tonggak tiap +-500 blok, kuil jalan, pos singgah;
    jalan tergambar di peta setelah menyentuh tonggak.
25. Perbaikan: `uji tempat rusak 40 pemain` di kampung -> Kepala Desa membawa tagihan; Bayar -> tukang x4 cepat; Tolak ->
    Nama Baik turun, harga +10 %. Ranah jauh di atas: tanpa tagihan, NPC berlutut, anak bersembunyi. Peti berisi di
    dalam rumah tidak pernah dibuang.
26. Mode Kehancuran di dalam ibu kota/kampung tidak menghancurkan apa pun; di tepi zona kerusakan tercatat.
27. UI: semua layar di skala GUI 2, 3, 4 dan jendela 320 x 240, navigasi keyboard saja, teks tidak di bawah 0.6.
28. Performa: `uji tempat ukur`: chunk baru <= +6 %, MSPT di tengah ibu kota <= patokan +4 ms; Efek Minimal tanpa
    lentera/impostor.
29. Simpanan: keluar-masuk dan restart server: rekening, pesanan, lencana, buku kas, alur, kerusakan dan tagihan tetap.

## 38.T — Kota Bertema

1. `uji tema rencana` menampilkan lima tema; empat tema damai layak di cincin pertama (atau alasannya); `uji tema cari
   <tema>` membawa ke setiap kota; semua distrik lengkap. Di dunia Terralith pelabuhan tetap utuh sembilan distrik (F2:
   distrik pelabuhan memakai tag bioma `#kultivator:has_structure/kota_tema/pelabuhan`).
2. Pelabuhan: dermaga di atas laut bertiang sampai dasar, air tetap di sekitar tiang, kota menghadap laut.
3. Kapal dagang datang tick 2000-4000 di hari siklusnya, tambat, bongkar, berangkat; restart saat tambat: kapal muncul lagi.
4. Kapal Lintas Laut (pita Fondasi): adegan berlayar, tiba di Laut Tanpa Batas; Gerbang Pulang kembali; murid (<= 2) ikut.
5. Serbuan Bajak Laut: kapal hitam, band menyerbu dermaga, tidak ada blok rusak; menang -> hadiah Kas Kota.
6. Oasis: tawar di bazar tiga putaran; harga akhir >= 0.90 dasar; tawaran terlalu rendah membuat pedagang ketus.
7. Badai pasir: di luar tembok tanpa Restu Mata Air -> Lambat I; di dalam aman; cuaca dunia tidak berubah; Gerbang Giok
   tertutup selama badai; sesudahnya 2-4 Temuan Pasir.
8. Kafilah Agung: unta beriring masuk Gerbang Giok, berlutut; ikut sebagai penumpang; sewa unta, tinggalkan 2 menit ->
   unta pulang. Kafilah jalan bertubuh unta atau jung hilang bersih saat perjalanan berakhir (tidak ada tubuh tertinggal).
9. Reruntuhan gurun: Lempeng Peta menunjuk; Raja Kalajengking menggali (tanpa blok berubah) dan muncul; hadiah sekali.
10. Kota Air: kanal bersambung (`uji tema cek`); Perahu Pos melewati 4 halte; Tikus Air dikejar dengan perahu sewaan.
11. Malam Lentera Air (bulan mati): warga melepas lentera; harapan pemain lain tidak terbaca.
12. Lomba Perahu Naga sebagai penabuh: ketepatan irama mempercepat perahu; juara berhadiah.
13. Kota Gunung: kereta gantung dua bentang lewat tiang tengah; badai petir menghentikannya; Lift Macet: tali turun.
14. Ziarah: terbang di tengah -> batal; tanpa terbang -> Restu Puncak; meditasi di teras Aula Emas mendapat faktor ekstra.
15. Kera gunung merebut bakpao saja; lempar buah -> barang jatuh.
16. Longsor saat hujan: batu menggelinding dan terpental di pagar; tidak ada blok berubah.
17. Kota Iblis: pita Pemurnian Qi ditolak; pita Fondasi masuk dengan kartu merah; upeti; terbang masuk tanpa upeti ->
    penegak bermusuhan.
18. Zona Perjanjian: menyerang NPC di pasar -> semua penegak memburu; malam di luar ZP -> Jam Berburu sekali semalam.
19. Arena: lima laga, kalah -> berhenti di 5 % HP dan hidup; taruhan 1000 simulasi -> EV 0.88-0.92.
20. Tawanan: tebus, kawal ke kampung asal -> Nama Baik +3, Infami -5; tidak ada pilihan memperbudak.
21. Menara Kitab Terlarang: lantai terkunci per Gentar; Bakar Darah menambah Infami. Kolam Semedi Darah (distrik Menara
    Terlarang, Infami >= 20) memberi faktor x1.3.
22. Infami >= 40: Pemburu Lurus mencegat di jalan (tidak di tempat aman); "Menyerah dan menebus" -> Infami -20; buronan
    iblis dari papan bisa ditemukan.
23. Penyerbuan Lurus: pilih pihak; hasil berbeda; gerbang = entitas.
24. Dagang: bicara ke pedagang pasar (Pedagang Seberang, Penjual Ikan, Pedagang Bazar, Kafilah Bashi, Nyonya Sutra, Ahli
    Teh, Pemetik Herba, pedagang keliling, Makelar Gelap): tab Jasa memuat "Pasar <kota>", membuka Buku Dagang mode pasar
    (tab Pasar pertama, blok pasar beli/jual/segel digambar di layar; skala GUI 2, 3, 4); Makelar Gelap menerima selundupan; Pasar Seberang memotong bea 2 %. `uji tema dagang
    simulasi 7` pita 3: laba/minggu <= 600 NB; jenuh setelah 10 unit; selundupan disita 40 % di bea.
25. Kontrak muatan: Syahbandar, Kafilah Bashi, Nyonya Sutra, Kuli Gunung punya Jasa "Papan Kontrak Muatan"; ambil, antar
    dengan kapal terbang ke ibu kota: bayaran x1.3; peti dibuka di jalan -> gagal, deposit hilang.
26. Lima rantai quest: tiap bab bisa dilompati (`uji tema alur`), tiap cabang utama berakhir berbeda.
27. Perbaikan: `uji tempat rusak 30 pemain` di dermaga/kanal -> tagihan; air kanal pulih; Kota Iblis: Harga Darah, tolak
    -> penegak bermusuhan 1 hari.
28. Peta dan hub: penanda 42-49 setelah kota ditemukan; kartu "Kota Bertema" (Infami, Gentar, Buku Dagang); Kitab Panduan
    bab 20; lore Kota Oasis dan Kota Iblis terbuka saat kota ditemukan.
29. Lelang Gelap: lot Kontrak Kepala bisa muncul (harga dasar 180 NB).
30. Performa: `uji tempat ukur` bagian `tema` <= 0.6 ms/tick di tengah tiap kota; Efek Minimal tanpa partikel badai,
    lentera air, cincin cahaya; entitas mekanisme <= 12 per kota.

## Tambahan gelombang detail W3
31. Ibu kota: papan buronan (dinding utara aula Yamen, di samping meja Kapten Penjaga) dan papan Pahlawan (ujung utara
    jalan Lapangan Agung) menghadap ke dalam, bukan ke dinding; NPC layanan muncul juga di distrik yang dikunjungi belakangan.
32. Festival Panen: empat babak terpisah wajar bila dimulai pagi; bila dimulai sore atau lewat perintah uji, babak rapat
    (diketahui).
33. Makam Warisan: jarum kompas Giok Warisan menunjuk makam dengan arah yang sama seperti kompas vanilla (bingkai 16 = lurus
    ke depan).
34. Pos singgah A/B, pos dagang, pos singgah besar: perabot templat (teratai, lilin di atas slab, lectern di atas tong, sea
    pickle) tetap di tempat setelah chunk dimuat.
35. Model (klien): `/hiasan pamer`, `/hiasan prop 1|2`, gerbang ibu kota saat senja (ayun, debu, pengetuk), pintu sel Yamen,
    gerobak kafilah (muatan peti/karung/guci), penyeberangan rakit.
36. Bea Gerbang Giok: dengan Surat Jalan Gurun yang sudah lewat masa berlakunya, bea 3 % dipungut (F3).
37. Kota bertema: dialog baru Lin dan Zhao (pelabuhan), kabar tema baru, perabot karavanserai.

## Belum (untuk gelombang berikut)
- Suara baru 38ST/38TK belum dibuat (VOICEVOX belum dijalankan; daftar `docs/progres/mega-voices.md`); kode memakai cadangan.
- Rak senjata Paviliun belum menampilkan stok toko (hanya `/hiasan senjata`).
- Beberapa peran di kota bertema belum punya dialog sendiri; menara pengawas dan dermaga awan kompleks sekte belum didetailkan.
- Tubuh Hei Sha hanya di memori: restart saat duel memanggilnya lagi lewat dialog Deng (batas yang diketahui).
