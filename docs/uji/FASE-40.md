# Checklist uji Fase 40 — Penampilan dan Animasi (final F3)

Ditulis F2, dirapikan F3 setelah W3 (D-40K, D-40G; build B3 hijau, `cek_sosok` 0/0). Sumber: DESIGN 40.7.6
(`docs/rancangan/40-penampilan-dan-animasi.md`). Belum ada yang diuji di game. Dunia uji baru dengan op, di laptop pengguna, setelan Sedang kecuali disebut. Perintah di bawah
`/kultivasi uji penampilan ...` dan `/sosok ...`.

**Selalu periksa:** tidak ada error di log (khususnya `IllegalArgumentException` dari `SektePoses.register`), tidak ada
kunci bahasa mentah, Sodium/Iris tidak berkedip.

## Identitas
- [ ] Rekrut 10 murid: kelamin tercampur; `grunt.female.*` hanya pada murid perempuan; nama dari kolam yang benar.
- [ ] `galeri usia warga`: anak setinggi +-1.2 blok berkepala besar, perempuan lebih ramping, tua membungkuk; F3+B: kotak
      tabrak anak kecil.
- [ ] `murid usia anak`, tunggu 48 hari dunia (`/time add`): anak menjadi muda; murid Fondasi tidak menua.
- [ ] Simpan-keluar-masuk: rupa murid sama persis; Prasasti Jiwa: murid yang dibangkitkan berupa sama.
- [ ] NPC kota: Meng Po perempuan, Raja Yama laki-laki, rahib laki-laki gundul, penari gurun perempuan.
- [ ] Alkemis saingan Pertemuan Pil: nama di papan cocok dengan kelamin tubuhnya.

## Kulit dan busana
- [ ] `galeri peran` semua halaman: tidak ada kulit Steve/Alex/Ari; tiap peran punya busana, topi, prop berbeda
      (termasuk peran kota tema: syahbandar, penari gurun, nyonya sutra, biksu gunung, penegak iblis).
- [ ] `acak 32`: 32 wajah berbeda; ulangi: 32 lain.
- [ ] `galeri murid`: pangkat terlihat dari tepi, hiasan rambut, lencana, jubah luar; Tetua Agung bermahkota teratai.
- [ ] Rancang seragam sekte (36): murid Inti memakai warna, potongan, lambang punggung baru dalam 1 detik.
- [ ] `galeri dao`: iris ambar (Api), biru pucat (Es), ungu (Petir); tanda Dao di dahi dari Jiwa Baru Lahir.
- [ ] `galeri fisik`: dua pupil emas pada murid Pupil Ganda dari 2 blok.
- [ ] Kerah silang orang hidup kanan-ke-kiri (panel kiri pemakai di atas); arwah kebalikan.
- [ ] Topi menutup sanggul; ekor kuda tetap terlihat di bawah helm.
- [ ] `/sosok dump` sebuah benih dan `python -I tools/gen_sosok_pratinjau.py --benih` yang sama: PNG identik.
- [ ] Jalan hidup 39 terlihat di busana (alkemis, pedagang, tabib, iblis, ...).

## Animasi
- [ ] `klip semua`: setiap klip diputar tanpa pose rusak (lengan memutar 360 derajat, kepala terbalik).
- [ ] `jalan`: 14 profil berbeda; tua bertongkat; anak melompat; perempuan langkah pendek.
- [ ] `wajah`: 16 strip mata dan 16 strip mulut; kedip acak; bicara mengikuti suku kata.
- [ ] `pegas`: rambut, ekor kuda, lengan lebar, jubah luar, selendang bergerak tertunda dan berhenti halus.
- [ ] Murid tugas air/kebun/dapur/ternak terlihat sama seperti sebelum 40, dengan prop lama.
- [ ] Aktivitas Sekte 13-24 dan Sekte Langit 25-27: pose lama, tanpa klip lain bertumpuk.
- [ ] Teknik (cast) saat bekerja: pose cast menang, lalu kembali ke kerja dengan halus.
- [ ] Duel pemain vs pengembara: pose duel 35 penuh, wajah marah; setelah duel sikap gagang kembali.
- [ ] Murid perempuan non-bela-diri memberi hormat wanfu; murid pedang perempuan baoquan.
- [ ] Meditasi: duduk bersila, napas dalam, mata tertutup; terobosan berhasil: lengan terangkat, tertawa; gagal: memegang
      dada, batuk darah, berlutut.
- [ ] HP murid < 35 %: pincang dengan wajah sakit.
- [ ] Murid mandiri 39: aktivitas 40-60 menampilkan klipnya (latih jurus, meramu bersila dengan kipas dan asap, menulis
      jimat, tanding bersinkron, tidur/bersandar, berdiri di pedang, perseteruan 57-60); prop tanah (kuali, meja jimat,
      pedupaan) berdiri di depan kaki, pedang terbang di bawah kaki.
- [ ] Warga kota tema: penari gurun berputar saat Tari Putar, pendayung mendayung, kuli memikul, penegak iblis berjaga.

## Performa dan kompatibilitas
- [ ] Ibu kota 38 atau kota tema ramai: `/sosok info` <= anggaran; FPS turun < 10 % dibanding Rendah.
- [ ] Detail animasi Tinggi/Sedang/Rendah/Otomatis berubah langsung; Ekspresi wajah mati -> wajah diam.
- [ ] Turnamen 36 dengan 160 penonton: tidak ada penyusunan atlas baru (`/sosok info` antrean 0).
- [ ] Sodium + Iris (paket shader umum): tekstur benar, bayangan NPC ada, tidak ada kedip; shadow pass tanpa wajah/prop.
- [ ] Player Animation Library + `duel_pal`: animasi PAL pemain normal, murid tetap memakai pose 35.
- [ ] Keluar dunia lalu masuk dunia lain: `/sosok info` atlas 0 sebelum NPC terlihat.

## Tambahan gelombang detail W3
- [ ] Busana: lipatan kain (drape) terlihat pada jubah dan rok; rambut punya kilau; pratinjau Python dan game cocok untuk benih
      yang sama (`pratinjau_benih`).
- [ ] Prop pinggang (pedang, botol pil, sempoa, gulungan) di sisi pinggang (+-2.6, 10.5, -2.6)/16, tidak di dalam lengan;
      prop bendera TEGAK tetap tegak; prop depan kaki tetap di lantai saat duduk atau jongkok.

## Belum (untuk gelombang berikut)
- Drape belum untuk lembar tambahan (bepergian, kerja, zirah, tidur), kain kepala dan alas kaki.
- Rambut hitam masih berkilau biru-abu (rumus palet di Python dan Java `RupaResolver` harus diubah bersama).
- Varian lari anak `lari_riang` belum; kekakuan rok zirah 1.6x belum.
