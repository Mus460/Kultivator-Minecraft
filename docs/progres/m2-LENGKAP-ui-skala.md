# m2-LENGKAP-ui-skala (builder ui-skala)

Tugas: Tempa dan Cincin muat di GUI skala 4 (1920x1080) dan skala 3 (1366x768); `uji ui skala` menskalakan lagi seperti
yang didokumentasikan (43-satu-gui 43.0.5: berlaku untuk seluruh Gulungan Utama); periksa lembar `screen/ui` lain.
Belum ada build atau uji game (tanpa Gradle): semua periksa manual terhadap `reference/mcsrc`.

## Ukuran yang dipakai
- 1920x1080 skala 4 = 480x270, 1366x768 skala 3 = 455x256: Forge (270x214 + rel 22) dan Cincin (190 x 226 pada 6 baris)
  sudah muat. Yang tidak muat adalah 1366x768 skala 4 (341x192, target 43.0.6): Forge 214, Cincin 5 baris = 208, 6 baris = 226.
- Slot menu bersifat `final` (`Slot.x/y`) dan menu ada di `main`, jadi tata slot tidak bisa dipindah di klien: seluruh panel
  yang digeser (kotak slot ikut `topPos`).

## Daftar
- [x] `tempa/TempaGeser.java` (baru, LF): geser vertikal panel layar wadah yang lebih tinggi dari jendela. Roda tetikus
      (setelah daftar kartu dan setelah roda bundel di slot), Page Up / Page Down, dua segel panah di tepi kiri jendela
      (tombol 16x12, Tab menjangkau, tooltip), dan Tab ke kontrol yang di luar layar menggeser sampai terlihat (sekali per fokus
      baru). Muat di jendela: panel tetap di tengah dan tidak ada yang digambar (perilaku lama tidak berubah).
- [x] `ForgeScreen`: `tataUlang()` (leftPos/topPos/posisi widget) dipanggil dari `init()` dan setelah tiap geseran; kotak `Fokus`
      = bagian panel yang terlihat (konfirmasi "Runtuhkan Ruang" di tengah layar).
- [x] `RingScreen`: sama; juga `mouseScrolled` (sebelumnya tidak ada).
- [x] `uji ui skala N [id]`: tingkat Gulungan, bukan lagi hanya kotak lembar. `Page.ujiSkala()` (default 0; `LayarTempatDasar`
      mengembalikan `uji_skala` lembar), `Page.render` memberi tahu `CultivationScreen.ujiLihat(n)`; host membaca di bingkai
      berikutnya, mengukur `width/height` seperti GUI skala N pada jendela ini (minimum 160x120), menggambar dengan
      `pose().scale(k)` (latar dan isi) dan membagi mouse dengan k (klik, lepas, seret, gulir). Meninggalkan lembar uji
      mengembalikan ukuran asli bingkai berikutnya. Dipakai juga oleh `gabung skala <n>` (kunci `uji_skala` yang sama).
- [x] lang: `lang-src/gui43_skala.{id_id,en_us}.json` (2 kunci `kultivator.gui43.geser.atas|bawah`, LF).
- [x] Periksa lembar lain di 341x192: host (isi 299x141, 299x128 dengan bilah sub, audit ulang inti sudah menghitung), `SegelKonfirmasi`
      (tinggi dibatasi ke kotak), `PemilihOrang` (min(220, kotak - 16)), `Pratinjau3D`/`KeadaanKosong`/`PapanMisi`/`Saring` mengikuti
      kotak, halaman `screen/` (Status, Akar Dao, Jalan, Alkimia, Ensiklopedia, Pengaturan) menghitung dari `w/h` dengan
      daftar gulir. Tidak ada luapan baru yang ditemukan lewat pembacaan; Forge dan Cincin adalah satu-satunya yang tetap.
- [ ] Belum diuji di game (tidak ada runClient): lihat daftar uji di bawah.

## Keputusan kecil
- Margin atas 4 px saat digeser (rod gulungan menjorok 5 px di atas panel); jangkauan geser = tinggi panel + 8 - tinggi jendela
  (Forge 30 px, Cincin 6 baris 42 px pada 192).
- Lebar: jendela lebih sempit dari 292 (skala 5 pada 1366) tidak digeser mendatar (di luar 43.0.6).
- `/kultivasi uji ui skala` untuk Forge/Cincin tidak ada (bukan lembar); cukup ganti skala GUI di Pilihan Video.

## Uji game (untuk orkestrator)
1. 1366x768, GUI skala 4: buka Tempa (klik kanan tungku) dan Cincin 5-6 baris: segel panah di kiri, roda, PgUp/PgDn, Tab ke
   Hapus Jejak; semua slot terjangkau; skala 3 dan 2: tidak ada segel, panel di tengah.
2. `/kultivasi uji ui layar toko_senjata` lalu `/kultivasi uji ui skala 4 toko_senjata`, `... 2`, `... 3`: Gulungan menata ulang
   dan memperbesar, klik/gulir tepat; keluar ke bagian lain: ukuran normal kembali.

## Butuh dari modul lain
Tidak ada. (Opsional: `ForgeMenu`/`RingMenu` bisa memadatkan tata slot agar tidak perlu menggeser; di luar tugas ini.)
