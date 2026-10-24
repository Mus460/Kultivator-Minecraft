# Checklist uji Fase 39 — Murid Mandiri dan Perseteruan antar-sekte (final F3)

Ditulis F2, dirapikan F3 setelah W3 (build B3 hijau). Sumber: DESIGN 39.13.2 dan 39.F.8
(`docs/rancangan/39-murid-mandiri.md`). Belum ada yang diuji di game. Perintah uji di bawah `/kultivasi uji perguruan mandiri ...` (op). **Selalu dunia baru.** Config bawaan:
`mandiri.skalaXp` 1.5, `mandiri.bolehGugur` false, `mandiri_seteru.bolehPerang` false. Panduan: bab 11 "Murid Mandiri"
(indeks simpan 20, langkah `c21.*`). Halaman Bantuan memuat topik "Murid Mandiri".

**Selalu periksa:** tidak ada error di log, tidak ada kunci bahasa mentah (id_id dan en_us), tidak ada murid gugur karena
39, tidak ada barang atau uang ganda.

## Persiapan
- [ ] Dunia baru, aula tingkat >= 4 dengan Paviliun, kebun, tambang, arena; 10+ murid beragam watak dan akar.
- [ ] `kebijakan SEIMBANG`; buka kartu hub "Jalan Hidup Murid".

## Jalan hidup dan perencana
- [ ] Setiap murid punya jalan hidup berbeda yang masuk akal (alasan di tab Jalan Hidup, tab ketiga setelah Hati dan
      Hubungan).
- [ ] `skor <murid>` menampilkan tiga kegiatan dengan faktor.
- [ ] Pita jadwal hari ini terisi; kemarin redup.
- [ ] Salam pagi 05:00; makan siang 12:00 di pos dapur.
- [ ] MALAS lebih sering istirahat; RAJIN lebih sering latihan/kerja.
- [ ] Hujan: latihan luar berkurang, teh bertambah. Purnama: meditasi malam bertambah.
- [ ] `jalan <murid> alkemis` lalu `lompat 10`: jalan bisa kembali bila skor lain unggul; rupa murid (busana jalan)
      ikut berganti.

## Kendali guru
- [ ] Perintah Ikuti: murid berhenti dari kegiatan mandiri seketika; `[Bebaskan]`: kembali ke jadwal.
- [ ] Kebijakan Patuh: tidak ada kegiatan 40-56, tidak ada perjalanan mandiri.
- [ ] Larang "belanja" untuk satu murid: ia tidak pernah ke kota. Tempat terlarang "pasar_gelap": murid tergoda tidak
      mampir.
- [ ] Uang saku 0 %: dompet tidak bertambah; kas aula tidak berkurang.

## Kerja sendiri (39D)
- [ ] `kerja info|mulai|bahan|natal|jimat|alkimia`: murid meramu pil, menulis jimat, merawat kebun sendiri; barang buatan
      murid punya tooltip "buatan murid".
- [ ] Teh bersama dan tanding latihan menaikkan afinitas; riwayat Hubungan menulis "Minum teh di waktu luang" dan "Tanding
      latihan" (bukan "Peristiwa lain").

## Belanja ke kota (39C)
- [ ] `kirim <murid> belanja`: berkemas, membungkuk, berjalan atau terbang keluar.
- [ ] Susul ke kota: tubuh murid (nama + jubah sekte) melihat lapak, menawar, membayar; pedagang menoleh dan menjawab.
- [ ] Klik kanan tubuh murid: `[Lihat belanja]`, `[Tambah uang]`, `[Ikut Guru pulang]` bekerja.
- [ ] Makan dan teh di kota; entri harian menyebut kota dan harga.
- [ ] `percepat <pelancong> pulang`: kotak di punggung, titipan ke Lumbung, hadiah ke sahabat, laporan, advancement
      `sekte/mandiri_belanja`.
- [ ] Barang dipakai: herba diramu, pil terobosan ditelan sebelum menembus.
- [ ] LICIK menawar lebih murah; SOMBONG membayar lebih mahal; murid ke-5 di hari sama tidak mendapat pil yang sama.
- [ ] Pencopet: murid berani mengejar; hasil tercatat; Nama Baik guru berubah.

## Perjalanan lain
- [ ] Berburu: inti binatang pulang; kisah 4-5 baris.
- [ ] Reruntuhan/alam rahasia sesuai Kehati-hatian.
- [ ] Perjalanan berdua sahabat: kisah dua sudut pandang; afinitas naik.
- [ ] `tawan <pelancong>`: pemberitahuan dengan tombol Tebus/Lacak, penanda peta `mandiri/tawanan`; selamatkan -> terima
      kasih; abaikan 3 hari -> lolos dengan Luka Berat.
- [ ] `[Panggil pulang]` mempercepat kepulangan.

## Tumbuh sampai Abadi Sejati (39B)
- [ ] Terobosan sendiri dengan dua Hu Fa sahabat; sambaran lebih ringan.
- [ ] Ujian Hati di Jiwa Baru Lahir (paksa dengan `hati`).
- [ ] Dao dipilih sendiri setelah menunggu guru 3 hari (Seimbang) / langsung (Penuh).
- [ ] Teknik dari rak, gulungan PK, tetua, senior, pencerahan (`lompat 30`).
- [ ] Naik jenjang disetujui Tetua Agung; Latihan Khusus Luar ke Dalam tetap jalan.
- [ ] Akar Merah lepas setelah Pil Pembersih Sumsum dibeli sendiri.
- [ ] Tutup Diri 2 hari; Serap Batu memotong dompet, maks 2 blok/hari.
- [ ] `proyeksi` sesuai tabel; satu murid (`lompat` berulang) mencapai Abadi Sejati tanpa ajaran guru -> langkah
      `c21.abadi`.

## Iblis dan risiko
- [ ] `iblis 70`: bisikan, Pasar Gelap, entri kelam, rupa jalan iblis; `[Tebus]` mengembalikan jalan asal.
- [ ] `bolehGugur false`: tidak ada murid gugur karena 39 di semua tingkat Kehati-hatian.

## Cerita, buku harian dan suara (39E)
- [ ] `harian <murid> uji`: 5 entri berbeda, kalimat lengkap di dua bahasa.
- [ ] Buku Harian dan Papan Mandiri terbuka di skala GUI 2-4; 10 tablet muat pada jendela setinggi 240 px.
- [ ] Tokoh pedagang langganan setelah 3 kunjungan; saingan muncul dan busurnya selesai.
- [ ] Baris suara baru (atau cadangan lama bila klip belum ada: VOICEVOX belum dijalankan di F2).
- [ ] Pemberitahuan ditunda saat guru tribulasi; ringkasan "Selama Guru pergi" saat masuk.

## Perseteruan antar-sekte (39F, uji terima 39.F.8)
1. Murid SOMBONG ke kota dekat gerbang sekte Iblis (`kirim belanja`, `seteru frekuensi 3`): tokoh bernama mengejek di
   kedai teh, murid membalas, warga menoleh; Buku Harian menyebut tokoh, sekte, kota.
2. Tantangan -> Duel Pedang dengan intro 35, kartu judul, warga berkerumun (38) atau suara kerumunan; tidak ada yang mati;
   hasil dan julukan tercatat; kerumunan bubar sesudahnya.
3. Kalah -> pulang pincang berperban, sumpah dendam bila PENDENDAM; cita DENDAM di tab.
4. Panas >= 60 -> geng patroli dengan panji sekte di jalan; perjalanan berikut disergap atau memutar (AMAN).
5. Guru: Damaikan / Utus tetua / Hukum / Tuntut ganti rugi / Dukung duel / Tantang tetua mereka.
6. Panas >= 80 -> standing turun di layar Diplomasi (`mandiri.seteru.*`); >= 90 -> tantangan turnamen resmi lewat D (tidak
   ada perang karena `bolehPerang` false).
7. Sahabat lintas sekte membantu melawan begal; Kekasih Terlarang: pertemuan pasar malam, ketahuan -> panas naik; kawin
   lari -> calon baru di aula.
8. Sekte berpakta damai tidak melewati T3 (tetua mereka melerai).
9. Maks 4 tubuh RIVAL; `anggaran` < 0,4 ms/tick; duel ditunda saat guru tribulasi. Advancement `sekte/mandiri_seteru_duel`,
   `_damai`, `_kekasih`.

## Kinerja dan keamanan data
- [ ] 120 murid: `anggaran` < 0,4 ms/tick; FPS klien stabil di Minimal.
- [ ] Putus koneksi saat murid di kota dan saat tiba: tidak ada barang/uang ganda; kas log cocok.
- [ ] Simpan-muat di tengah Tutup Diri dan di tengah perjalanan: keadaan berlanjut benar.

## Tambahan gelombang detail W3
- [ ] Murid kita kalah duel seteru yang ditonton di rumah: setelah adegan, murid berstatus terluka satu hari (`luka` di tab
      Jalan Hidup, sebab `seteru`); tidak ada yang mati.
- [ ] Prop duduk/jongkok (kuali, meja, dupa) berdiri di lantai, tidak tenggelam; labu air di pinggang kanan tidak menembus
      lengan.
- [ ] Buku Harian, kabar kerja dan baris kepulangan memakai teks baru (tanpa kunci mentah).
- [ ] Adegan seteru hanya berjalan bila pemain dalam 96 blok; teks dan suara kategori (ejekan, balasan) cocok secara umum.

## Belum (untuk gelombang berikut)
- Klip suara 39E/39F belum dibuat (VOICEVOX; daftar `docs/progres/mega-voices.md`).
- Kekasih kawin lari datang dengan nama gulungan aula (butuh nama tetap di `CandidateSpec`).
- Tombol Larang guru, glyph pedang bersilang, sahabat dari sekte lain (`SAHABAT_LUAR`) belum ada; kabar pengembara belum
  membuka situs baru.
