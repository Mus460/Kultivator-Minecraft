# Checklist uji Fase 1 — ranah, terobosan, tribulasi, meditasi, pil, HUD & layar

Persiapan: dunia survival dengan cheat aktif, profil SODIUM. Semua perintah boleh pakai `/cultivate` atau `/kultivasi`.
Kalau ada yang aneh, catat langkahnya dan pesan yang muncul.

## A. Dasar & data
1. `/kultivasi debug reset` → `/kultivasi info`: Manusia Fana, Kultivasi 0 / 60. Kiri bawah ada orb dantian dengan
   petunjuk "Tekan ' untuk menguji akar spiritualmu".
2. Mati (`/kill`), respawn, keluar-masuk dunia, pindah ke Nether: data kultivasi tetap sama.

## B. XP, leher botol, Qi Tertimbun
3. Bunuh zombie (`/summon zombie ~ ~ ~3`) → action bar "+10 Kultivasi". Babi tidak memberi XP.
4. Tambang bijih berlian (+10) dan deepslate (+15). Pakai Silk Touch atau mode kreatif → tidak ada XP.
   Ancient debris sengaja tidak memberi XP (bisa dipasang ulang terus-menerus).
5. `/kultivasi debug addxp 100` saat Manusia Fana: bar 60/60, sisanya jadi Qi Tertimbun, muncul pesan sekali bahwa
   akar harus diuji dulu.
6. `/kultivasi debug set realm qi_refining` lalu `/kultivasi debug addxp 1000`: naik tahap otomatis sampai Puncak,
   pesan leher botol muncul sekali, sisanya masuk Qi Tertimbun.
7. Statistik: `/kultivasi debug set realm golden_core 3` → `/attribute @s minecraft:max_health get` = 34.

## C. Terobosan & Tribulasi Langit
8. Kebangkitan: `/kultivasi root test`, `/kultivasi debug fill`, `/kultivasi breakthrough`, diam 6 detik → qi masuk
   ke tubuh, judul "Pemurnian Qi, Tahap Awal" + kualitas.
9. Pemurnian Qi → Fondasi: `/kultivasi debug set realm qi_refining 4`, `fill`, `breakthrough` → peluang ±75%.
   Gagal = Penyimpangan Qi (Weakness, Slowness, darah −25%, bar −30%), tidak mati.
10. Tribulasi Overworld: `/kultivasi debug set realm foundation 4`, `fill`, `breakthrough` di tempat terbuka →
    12 detik pertanda (awan gelap partikel, gemuruh), lalu 3 sambaran petir **partikel**. Monster Pelindung Dao muncul
    10–16 blok; sisa monster hilang dengan asap setelah selesai (tanpa drop).
11. **Blok aman**: taruh penangkal petir, blok tembaga, rumput kering, TNT, pintu kayu dan ladang di dekat tribulasi.
    Tidak ada yang terbakar, rusak, berubah, teroksidasi; penangkal petir tidak menyala; cuaca tidak berubah.
    Skeleton/stray pelindung memegang pedang batu (tanpa panah); monster pelindung tidak menginjak ladang.
12. Damage tidak bisa dikurangi: armor netherite Protection IV + `/effect give @s resistance 120 4` + perisai →
    darah tetap turun ±15% per sambaran, sambaran terakhir ±30%.
13. Tidak pernah mati: pegang Totem, `/kultivasi debug set realm dao_union 4`, `fill`, `breakthrough`, lalu
    `/damage @s 17` sebelum sambaran pertama → tribulasi gagal, darah tersisa setengah hati, totem tidak terpakai.
14. Nether (`Tribulasi Api Neraka`, api di tubuh pemain saja, pelindung wither skeleton/magma cube/hoglin) dan
    End (`Tribulasi Kehampaan`, pelindung endermite/phantom).
15. Lari >6 blok saat pertanda = batal tanpa hukuman; setelah sambaran pertama = gagal. Keluar dunia setelah
    sambaran pertama → setelah masuk lagi kena Penyimpangan Qi.
16. Hardcore: `/kultivasi hardcore on` lalu gagalkan tribulasi → turun satu ranah. Matikan lagi: `hardcore off`.
17. `/kultivasi debug tribulation test` (uji tanpa naik ranah), `/kultivasi debug breakthrough force`,
    `/kultivasi debug breakthrough cancel`.

## D. Meditasi
18. Tekan `;` di tanah → duduk bersila (cek dengan F5): kaki bersilang, tangan di lutut, partikel qi masuk ke perut.
    Berhenti kalau bergerak, terluka, menyerang, memakai item, atau pindah dimensi.
19. Tempat spiritual: `/tp @s ~ 130 ~` (puncak gunung ×1.5), hutan ceri, dekat beacon aktif, 4 balok kecubung, The End,
    bulan purnama (`/time set 18000`). Pengali tampil di action bar dan HUD, maksimal ×4.
20. Formasi: 4 Balok Kecubung sejajar balok yang diduduki, 3 blok ke utara/selatan/timur/barat → Formasi Kecil, garis qi
    mengalir. Tambah 4 di sudut + duduk di Balok Emas → Formasi Agung. Tambah End Rod di atas 4 pilar utama + Beacon atau
    Lodestone di bawah Balok Emas → Formasi Bintang Surgawi. Tidak ada blok yang diubah mod.
21. Urat roh: `/kultivasi debug vein 15` saat meditasi → pesan urat menipis; pindah >64 blok → 100% lagi.
22. Momen Pencerahan: `/kultivasi debug enlighten` → judul "Pencerahan!", halo emas, XP ±25% bar.

## E. Pil vanilla
23. Makan Apel Emas → "+30 Kultivasi, +30 Qi" + partikel emas. Batal makan di tengah = tidak ada efek.
24. Shift + klik kanan ke udara: pecahan kecubung (+15 Qi), botol pengalaman (tidak dilempar, +15), napas naga (+400),
    bintang nether (+1500). Tanpa Shift = perilaku vanilla biasa.
25. Racun pil: 3 bintang nether berturut-turut → peringatan, lalu Mual + Racun 5 detik (tidak mematikan).
    Madu saat Penyimpangan Qi memotong 20% sisa waktu (sekali per penyimpangan).
26. Tooltip item pil menampilkan efek dan racunnya.

## F. HUD & layar kultivasi (gaya giok, beda dari Butter Bot)
27. Tekan `'` → gulungan giok terbuka (animasi gulung), tab berupa 5 loh giok tegak di **kiri**, tombol jimat kuning
    bersegel merah, bunyi denting kecubung. Bandingkan dengan panel Butter Bot: harus jelas berbeda.
28. Tab Status: lingkaran dantian (cincin emas = kultivasi, biru = Qi, ungu = Qi Tertimbun), "Anugerah ranah" berisi
    bonus darah/serangan/kecepatan/armor.
29. Tab Akar & Dao: pentagram lima elemen + Bagua 8 Dao (memilih Dao baru aktif di Fase 2).
30. Tab Teknik: kartu segel (teknik baru berfungsi di Fase 2). Tab Pengaturan: Kualitas Efek Minimal/Normal/Maksimal,
    koin giok untuk aura/jubah/dll, HUD on/off, hardcore (klik dua kali untuk konfirmasi).
31. Aura per ranah (F5): `/kultivasi debug set realm golden_core 1` → partikel naik + 3 titik emas mengorbit.
32. HUD hilang saat F1/F3/chat; tidak menimpa slot offhand; layar tetap muat di GUI Scale 4.
33. Ganti bahasa ke English dan Bahasa Indonesia: tidak ada teks mentah `kultivator.xxx`.
