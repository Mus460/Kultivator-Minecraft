# m2-F43-AU: finish perbaikan audit ulang GUI 43

Masukan: laporan fixer irisan inti, kit, sekte, dunia, disini (`m2-AU-*.md`). Langkah ini menerapkan butir "needs" lintas
irisan yang kecil dan pasti, lalu membangun. Tanpa commit, tanpa salin ke `.minecraft`. 42-G2 dan `*.deactivation` tidak disentuh.

## Hasil build
- `gradlew compileJava compileClientJava jar` (varian /low): **BUILD SUCCESSFUL**; mergeLang lulus (tanpa kunci ganda/hilang).
- Jar: `%TEMP%\kultivator-F43-AU.jar`.
- Galat yang diperbaiki saat build: `SettingsRows` tidak public (dipakai `HudHider`, `SlotSisi` dari paket lain);
  `TreasuryPage.showSub` menulis ulang parameter yang ditangkap lambda.
- Cek kunci lang: tidak ada literal kunci penuh di `src` (di luar kota42) yang hilang dari lang-src; 193 kunci yang dibuang
  fixer dicek terhadap awalan dinamis, tidak ada yang masih dipakai.

## Butir needs yang dikerjakan
- [x] inti: `UiTempatClient.rumah/terima` hanya memakai `HubPages.rumah`; `LembarHub.rumah` dihapus.
- [x] inti: `TreasuryPage.sembunyikan` dihapus, anak cukup `onHide()` (pengaman ada di `LembarHub.Halaman.onHide`).
- [x] inti: `SettingsRows.openKeyBinds` memanggil `HubNav.kembaliSetelah(KeyBindsScreen.class)`: konteks Di Sini bertahan.
- [x] kit K-1: `TitahScreen` memakai `enabledWhen(() -> open)`; `JadeSlider.active` dihapus.
- [x] kit G-5 sisa: semua pemanggil `TeksTempat.angka/singkat/desimal1/nb/bertanda/rincianBatu/waktu/hari` di luar kota42
      memanggil `Brush` langsung (16 berkas); penerus di `TeksTempat` tinggal untuk kota42 (shortcut tercatat).
- [x] sekte #5 / dunia G-3: `GulirIsi` pindah ke `screen/ui` (satu gulir isi kit; 16 pengimpor diperbarui).
- [x] sekte #12: zona yang tergulir habis keluar kotak `GulirIsi` tetap di cincin Tab sebagai fokus-saja
      (`Lukis.klipGulir`, `Zona.terpotong`; tidak bisa disorot/diklik); `GulirIsi.ikutFokus` menggulir sampai zona tampil.
      Hanya klip `GulirIsi` teratas: daftar bergulir lain di dalamnya tetap membuang zonanya.
- [x] dunia K-5: Keabadian › Lelang terkunci sebelum Kenaikan (`LayarLelang.kunciDari(AbadiClient::kunciNaik)`, hanya
      jembatan Abadi; Balai Lelang kota dan Juru Lelang K tidak terkunci).
- [x] sekte #3/#11: ubahan server dicatat di 43.14 sebagai S6 (Tangga `soul`/`eid`) dan S7 (blok `turnamen` papan sekte).

## Butir needs yang belum (pekerjaan tersendiri, satu lembar per langkah dengan kompilasi)
- [ ] kit G-1/G-2/G-4, sekte #7, dunia G-4/K-4, disini #10: lembar kit 38 (sekte/gabung/ui/layar/*, tempat/ui/layar/*,
      `LayarBukuDagang`) ke `screen/ui.Lukis` + `TabBar.Tab` + `TalismanButton`; sesudahnya `.deactivation` 10 kembar
      kit 38, `TombolAksi`, `TabLentera`, mesin LTD. Kit 38 juga dipakai kota42 (G2, tidak boleh disentuh).
- [ ] kit G-3: `LayarPaviliun` ke `Saring`/`InkField.cari`, lalu `KotakCari` (ikut port lembar di atas).
- [ ] kit G-6..G-12, B-2, K-2, K-4, T-2, T-3: ikut port kit 38 yang sama.
- [ ] sekte #9: `PapanMisi` butuh imbalan PK sebelum `LayarPapan` pindah.
- [ ] disini #12: uji skala UI untuk lembar kit tunggal (skala di `Page.render` + transform tetikus) belum.
- [ ] dunia: baris sumber `abadi.lelang.source` di LayarLelang (kontrak lembar tanpa baris kaki); kunci dibiarkan.
- [ ] sekte #13, P4: uji tata 289x148 skala 4 butuh klien berjalan.
- [ ] K7 (43.17): Teh/Gelap/Misi/Pertemuan kota K menunggu keputusan pengguna.

## Belum diuji
Hanya kompilasi. Tidak ada uji di game: Tab ke tombol tergulir, konteks Di Sini lewat layar tombol vanilla, kunci Lelang Abadi.
