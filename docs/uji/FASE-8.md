# Checklist uji Fase 8 — Jalan Dunia Kultivasi

Persiapan: dunia uji dengan cheat aktif, mode Bertahan kecuali disebut lain, tingkat kesulitan **bukan** Peaceful. Aturan
dan resep ada di [PANDUAN 55](../PANDUAN.md#55-jalan-dunia-kultivasi-fase-8), rancangannya di DESIGN bagian 25.

Perintah ditulis singkat: `uji ...` = `/kultivasi uji ...`, `debug ...` = `/kultivasi debug ...`. Bahan cepat:
`uji kit` (kotak shulker "Fase 8": semua kunci alam, bahan alam, Mutiara Es Dingin, Pil Mata Terang, tungku, plakat,
kantong, cincin tiap tingkat, pusaka tiap bentuk Langit +3, Kantong Rampasan, Kuali). Bilah HUD tanpa main:
`uji hud dunia`.

**Selalu periksa:** tidak ada error di log, tidak ada kunci bahasa mentah (coba Bahasa Indonesia dan English), tidak ada
blok yang berubah oleh fitur fase 8, tidak ada `LightningBolt`, cuaca dan waktu tidak berubah, penduduk desa, hewan,
peliharaan, calon murid, dan pemain kreatif tidak pernah diserang. Seluruh Fase 8 **belum pernah diuji di game** (hanya
dikompilasi).

## A. Alam Rahasia
1. `uji alam blood_fire_trial` saat Pemurnian Qi: pindah ke lantai datar dimensi `kultivator:alam_rahasia`, bilah bos
   "Ruang 1/3" dengan waktunya. Gerbang biasa (pakai Token Ujian Darah-Api) memberi omen 10 detik dulu. Saat Inti Emas
   gerbang ditolak "Pembatasan kedua menolakmu".
2. Di dalam alam: tidak bisa memecah/menaruh blok, memakai ember, mutiara ender, korus, wind charge, ranjang, atau peti.
   Kehancuran tidak bekerja. Terobosan, Gua Kediaman, formasi, duel ditolak dengan pesan alam.
3. Pukulan mematikan → keluar dengan 1 HP, Luka Alam 5 menit (kerusakan teknik ×0,8), kunci tetap ada, Totem tidak
   terpakai. `/kill` tetap membunuh; setelah respawn kamu tidak tertinggal di alam.
4. Lari keluar panggung: didorong balik di 32 blok, diteleport ke bantalan di 48. Jatuh di bawah lantai → kembali ke
   bantalan, tidak mati. Dinding labirin yang tak terlihat mendorongmu balik.
5. Keluar game di dalam alam lalu masuk lagi → dikirim ke titik asal pada tick berikutnya tanpa hukuman. Pemain kreatif
   tidak dikirim pulang. Server dimatikan saat di dalam alam → saat masuk lagi juga pulang.
6. `uji alam heavenvoid_hall` (Inti Emas–Penyatuan Dao): Jalan Lava atau Padang Semut Api Besi, Labirin Ilusi (pintu
   palsu −10% HP dan kembali ke awal; Pil Mata Terang menandai pintu asli 2 menit), tiga gerbang batu (satu "tidak ada
   harta"), penjaga dengan bilah bos. Menang pertama memberi Kuali Hampa Langit. Gagal waktu di Jalan Lava (60 dtk) atau
   labirin (3 mnt) mengulang ruang itu, bukan menggagalkan run.
7. Jalan Lava dengan Mutiara Es Dingin di tangan kiri atau Fire Resistance → semburan separuh.
8. `uji alam ancient_god_land`: setiap kristal +0,25 HP permanen (tetap setelah respawn). `uji kristal 71` → kristal
   berikutnya 30 detik. Kristal Es Warisan habis saat menang, tetap ada saat gagal.
9. `uji alam heavenly_tomb 1`, lalu `uji alam ruang`/`selesai`: lapisan 9 memberi penjaga; batu Agung, inti Raja, dan
   lencana hanya pada rekor baru. `/kultivasi alam masuk heavenly_tomb 1` setelah rekor > 1 → separuh Esensi, tanpa
   hadiah 9k.
10. `uji alam ringan nyala` lalu `uji alam blood_fire_trial`: alam turun di sekitarmu (lingkar glyph), tidak ada blok
    berubah. Keluar lingkar > 5 detik menggagalkan run tanpa Luka Alam. Mode Ringan hanya berlaku untuk run berikutnya.
11. Dua murid Dalam+ dalam 16 blok ikut masuk (murid FOLLOW lain tidak). Murid "mati" di dalam → pulang Terluka, tanpa
    Papan Arwah. Setelah server dimulai ulang tidak ada murid tertinggal di dimensi alam.
12. Teman sekte dalam 8 blok mendapat tombol **[Masuk]** di chat saat omen (`/kultivasi alam ikut`).
13. `uji alam slot` mencetak slot dan entitas run; `debug alam info`.

## B. Kultivator Liar dan Serbuan
14. `uji perampok 1` sampai `4` (`uji perampok 3 2 api hantu`): model murid bertopeng, warna trim per tingkat, tingkat 4
    berbilah bos, nama tanpa custom name. Tidak menyerang penduduk, hewan, peliharaan, atau pemain kreatif; tidak membuka
    pintu, tidak memungut barang. `uji perampok hapus` membersihkan.
15. Saat Inti Emas, perampok tingkat 3 (±197 HP) kena aturan trial-mob. Formasi pembunuh 7c mengenai perampok ×0,6;
    kill formasi tanpa XP/jarahan.
16. `uji sergap`: peringatan bahaya 3 detik lalu 1–3 perampok di depan. Menang memberi Kantong Rampasan. Tidak ada
    penyergapan di Peaceful, di perahu, saat terbang, atau dekat desa.
17. `uji serbuan jadwal`, `uji serbuan peringatan` (panji hitam, tidur ditolak di wilayah malam serbuan),
    `uji serbuan mulai` → bilah "Gelombang 1/N" dan bilah merah Segel Penjarah yang naik saat perampok dekat prasasti.
    `uji serbuan segel 90` mengisi segel.
18. Murid GUARD_HALL, Pelindung Sekte, dan formasi ikut bertahan; murid yang "mati" menjadi Terluka. Calon menghilang
    selama serbuan dan kembali sesudahnya. (Murid di pos tugas belum ikut bertarung, lihat PANDUAN 55.12.)
19. `uji serbuan menang` → PK, Kantong Rampasan Sekte Iblis di Lumbung, ketenaran hanya bila murid ikut menyerang.
    `uji serbuan kalah` → paling banyak 3 tumpuk Lumbung hilang (kunci, cincin, pusaka, Kuali aman), Kas Daya terpotong
    sesuai batas, tidak ada blok berubah.
20. Pemilik AFK > 60 detik atau di luar wilayah → `uji serbuan data` menyelesaikan sebagai data sesuai skor pertahanan.
21. 12 perampok hidup di dunia → perampok baru antre. `debug perampok info` pada perampok yang kamu lihat.

## C. Tempa dan Pusaka Natal
22. Tungku di atas magma: "Api Bumi · batas Bumi"; di atas lava pada y < 0: "Esensi Api Bumi · batas Abadi"; tanpa
    apa pun: Api Sejati Bawaan, biaya qi ×2. `debug tempa panas`, `uji tempa panas 5`.
23. Tempa pedang Kuning (pedang besi + 4 iron ingot + inti Rendah): hasil pedang dengan kerusakan dan baris Sumber.
    Gagal → bahan hilang kecuali embrio. Tutup layar di tengah → semua bahan kembali. Pecahkan tungku → tidak ada
    duplikasi. `uji tempa selesai` menuntaskan proses.
24. `/kultivasi pusaka ikat` saat Inti Emas sambil memegang pusaka: meditasi 30 detik, pusaka masuk tubuh dan tampak di
    punggung/orbit. Bonus serangan hanya dengan tangan kosong, mati saat duel, maks +8 melawan pemain.
    `/kultivasi pusaka lepas` → kembali jadi item, deviasi 5 menit, cooldown 1 hari.
25. Tempa Ulang +4 gagal → turun ke +3. +9 warna biru langit. Pedang Dewa Tinggi +12 natal berhenti di serangan 22
    (`uji pusaka pedang 5 2 12`).
26. Roh Buatan (Jiwa Binatang) langsung Roh Dasar, tidak pernah Lengkap. `uji roh 3` / `uji roh umur 1000`: pedang
    menyerang sendiri, tidak pernah mengenai pemain atau murid.
27. Murid dengan bakat Jari Penempa Roh sebagai asisten → kartu pratinjau +0,10.
28. Wajan maks DR 0,25; faktor kaster total ≤ 1,30.
29. Kuali Hampa Langit di tungku panas Langit (Jiwa Baru Lahir+, keahlian ≥ 6): "Buka Tutup" 60 detik → embrio Langit
    Tinggi, pencapaian `cauldron_opened`.

## D. Desa Bawahan
30. Taruh Plakat Pelindung Sekte ≤ 24 blok dari lonceng desa, ≤ 256 blok dari aula level ≥ 2: Malam Penjagaan malam
    berikutnya, lalu desa bersumpah setia (`uji desa ikrar` untuk langsung). Plakat kedua < 96 blok ditolak.
31. Upeti ke Lumbung tiap 3 hari; `/time add` besar paling banyak 3 siklus (`uji desa upeti 3`). Lumbung penuh → sisa
    di buffer `owed`.
32. Kesetiaan ≥ 50 (`uji desa setia 60`) → calon "dari Desa …" dengan Tekad +10 pada Hari Penerimaan.
33. `uji desa minta sarang` → Surat Permohonan dengan jarum menunjuk sarang; bersihkan → kesetiaan +15, harga dagang
    turun sedikit lalu pulih. `uji desa minta binatang` → elite bukan zombi/drowned/husk/illager.
34. `uji desa jarah jauh` tanpa Pelindung Desa → dijarah, upeti berikutnya separuh. `uji desa jarah hadir` → 2 gelombang
    ke plakat.
35. Kehancuran tidak pernah memahat desa bersumpah (radius 48). `debug desa poi` menampilkan lonceng dan penduduk.

## E. Cincin
36. Kantong 9 slot, Cincin Rendah 18 sampai Agung 54 (`uji cincin 4 penuh`). Peningkatan di tungku menyimpan isi dan
    ikatan; inti Ruang +9 slot.
37. Pemain lain tidak bisa membuka cincin terikat (op: `uji cincin buka`). Cincin jatuh tahan lava/ledakan, tidak hilang,
    hanya pemilik yang bisa memungut; jatuh ke void → kembali ke pemilik.
38. Cincin, shulker, dan bundel ditolak di dalam cincin. Saat terbuka: membuang, tukar offhand, atau angka hotbar ke slot
    cincin ditolak. Mati dengan layar terbuka → layar tertutup, tidak ada duplikasi.

## F. Lem progresi
39. `uji jalan bab 5` lalu naik ke Fondasi → pelacak panduan muncul lagi untuk bab 6. Hadiah bab 6–10 sekali saja.
40. Ujian Fondasi menampilkan baris ke-4; tiga dari empat baris sudah "Fondasi Kokoh".
41. Esensi Energi Makam di tas saat memadatkan bintang → biaya −2% per esensi (maks −20%), esensi habis.
42. Resep alternatif (Herba Fondasi → Pil Fondasi, Kayu Pemelihara Jiwa, Darah Dewa Kuno, Esensi, Pil Mata Terang)
    muncul di Kitab Alkimia.

## G. Keamanan dan performa
43. `grep setBlock` di paket `alam`, `musuh`, `tempa`, `desa` kosong.
44. Serbuan 8 musuh dengan formasi aktif: server tetap mendekati 20 TPS. Dimensi alam yang kosong tidak menambah beban.
45. Setiap item, tingkat perampok, alam, ruang, tingkat pusaka, tahap roh, cincin, dan mekanik desa punya baris Sumber.
46. Tidak ada layar "Experimental settings" saat dunia dimuat; dimensi alam memakai waktu overworld.
