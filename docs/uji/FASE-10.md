# Checklist uji Fase 10 — Dampak Langit

Persiapan: dunia uji dengan cheat aktif, mode Bertahan kecuali disebut lain. Aturan dan pengaturan ada di
[PANDUAN 57](../PANDUAN.md#57-dampak-langit-fase-10), rancangannya di DESIGN bagian 28.

Perintah ditulis singkat: `uji ...` = `/kultivasi uji ...`. Perintah client `/sinematik dampak ...` tidak butuh cheat.
Pemain uji: `uji ranah true_immortal`, lalu pelajari pamungkas Abadi/Dewa (atau langsung `uji dampak <id>`, yang memakai
runner `uji teknik pamer`: tanpa biaya dan tanpa kerusakan). Untuk Perjalanan Langit pasang **Kamera sinematik: Penuh**
(bawaannya Singkat).

**Selalu periksa:** tidak ada error di log, tidak ada kunci bahasa mentah (coba Bahasa Indonesia dan English), tidak ada
blok yang berubah tanpa Mode Kehancuran, tidak ada `LightningBolt`, cuaca, waktu dan tick-rate tidak berubah. Seluruh
Fase 10 **belum pernah diuji di game** (hanya dikompilasi).

## Daftar uji

1. Tanpa Mode Kehancuran: `uji dampak semua` → tidak ada satu blok pun berubah (bandingkan tangkapan layar tanah
   sebelum/sesudah dan cek F3); puing, sel terangkat dan kawah hanya visual lalu hilang.
2. Tanda Langit muncul di titik bidik sebelum setiap pamungkas Abadi/Dewa, ikut crosshair saat mengisi (Sembilan
   Matahari, Diagram Taiji, Kunpeng) dan berpindah ke titik server saat dilepas.
3. Pemain lain melihat pilar dan cincin tanda; dengan "Efek pemain lain: Ringan" hanya cincin; "Mati" tidak ada.
4. Perjalanan Langit (Penuh, Pertama): Pedang Hukuman Langit naik → awan menutup layar → gerbang langit → awan → turun
   mengikuti pedang → bingkai dampak tepat saat beam mendarat; total ≤ 4 detik.
5. Perjalanan tidak dimulai bila ada musuh ≤ 24 b, nyawa < 50 %, sedang jatuh, di air/lava, menunggang, atau ada layar
   terbuka; Singkat memberi crane pendek; Mati tanpa kamera.
6. Selama perjalanan: tombol gerak menghentikan shot, Backspace melewati, terkena serangan langsung kembali ke mata.
7. Gerak kamera tidak memusingkan; tidak ada lompatan kamera yang tidak tertutup awan atau bingkai.
8. Bingkai dampak Lembut (bawaan): satu kilatan ≤ 0,35, tanpa pembalikan warna.
9. Mengganti ke Penuh membuka layar peringatan fotosensitif; Batal tetap Lembut; Aktifkan memberi 3 bingkai (balik
   warna, warna Dao, memudar).
10. "Hide Lightning Flashes" vanilla atau "Kilatan layar" mati → tidak ada bingkai sama sekali.
11. Dampak sunyi: pukulan mendarat tanpa suara, dentuman datang setelah jeda; makin jauh makin lambat
    (`/sinematik dampak sunyi 8` lawan `48`); dengan "Dampak sunyi: Mati" suara langsung.
12. Dentuman tetap terdengar walau "Efek pemain lain: Mati"; suara musuh (HOSTILE) tidak pernah diredam.
13. Hit-stop: pose, model 3D dan puing berhenti sesaat saat klimaks; mob, partikel vanilla dan damage tidak.
14. Dengan Mode Kehancuran: puing berupa blok asli kawah beterbangan, berputar, memantul sekali, lalu hilang.
15. Kawah Lebur Api: dasar/bibir kawah jadi blackstone/basalt/magma; tidak ada magma dalam 6 b dari posisi casting;
    Pulihkan mengembalikan semua blok, termasuk yang berubah.
16. Kawah Es: hanya packed ice dan snow block (tidak ada es biasa yang mencair, tidak ada blue ice).
17. Tidak ada blok berubah di zona terlindung, dekat air/lava, dekat chest/blok entitas, atau di kolom aman kastor.
18. Sigil Bumi: Biduk Utara pada Langkah Penginjak Langit, satu bintang menyala tiap langkah, garis mengikuti bukit dan
    lubang; dengan Kehancuran garis menjadi parit menyala dan total blok tidak melebihi batas per casting.
19. Lubang Hitam: grid cahaya memotong tanah, sel terangkat berputar masuk ke inti; Minimal hanya partikel.
20. Monumen: pedang tertancap bertahan 180 s, berpendar pelan, lalu memudar; tidak ada blok sisa; paling banyak
    1 / 2 / 4 monumen hidup sesuai kualitas.
21. Setiap pemotongan Mode Kehancuran (termasuk Jurus Pemusnah murid) mendapat gelombang kejut dan puing.
22. Laptop lemah (Minimal atau Otomatis): FPS tidak jatuh di bawah 30 lebih dari sesaat saat `uji dampak semua`;
    penjaga frame menurunkan tingkat bila perlu.
23. Sodium + Iris (Complementary): sigil dan cincin terlihat, tidak masuk bayangan, tidak berkedip (z-fight).
24. Server: `uji dampak puing 256` → paket ≤ 64 entri, ≤ 1 per job per tick, hanya ke pemain dalam 96 b.
25. Ganti dimensi, putus koneksi, `uji dampak berhenti` → semua puing, sel, sigil dan monumen hilang.
26. Teks: setiap kunci `dampak.*.json` dan `dampak_server.*.json` ada di en_us dan id_id dengan kunci yang sama;
    `uji teknik audit` tidak melaporkan peringatan Dampak.
