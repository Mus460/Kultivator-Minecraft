# Checklist uji Fase 31 — Aura Kultivasi

Persiapan: dunia uji dengan cheat aktif. Aturan dan perintah ada di [PANDUAN 61](../PANDUAN.md#61-aura-kultivasi),
rancangannya di DESIGN bagian 31. Pratinjau: `/kultivasi uji aura <ranah> [tahap] [lapisan]`, `semua`, `murid <nama> ...`,
`aksen`, `stop` (op; peringkat asli tidak berubah).

**Selalu periksa:** tidak ada error di log, tidak ada kunci bahasa mentah (coba Bahasa Indonesia dan English), tidak ada
blok yang berubah, tidak ada `LightningBolt`, cuaca, waktu dan tick-rate tidak berubah, kamera tidak digerakkan aura.

**Dasar**
1. Duduk bermeditasi (tombol meditasi): aura lama memudar dan Aura Kultivasi muncul halus dalam ±1,5 detik; berdiri:
   aura memudar dalam ±1 detik, model 3D mengecil sekali lalu hilang, aura lama kembali. Tidak ada kedip.
2. `/kultivasi uji aura semua`: 33 langkah, tiap 3 detik, baris aksi menyebut ranah dan tahapnya. Setiap ranah jelas
   berbeda; setiap tahap menambah unsur yang terlihat (tabel PANDUAN 61.3); Kenaikan Abadi bertambah pilar dan paku gerbang
   per lapisan.
3. `/kultivasi uji aura golden_core 2`, lalu `... 4`: ukuran berubah mulus sekali, ada satu cincin cahaya dari dekal;
   tidak ada denyut ukuran acak. Ulangi dua kali: gerakannya sama persis.
4. Peringkat asli tidak berubah setelah pratinjau (status `/kultivasi` sama sebelum dan sesudah); pratinjau berhenti
   sendiri setelah 20 detik dan dengan `/kultivasi uji aura stop`.
5. `/kultivasi uji aura murid <nama> nascent_soul 3`: murid itu menampilkan aura Jiwa Baru Lahir; murid yang benar-benar
   bermeditasi juga menampilkan auranya sendiri; efek meditasi lama murid tidak tampil ganda.

**Warna dan aksen**
6. Pilih Dao berbeda (Api, Es, Petir, ...): cincin, pita, dekal dan rune ikut warna Dao; motes yang masuk memakai warna
   akar.
7. Akar Surgawi (atau `/kultivasi uji aura aksen`): cincin luar emas dan tiga percikan emas; murid dengan fisik langka
   memakai warna aksen fisiknya.
8. Bar kultivasi penuh di Puncak: cahaya peringatan kuning-merah berdenyut (bukan berubah ukuran), garis retak tipis di
   dekal, denting lonceng pelan; di Abadi Sejati Puncak tidak pernah muncul.

**Pengaturan**
9. "Aura Kultivasi: Sederhana": hanya dekal, cahaya dan partikel; tanpa model 3D dan pita. "Mati": hanya aura lama.
10. "Aura orang lain: Mati": aura pemain lain dan murid mereka hilang; aura sendiri dan murid sendiri tetap.
11. "Dengung aura: Mati": tidak ada dengung atau denting. Nyala: dengung sangat pelan, berbeda per ranah, ikut slider
    Suara Sekitar vanilla.
12. Kualitas Minimal / Normal / Maksimal: jumlah lapisan, model dan partikel sesuai tabel PANDUAN 61.5; "Efek pemain lain:
    Ringan" mengurangi aura orang lain.

**Kamera, jarak dan keamanan**
13. Orang pertama: tidak ada model atau quad menutupi pandangan; hanya cahaya lembut di tepi layar dan beberapa motes di
    bawah garis mata. F5: aura lengkap. F1: cahaya tepi hilang.
14. Jarak: ≤ 24 b lengkap; 24-64 b hanya dekal, satu cahaya dan sedikit partikel; > 64 b tidak ada.
15. Aura tidak pernah menggerakkan kamera; film dan sinematik tetap seperti biasa, dan cahaya tepi hilang saat film main.
16. Tidak ada blok berubah, tidak ada petir vanilla, cuaca, waktu atau kecepatan tick yang berubah; Mode Kehancuran tidak
    berpengaruh pada aura.
17. Terobosan: aura meredup separuh agar efek terobosan jelas; Penyimpangan Qi dan Tribulasi mengakhiri aura.

**Performa dan kompatibilitas (laptop lemah, Minimal/Otomatis, jarak render 8)**
18. Lima murid bermeditasi + diri sendiri di Abadi Sejati: FPS tidak turun di bawah 30 lebih dari sesaat; teknik tetap
    mendapat model lebih dulu (aura memakai prioritas DETAIL).
19. Sodium + Iris dengan paket shader: aura tampil, tidak ada bayangan aneh dari quad, dekal tidak berkedip di tanah,
    paling banyak satu lapisan besar per aura.
20. Tidak ada yang bermeditasi: biaya aura hampir nol (tanpa rig, tanpa quad).
21. Teks: setiap kunci `kultivator.aura.*` ada di en_us dan id_id; semua baris Sumber lama tetap sama; setiap ranah punya
    baris Sumber aura sesuai PANDUAN 61.3.
22. Semua tekstur aura tampak benar (tanpa ungu-hitam), tepi cahaya lembut; model 3D aura tampil rapi dari segala arah.
