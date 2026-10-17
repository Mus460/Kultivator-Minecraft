# Checklist uji Fase 4 — visual mewah, alkimia, kenaikan Dewa Abadi

Persiapan: dunia dengan cheat aktif. Tekan F5 untuk melihat diri sendiri dari orang ketiga.

## A. Visual pemain
1. `/kultivasi debug set realm mortal` → tidak ada jubah, mata, lingkaran, atau aura.
2. `/kultivasi debug set realm qi_refining 1` → jubah panjang dengan sabuk dan kerah, rok menggantung, kabut tipis.
   `/kultivasi debug set realm golden_core 1` → jubah emas bertrim emas, 3 manik emas mengorbit pinggang.
3. Warna jubah ikut Dao: `/kultivasi debug set dao fire` (oranye-merah), `ice` (biru es), `none` (warna ranah).
4. `/kultivasi debug set realm nascent_soul 1` → mata bersinar (tetap terang dalam gelap), lambang yin-yang di
   punggung, bayi roh kecil bersila di atas kepala.
5. Pedang di punggung: punya pedang di inventaris tapi tidak dipegang → pedang miring di punggung. Dipegang → hilang.
6. Lingkaran Dao: bermeditasi (`;`) → lingkaran rune bercahaya rata di tanah, berputar pelan dan tidak ikut menoleh.
   Juga muncul selama terobosan. Wilayah Dao → lingkaran lebih besar dan lebih cepat.
7. Pedang Terbang (`\`) → pedang besar mendatar di bawah kaki, naik-turun pelan.
8. Wujud Dharma → kilau emas di seluruh tubuh. Kenaikan Abadi → pilar cahaya; Dewa Abadi yang sudah naik → aura
   pelangi.
9. Saat Penyimpangan Qi mata berkedip merah. Tidak terlihat (invisibility) → semua lapisan hilang.
10. `/kultivasi visual robe|back_sword|eyes|dao_circle|aura on|off` hanya menyembunyikan bagian itu (juga bagi pemain
    lain). Sudut pandang orang pertama tetap bersih.
11. Kompatibilitas: Sodium + Iris (dengan shader) + EMF/ETF + Butter Bot → tidak crash, tidak spam error di log, bot
    Butter Bot tidak ikut berjubah. Kalau ada crash, simpan file `crash-reports` dan kabari.

## B. Alkimia (Tungku Alkimia)
12. Kuali (kosong/air/lava/salju bubuk) di atas Api Unggun atau Api Unggun Jiwa yang menyala = Tungku Alkimia.
    Klik kanan dengan tangan kosong → isi kuali. Tidak ada blok yang berubah.
13. Masukkan bahan satu per satu (maks 8), lalu jongkok + tangan kosong → 10 detik pemurnian (uap, gelembung, qi
    berpilin) → 2 pil melompat keluar sebagai item, dengan mutu Rendah/Menengah/Tinggi/Sempurna.
    Contoh: debu glowstone + gula + glow berries = Pil Pengumpul Qi. Daftar lengkap: `/kultivasi alchemy recipes`.
14. Campuran salah → asap hitam, bahan hilang, pencapaian "Asap Hitam". Api dipadamkan / kuali dihancurkan → bahan
    jatuh kembali sebagai item.
15. Perilaku vanilla kuali tetap normal: ember, botol, mencuci baju kulit berwarna (juga kalau ember dipegang di
    tangan kiri).
16. Api Unggun Jiwa → peluang +10%. `/kultivasi alchemy level` menampilkan tingkat dan peluang.
17. Pil: Pengumpul Qi, Pemulih Qi, Pembangunan Fondasi, Inti Emas, Penenang Hati, Penyembuh, Penangkal Petir,
    Pemelihara Jiwa (perlu Jiwa Baru Lahir), Pil Dewa. Tooltip menampilkan efek, mutu, dan racun. Uji cepat:
    `/kultivasi debug alchemy give <pil> <mutu 0-3>`.
18. Keluar-masuk dunia saat memurnikan → pemurnian tetap selesai, isi kuali tetap tersimpan.

## C. Kenaikan Dewa Abadi
19. `/kultivasi debug set realm true_immortal` lalu diam ±4 detik → layar menggelap, pilar emas turun dari langit,
    langit terbuka dengan awan emas dan kelopak bunga, pengumuman ke server, aura pelangi. Pemain tidak digerakkan dan
    tidak ada blok/cuaca/waktu yang berubah.
20. Setelah naik: kebal Penyimpangan Qi, dan nyawa pulih setengah hati tiap 5 detik.
21. Mati / pindah dimensi / keluar dunia di tengah peristiwa → batal tanpa hukuman, lalu diulang nanti.
22. Uji tanpa mengubah status: `/kultivasi debug ascension start` di ranah lain. Reset: `/kultivasi debug ascension
    reset`.
23. Pencapaian akhir (Jalan Tuntas, Satu Jam Keheningan, Veteran Tribulasi, Seratus Tumbang, Jalan Tanpa Cela) di tab
    Kultivasi (tombol L).

## D. Panduan
24. Buka `docs/PANDUAN.md` (di GitHub atau VS Code): panduan lengkap berbahasa Indonesia berisi tombol, perintah, tabel
    ranah, formasi (diagram), teknik, pil, resep alkimia, Iblis Hati, binatang roh, sekte, dan pengaturan.
