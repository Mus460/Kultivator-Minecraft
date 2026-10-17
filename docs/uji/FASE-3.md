# Checklist uji Fase 3 — Iblis Hati, binatang roh, formasi pelindung, sekte

Persiapan: dunia dengan cheat aktif, mode Survival kecuali disebut lain.

## A. Iblis Hati
1. Ujian sebelum Jiwa Baru Lahir: `/kultivasi debug set realm golden_core 4`, `/kultivasi debug fill`,
   `/kultivasi breakthrough` → Iblis Hati muncul 4–6 blok darimu (pilar asap hitam), judul "Iblis Hati", bilah bos ungu
   120 detik yang hanya kamu lihat.
2. Tampilannya: skin kamu sendiri, hitam-ungu agak tembus pandang, mata merah menyala (cek di tempat gelap), asap naik,
   papan nama "Iblis Hati <namamu>". Kalau game crash saat iblis muncul, catat dan laporkan (renderer baru).
3. Ejekan di chat tiap ±15 detik. Gerakan khusus: melesat mendekat, atau mengangkat lengan lalu "Telapak Gelap".
   Teknik dan Langkah Awan bisa dipakai melawannya.
4. Menang sebelum 120 detik → "Hati Dao Teguh", lalu pertanda Tribulasi Langit langsung dimulai.
5. Kalah: pukulan yang membuat nyawa < 20% dibatalkan (tidak mati), ujian kalah → Penyimpangan Qi, tribulasi TIDAK
   dimulai. Kalah karena waktu habis juga gagal.
6. Setelah menang, ujian ranah itu tidak berulang. Ujian berikutnya sebelum Penyatuan Dao dan Dewa Abadi.
7. Iblis tekanan: `/kultivasi debug heartdemon summon` (90 detik, tanpa terobosan). Menang → tekanan 0 + XP; kalah →
   Penyimpangan Qi ringan. Alami: `/kultivasi debug heartdemon pressure 150` lalu tunggu (peringatan "Hatimu
   bergetar…" 10 detik sebelum muncul).
8. Iblis tidak tersimpan: keluar-masuk dunia saat ujian → iblis hilang dan ujian dihitung kalah.
9. Hanya pemiliknya yang bisa melukai (pemain lain/teknik pemain lain tidak mempan); tidak menginjak ladang, tidak
   memungut item, tidak jatuh ke lava/void (berkedip kembali). `/summon kultivator:heart_demon` ditolak.

## B. Binatang roh
10. Resep Pil Binatang Roh: tulang + glow berries + pecahan kecubung + wortel emas (bebas posisi).
11. Serigala jinak milikmu + pil (klik kanan) → spiral partikel, nama "Serigala Roh". Serigala liar / milik orang lain
    → ditolak, pil tidak berkurang.
12. Rubah: hanya rubah yang sudah percaya padamu (hasil ternak); rubah roh mengikutimu (teleport kalau >24 blok),
    tidak memetik semak beri, tidak memburu ayam.
13. XP: binatang membunuh monster, atau kamu membunuh di dekatnya, atau pil tambahan (+40, jeda 30 detik).
    `/kultivasi beast list` menampilkan tingkat dan XP. Uji cepat: lihat binatangnya lalu `/kultivasi debug beast xp 100`.
14. Evolusi: tingkat 2 butuh kamu Inti Emas, tingkat 3 butuh Jiwa Baru Lahir. Nama dari name tag tidak ditimpa.
    Serigala Petir Bulan (petir partikel, penangkal petir tidak menyala), Rubah Ekor Tiga (gigitan membakar monster
    saja), Serigala Langit / Rubah Ekor Sembilan (9 api roh berputar).
15. Binatang roh tidak melukai sapi, villager, atau pemain lain di luar latih tanding. Data tetap setelah keluar-masuk.

## C. Formasi Pelindung (bawaannya MATI)
16. Batu Magnet + 4 Obsidian Menangis tepat 4 blok ke utara/selatan/timur/barat, sejajar. Belum melakukan apa-apa
    sebelum diaktifkan.
17. Jongkok + klik kanan Batu Magnet dengan pecahan kecubung → aktif, daya +10 menit (maks 2 jam), kubah giok.
    Pola belum lengkap → pilar yang kurang ditandai, pecahan tidak berkurang. Kompas tetap berfungsi normal.
18. Monster dalam radius 16 terdorong keluar dan kena 1 damage/detik; pemain, anjing jinak, villager, sapi aman.
19. Pemilik jongkok + tangan kosong → mati/nyala (daya tersimpan). Pemain lain tidak bisa mengubahnya.
20. Pilar dihancurkan → formasi mati dalam 5 detik. Daya habis → mati sendiri. Tidak ada blok yang diubah mod.
21. Di dalam formasi aktif, sambaran tribulasi 15% lebih lemah. `/kultivasi formation list` menampilkan formasimu.
22. `"protectiveFormations": false` di `config/kultivator.json` → fitur nonaktif di server.

## D. Sekte & latih tanding (multiplayer; LAN atau server)
23. `/sect create Lembah Giok` (alias `/sekte`), `/sect list`, `/sect info`. Nama 3–24 karakter, unik.
24. `/sect invite B` → B klik [Bergabung]. `/sect chat …`, `/sect kick`, `/sect leave` (ketua keluar → anggota tertua
    jadi ketua), `/sect disband` (konfirmasi).
25. Bonus meditasi ×1,15 saat bermeditasi dalam 16 blok dari saudara sekte.
26. Latih tanding: `/gamerule pvp true`, `/sect spar B` → B [Terima] → hitung mundur 3-2-1. Teknik bisa mengenai lawan
    hanya selama duel. Pukulan yang membuat lawan < 10% dibatalkan dan duel selesai (tidak ada yang mati).
    Hadiah hanya untuk duel sungguhan (≥ 15 detik, lawan kehilangan ≥ 40% nyawa, jeda 10 menit per pasangan).
27. Duel berakhir tanpa pemenang kalau terlalu jauh (>48 blok), pindah dimensi, keluar server, atau waktu habis (seri).
28. Data sekte tetap setelah restart. Semua teks tampil dalam bahasa Indonesia / English tanpa kunci mentah.
