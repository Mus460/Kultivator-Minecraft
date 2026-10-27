# m2-43B-lain: porter "lain" (hall, hub, screen non-ui, screen/ui self-contained, dunia/tempat/jalan)

Tugas: nol impor `tempat.ui.kit.*` di folder ini. Tanpa Gradle (agen finish membangun). Hanya kompilasi belum diuji.

## Daftar berkas (semua berkas yang mengimpor kit 38 di folder ini)
- [x] `hub/LembarHub.java` (CRLF): `instanceof LayarTempatDasar` diganti kait `Page.batasTab/lihatSaja/tabSekarang`.
      Memuat: chip "bersama X ×", OP_LIHAT 3 s, tab sub saat memuat, kunci/rinci/angka/mundur/fokus: tak berubah.
      Beda satu: tuju-ulang sub hanya bila `tabSekarang()` tidak kosong (dulu LTD tanpa tab bisa memanggil `sub("")`).
- [x] `hall/TreasuryPage.java` (CRLF): `tabBuku()` dan `chipAktif()` memakai `Page.tabSekarang()` (hasil sama untuk lembar).
- [x] `screen/HartaPage.java`: ikut tab lembar Kediaman lewat `Page.tabSekarang()`.
- [x] `screen/Page.java`: kait baru, bawaan kosong: `tabSekarang()` = "", `batasTab(Set)`, `lihatSaja(boolean)`.
      `LayarTempatDasar` sudah punya ketiganya dengan tanda tangan sama (public), jadi menimpa tanpa ubahan.
- [x] `screen/ui/Lembar.java` BARU (LF): salinan `tempat/ui/kit/Lembar` (API sama). 
- [x] `screen/ui/{DaftarPilihan, KartuBarang, KeadaanKosong, PapanSkor}.java`: impor `tempat.ui.kit.Lembar` dibuang
      (Lembar kini satu paket). Tanda tangan tak berubah.
- [ ] `dunia/tempat/jalan/JalanPapan.java`: TETAP memakai `TempatWarna.Gaya.BATU` (papan teks dunia; `TeksDunia.papan` milik
      `tempat/ui` meminta `TempatWarna.Gaya`). Bukan GUI.
- Lainnya di folder: tanpa impor kit 38. Potret, Pratinjau3D, Glyphs, LabelHarga, CincinWaktu, DaftarGulir, KeadaanTerkunci,
  LencanaPangkat, PemilihJumlah, Lukis di `screen/ui` sudah kembar tunggal (hanya komentar sejarah menyebut kit 38).

## Tidak dikerjakan (di luar tugas ini)
Butir audit ulang di folder ini (B-1 panah luapan TabBar, B-2 format j:mm, G-10 uang tanpa LabelHarga, T-1 PANAH, M-1..M-3
kode mati, K-1 JadeSlider): bukan impor kit 38, dibiarkan.

## needs (untuk agen finish)
Lihat balasan porter: (1) tukar impor `Lembar` di 65 berkas + LTD, (2) `TeksDunia`/`JalanPapan` ke `Palette.Gaya`.
