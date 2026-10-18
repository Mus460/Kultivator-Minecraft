# Checklist uji Fase 7b — Momen Epik

Persiapan: dunia uji dengan cheat aktif. Semua momen bisa diputar tanpa grinding dengan `uji momen <id>` (daftar
lengkap: `uji momen daftar`). Mode kamera cepat diganti dengan `/sinematik penuh|singkat|mati`. Uji dulu dengan setelan
biasamu (Iris + Complementary, Maksimal), lalu ulangi bagian I tanpa shader. Kamera hanya bergerak di tempat yang aman:
uji di tempat tenang tanpa monster. Aturan lengkap: [PANDUAN 54](../PANDUAN.md#54-momen-epik-fase-7b), rancangan di
DESIGN bagian 23.

Perintah ditulis singkat: `uji ...` = `/kultivasi uji ...`, `debug ...` = `/kultivasi debug ...`.

**Selalu periksa:** tidak ada error di log, tidak ada kunci bahasa mentah (coba Bahasa Indonesia dan English), tidak ada
blok yang berubah, tidak ada `LightningBolt`, cuaca, waktu dan kecepatan tick tidak pernah berubah. Seluruh Fase 7b
**belum pernah diuji di game** (hanya dikompilasi).

## A. Dasar dan pengaturan
1. Muat simpanan fase 7: tidak ada crash, tidak ada peringatan kunci bahasa. Tab Pengaturan punya grup "Momen Epik";
   Bantuan punya topik "Momen Epik" yang diakhiri baris Sumber.
2. Kontrol: "Lewati sinematik" default Backspace dan tidak bentrok dengan tombol lain (termasuk Butter Bot).
3. `/sinematik info` menampilkan profil (shader aktif atau vanilla), tingkat kualitas sekarang dan penjaga frame.
   `/sinematik reset` mengembalikan semua setelan Momen Epik.

## B. Tribulasi Langit
4. `uji momen tribulasi_penuh inti_emas` dengan Kamera **Singkat**: langit menggelap, pusaran awan terbentuk, kamera naik
   melihat mata langit lalu kembali sebelum petir pertama. Ada bilah layar lebar, kartu "TRIBULASI LANGIT" dan musik
   pertanda.
5. Di antara petir kamera tidak bergerak sama sekali (Singkat); cincin peringatan tetap bisa dihindari.
6. Petir terakhir: banner "Petir Terakhir", hening sebentar, petir raksasa dan beberapa petir tipis berkumpul, getaran,
   lalu gerak lambat 1,5 detik dengan kamera mengitari.
7. Berhasil: awan terbelah, pilar emas turun, kartu ranah baru ("INTI EMAS"), musik kemenangan, kamera kembali mulus.
8. `uji momen tribulasi_penuh kenaikan_abadi 9`: petir tujuh warna, Pintu Keabadian terbuka penuh, awan hilang seketika.
9. `uji momen tribulasi_penuh dewa_abadi`: 18 petir, finale sembilan warna, Gerbang Langit terbuka. Tidak ada kartu atau
   teks yang menyebut "petir sembilan warna".
10. `uji momen tribulasi_penuh inti_emas gagal`: kartu "PENYIMPANGAN QI", tepi tinta merah, kamera diam paling lama
    2 detik tanpa miring.
11. Tribulasi di Nether: tidak ada geometri langit, hanya cincin api dan kabut. Di End: pusaran ungu.
12. Tribulasi uji tidak mengumumkan apa pun di chat dan tidak mengubah ranah.

## C. Terobosan
13. `uji momen kebangkitan`, lalu `fondasi retak`, `fondasi normal`, `fondasi sempurna`, `fondasi surgawi`: pilar sesuai
    kualitas (retak berat, satu garis retak, tanpa retak, pilar kesepuluh).
14. `inti_emas`, `jiwa_baru`, `transformasi_dewa`, `penyatuan_dao` dan `kenaikan_abadi 3`: lapisan ranah masing-masing
    tampil (inti emas berputar, bayi jiwa, sosok emas, aksara Dao, pintu yang terbuka sebagian).
15. `surgawi`: langit putih, pita pelangi naik di pilar, huruf kartu pelangi (diam saat Kurangi gerak menyala).
16. `pengakuan_langit`: bintang di siang hari, pilar emas turun, gerbang terbuka, musik penutup. Singkat 3 shot, Penuh
    4 shot.
17. Tidak pernah ada kartu dan judul vanilla yang tampil bersamaan.

## D. Krisis
18. `iblis_hati`: cermin retak dan pecah; **tidak ada** gerakan kamera saat iblis muncul. Menang: iblis pecah jadi
    serpihan kaca, gerak lambat, kamera mengitari. Pemain lain di dekatmu tidak melihat peringatan iblismu.
19. `penyimpangan`: ledakan hitam-merah dan retakan merah kecil di sekitar tubuh.

## E. Kekuatan dan jurus
20. `wujud_dharma` dan `wilayah_dao es`: stempel kecil di kanan atas; FOV sedikit melebar saat Wujud Dharma.
21. `jurus_abadi nine_suns_heavens_fury` dan `jurus_dewa heaven_punishing_sword`: kartu atau stempel nama jurus, warna
    langit, gerak lambat pada pukulan penentu. Dengan Singkat kamera tidak bergerak selama pertarungan.
22. `kehancuran` dan `pemusnah` (satu jurus): langit merah menghitung mundur, efek pemusnahan, kartu "SEGEL MERAH
    DILEPAS". **Tidak ada blok yang berubah** (uji tidak pernah menggali).

## F. Murid dan sekte
23. `murid_terobosan`, `bencana 3`, `penginjak_langit`, `lampu_jiwa`, `melampaui_guru` (pakai murid dalam 32 blok, atau
    titik di depanmu).
24. `penerimaan`, `ujian_masuk 9` (warna jiwa: merah sampai nila, **tidak ada ungu**), `pengemis`, `nadi_dewa_sesat`,
    `darah_dou_di`.
25. `turnamen`, `sekte_tingkat`, `sekte_templat`, `istana_langit`, `aula`: kamera terbang di sepanjang sumbu sekte paling
    lama 8 detik, tidak menembus bangunan.

## G. Tempur
26. `bos warden` dan `bos naga`: kill-cam berjalan (Singkat hanya pembunuhan pertama tiap jenis bos, Penuh setiap kali).
27. Semua `lolos_maut …`: tidak pernah ada gerakan kamera; hanya stempel, satu kilatan, tepi tinta dan gerak lambat.
28. `uji momen semua` (atau `uji momen semua tribulasi jeda 2`): semua momen berputar berurutan dengan jeda dan nomor urut
    di chat; `uji momen berhenti` menghentikannya dan langit, suara, musik kembali normal.

## H. Keamanan kamera
29. Saat shot berjalan, tekan W, spasi, Backspace, Esc, atau gerakkan mouse: kendali langsung kembali dan kamera kembali
    halus. Tekan Backspace dua kali: bilah hilang untuk sisa adegan itu.
30. Panggil zombi dalam 16 blok (Singkat): tidak ada shot. Darah di bawah 45% (Singkat) atau 30% (Penuh): tidak ada shot,
    kecuali shot hasil setelah gagal (diam, paling lama 2 detik). `debug momen bendera` menulis alasan SAFE = salah.
31. Di air, di lava, saat jatuh, menunggang kuda, memakai elytra atau pedang terbang: tidak ada shot.
32. Kurangi gerak menyala: kamera mati, tanpa miring, tanpa gerak lambat, tanpa pinggiran warna.
33. Kamera tidak pernah masuk ke blok atau air (uji di dalam gua sempit dan di tepi danau).
34. Mode Rekam: HUD vanilla dan bar Qi hilang hanya saat momen aman milikmu sendiri; bilah dan kartu tetap. F1
    menyembunyikan bilah dan kartu. Darah, armor dan HUD tribulasi tetap terlihat di luar Mode Rekam.
35. "Hide Lightning Flashes" (vanilla) menyala: tidak ada kilatan layar maupun kilatan langit dari momen apa pun.

## I. Kinerja dan kompatibilitas
36. Iris + Complementary menyala: petir dan pilar terlihat, langit menggelap lewat lapisan layar, tangan tersembunyi saat
    shot, badan pemain terlihat.
37. Shader mati (Sodium saja): pusaran, sinar dan gerbang tampil sebagai geometri; tidak ada lubang dunia saat kamera naik
    atau terbang.
38. Maksimal + shader, finale `tribulasi_penuh dewa_abadi`: FPS turun paling banyak sekitar 10% dibanding tribulasi tanpa
    7b; kalau berat, `/sinematik info` menunjukkan penjaga frame menurunkan tingkat.
39. Minimal: tidak ada pusaran geometri, FPS stabil. Pindah dimensi atau keluar dunia di tengah adegan: langit, volume
    suara dan musik kembali normal.

## J. Multipemain (LAN)
40. Pemain kedua dalam 192 blok dari tribulasi Dewa Abadi melihat pusaran, petir dan gerbang, tetapi tidak mendapat
    gerakan kamera, bilah, musik atau kartu.
41. Pemain kedua lebih dari 48 blok: hanya warna langit yang berubah; dunianya tidak ikut gelap.

## K. Aturan keras
42. Tidak ada blok berubah di momen mana pun, kecuali Mode Kehancuran yang asli. `/execute if entity
    @e[type=lightning_bolt]` selalu gagal. Cuaca dan waktu tidak berubah. MSPT server tidak naik.

## S. Sumber
43. Setiap kartu dengan klaim cerita punya baris Sumber di DESIGN 23.13; pilar cahaya, warna petir sembilan, kamera dan
    musik tidak pernah disebut sebagai kanon.

## Diketahui belum lengkap
* Pengungkapan otomatis Pengemis, Nadi Dewa Sesat dan darah Dou Di belum terpicu dari permainan nyata (butuh getter publik
  di `SpecialCandidates` dan `HereticGates`); `uji momen pengemis|nadi_dewa_sesat|darah_dou_di` tetap bisa diputar.
* Pengecualian penjaga Hu Fa dari pemeriksaan bahaya belum tersambung (butuh `HuFaTribulation.isGuardian`): saat Hu Fa
  bersama teman, kamera Singkat mungkin diam lebih sering.
