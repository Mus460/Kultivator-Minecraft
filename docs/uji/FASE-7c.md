# Checklist uji Fase 7c — Formasi Sekte

Persiapan: dunia uji dengan cheat aktif, mode Bertahan kecuali disebut lain, punya aula sekte. Aturan, pola, dan bahan
ada di [PANDUAN 20c](../PANDUAN.md#20c-formasi-sekte) (bahan dengan nama blok Inggris di
[20c.9](../PANDUAN.md#20c9-bahan-tiap-formasi-nama-blok-inggris)), rancangannya di DESIGN bagian 24.

Perintah ditulis singkat: `formasi ...` = `/kultivasi formasi ...`, `uji ...` = `/kultivasi uji ...`,
`debug ...` = `/kultivasi debug ...`. Bahan cepat: `uji kit` (kotak shulker "Fase 7c": 8 Mata Formasi, semua balok
jantung, Balok Kecubung, serpihan, Tongkat End, Obsidian Menangis). Tanpa membangun: `uji formasi pasang <id>`.

**Selalu periksa:** tidak ada error di log, tidak ada kunci bahasa mentah (coba Bahasa Indonesia dan English), formasi
tidak pernah memasang, merusak, atau mengubah blok, tidak ada `LightningBolt`, cuaca dan waktu tidak berubah, pemain,
hewan peliharaan, penduduk desa, dan murid tidak pernah terluka. Seluruh Fase 7c **belum pernah diuji di game** (hanya
dikompilasi).

## A. Dasar dan kerangka
1. Simpanan dari fase 7/7b termuat; tidak ada yang berubah sampai sebuah Mata Formasi dipasang. Tablet Formasi muncul
   ketiga (setelah Bangun) dan muat di 320×240.
2. Buat Mata Formasi dari resepnya. `formasi pola small_spirit_gathering` → hologram menandai lapis terendah yang kurang:
   kubus emas berdenyut untuk slot kosong, silang merah untuk blok salah, garis hijau untuk yang benar. **Tidak ada blok
   yang berubah.**
3. Jongkok + pakai mata dengan Serpihan Kecubung pada pola yang belum lengkap → daftar kekurangan berkelompok, penanda
   merah 30 detik, tidak ada yang terpakai. Lengkapi → Pengenalan Darah, status Berdaya, pencapaian `formation_eye`.
4. Bangun K2 diputar 90/180/270° → tetap terdeteksi; versi cermin tidak. M1 yang diputar ditolak (TETAP, hanya arah
   kompas sebenarnya).
5. Cabut satu tiang → Rusak dalam satu tick, pemilik diberi tahu; pasang kembali → menyala lagi. Ledakkan satu tiang
   dengan TNT → Rusak dalam ±10 detik.
6. Klik balok emas, besi, atau magma biasa dengan kecubung → tidak terjadi apa-apa (hanya mata yang memulai deteksi).

## B. Daya, Kas, slot, penjaga
7. Batu Roh Rendah = 2 Daya, Menengah 18, Tinggi 144, Agung 1.296. Inti Bumi menolak kecubung. Batu yang berlebih masuk
   ke Kas. Dengan "Tarik dari Kas", inti yang tersisa < 2 jam terisi otomatis. Faktor urat (Agung/Kaya/Tipis) terlihat
   di baris biaya.
8. Siaga: tanpa monster selama 60 detik → pengurasan ×0,25 terlihat di slip.
9. Aula Tingkat 1 → slot aktif 2; formasi ketiga yang dinyalakan Tertahan "Slot aktif penuh 2/2". `uji formasi aula 9`
   → 6 slot.
10. Penjaga: murid Pemurnian Qi pada Jaga di inti Kuning → biaya 0, "Dijaga". Perkuat → P naik dan murid bermeditasi.
    Waspada → murid hanya bertarung di dalam radius. Inti Langit menolak murid Inti Emas, dengan alasannya. Formasi
    Agung Sekte tetap menjaga penjaga lamanya.
11. Kepemilikan: mata di wilayah pemain lain menolak pendaftaran; teman sekte bisa menyetor tetapi tidak bisa
    mendaftarkan; prasasti Aula ditambang → semua inti sekte Tertahan; dipasang kembali → menyala lagi.

## C. Katalog A
12. K2: meditasi di dalam radius 10 → HUD "Sekte ×1,10". Dengan kursi Bintang Surgawi (×2,2), faktor formasi tertahan
    di 2,6. K2 dan B5 bersamaan → hanya yang lebih kuat yang dihitung.
13. M5: tiga murid bermeditasi di bantal + kamu + penjaga di tengah → faktor 1 + 0,03 × 5. Bonus teman sekte ×1,15
    tampil terpisah di Medan.
14. B5: meditasi di mana pun di wilayah → ×1,20; mata di Y ≥ 120 → ×1,25; pengurasan urat ×0,8. Urat tidak pernah naik.
15. M4: Regenerasi I tiap 5 detik; Penyimpangan Qi selesai 25% lebih cepat; berhenti saat tribulasimu sendiri; monster
    tidak ikut sembuh.
16. K3: peluang di `/kultivasi resep` naik +5%, meracik 8 detik. Bangun B7 di mata yang sama → +10% dan +1 tingkat
    mutu; K3 tidak ikut bertumpuk. Peluang tidak pernah melewati 0,95.
17. B6 lalu `uji tribulasi golden_core` → faktor ×0,90 (×0,85 dengan penjaga Inti Emas). Di dalam Formasi Pelindung
    juga, total formasi tidak pernah di bawah 0,70; tidak pernah mati karena formasi.
18. L1 tanpa Formasi Agung → Tertahan. Dengan Agung → panah, bola api, dan wind charge dari luar lenyap di tepi; panah
    anggota sendiri tidak; creeper di dalam tidak pernah meledak (dengan penjaga Jiwa Baru Lahir); Batu Magnet tidak
    menguras kecubung; tampilan lapisan "6/6". Radius dan lapisan Formasi Agung sendiri tidak berubah.
19. L6: target P +0,25 di slip; cabut satu blok target → "Retak", tetap bekerja 50% selama 10 menit lalu Rusak.

## D. Katalog B
20. K1: `uji formasi musuh 3` → baris peringatan, panah HUD, suara lonceng; tahap mendekat lalu menembus; satu baris
    chat per 5 detik. Di LAN, orang asing memicu alarm. `uji formasi alarm elit` memberi contoh dari arah acak.
21. K4: zombie di luar radius tidak bisa mengincarmu saat kamu di dalam; elit tetap melihat. Di LAN, nama anggota di
    dalam tidak tampak bagi orang luar (tab list masih menampilkannya; ini kosmetik). `uji formasi sembunyi`.
22. M2: seluruh wilayah tersembunyi dan terpantau; elit tingkat 1 pun tertipu; peringatan tepi "Penyusup di tepi …".
23. M1: monster berputar di cincin dengan Lambat I; tiap 60 detik arah berbalik; setelah 20 detik terdorong keluar;
    zombi yang dibelokkan tidak mendobrak pintu.
24. L2: buff monster hilang, keahlian elit diam; tidak ada jalan lurus; keluar setelah 30 detik; bos hanya dilucuti.
25. B4: monster yang melintas masuk terkunci 4 detik (elit 2, bos 1), sekali per 15 detik.
26. B1 di Aula Tingkat 4 → ditolak "butuh Aula Tingkat 5". `uji formasi aula 5` → terdaftar; pedang menyerang ≤ 6
    monster; sapuan pedang tiap 5 detik. `uji formasi pembunuh` menampilkan catatan kerusakan.
27. B2/B3/L3/L4: hanya petir dan api partikel; tidak ada `LightningBolt`, tidak ada api pada blok, tembaga dan air
    tidak berubah. Dua formasi pembunuh pada satu zombie tidak pernah melewati `(2 + 0,5 × Tingkat Aula)` per detik; bos
    ×0,25; Wither dan Naga Ender tidak tersentuh; golem buatan pemain, hewan bernama, dan yang diikat tali tidak
    tersentuh.
28. Tidak ada XP kultivasi, inti binatang, batu roh, atau ketenaran dari kematian karena formasi, meskipun kamu memukul
    monster itu lebih dulu.
29. M3: dua simpul berjarak 16+ blok; jongkok + tangan kosong → peta simpul; perjalanan 3 detik; murid yang mengikuti
    ikut (≤ 4); hewan peliharaan tidak ikut; ditolak saat tribulasi, saat menunggang, atau baru terluka; simpul tujuan
    yang rusak ditolak. `formasi simpul nama Gerbang`, `formasi pindah Gerbang`.
30. L5: perjalanan 1.000 blok memakan 4 Daya dari tangki; chunk tujuan dimuat sebentar lalu dilepas; tidak pernah
    ganti dimensi.
31. A1: tanpa salah satu bagian (Taiji, Liangyi, Sixiang) → tidak lengkap. Lengkap → semua formasi lain P +0,15 dan
    radius +2, batas tumpuk 2,8, mutu terobosan +0,04. `uji formasi resonansi`.
32. A2: `uji formasi mutlak 10` → setiap formasi Mutlak P +0,30, interval pulsa ×0,75, pencapaian `ten_absolute`.

## E. Integrasi dan kinerja
33. Kehancuran: tidak ada kawah dalam radius + 8 dari mata formasi mana pun; mata tidak pernah terukir.
34. Pembangunan Sekte: komponen pola opsional hanya dibangun di dalam tapak, tidak aktif, dengan soket mata kosong;
    Perbaiki memulihkannya; `uji sekte audit` lulus. (Titik Formasi dari cetakan 6b masih kosong, lihat bawah.)
35. `uji formasi pasang <id>` menjalankan setiap formasi tanpa blok; formasi uji hilang setelah restart dan tidak
    dihitung dalam batas. `uji formasi audit` melaporkan nol galat; setiap kartu dan tooltip punya baris Sumber.
36. `uji formasi musuh` hanya memunculkan zombi, husk, tengkorak, dan laba-laba; `uji formasi bersih` menghapus
    semuanya.
37. Kinerja: 6 formasi aktif, 20 monster, 3 elit, dan satu tribulasi → 20 TPS; `debug formasi stats` ≤ 0,15 ms per
    tick; pelambat aktif bila dilampaui. Kualitas Efek Minimal → hologram hanya garis, tanpa kubah, hanya cincin tepi.

## Diketahui belum lengkap
* **Panji Induk Formasi** (Master Flag) belum dibuat; hologram otomatis saat memegangnya juga belum ada. Jongkok sambil
  melihat mata formasi, atau memegang blok polanya, tetap menampilkan hologram.
* XP binatang roh dari M4 belum diberikan.
* Titik Formasi di cetakan sekte (6b) belum diisi, jadi daftar titik di `uji formasi audit` kosong dan talisman
  "Di Titik Formasi…" masih abu-abu.
* `uji formasi daftar` di server khusus bisa menandai kunci bahasa merah padahal tidak hilang (server tidak memuat teks
  mod); di pemain tunggal dan LAN benar.
