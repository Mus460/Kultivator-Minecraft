# m2-AU-kit: perbaikan audit ulang GUI 43, irisan "kit"

Sumber: side-session-notes `gui-audit-ulang-kit.md` (commit 1e305fa0). Berkas irisan: `client/screen/ui/**` dan
`client/tempat/ui/kit/**`. Tanpa Gradle, tanpa commit. 42-G2 dan `*.deactivation` tidak disentuh.

## Daftar centang
- [x] B-1 TabBar: penjepit `awal` (batang mendatar) dan `jagaAktif` (rel tegak) hanya jalan saat id aktif berubah
      (`aktifTerakhir`/`aktifBaru`), jadi panah luapan tidak dibatalkan bingkai berikut
- [x] T-1 TabBar `PANAH = TalismanButton.MIN_W`, rel tegak `PANAH_TEGAK = MIN_H`; PemilihOrang zona centang dari `x0`, lebar >= 16
- [x] K-1 JadeSlider: jembatan vanilla (Renderable/GuiEventListener/NarratableEntry, fokus, narasi, getRectangle) dibuang;
      `active` tinggal sebagai shortcut yang dibaca `enabledWhen(() -> active)` sampai TitahScreen pindah (needs)
- [x] K-3 PemilihOrang javadoc + 43.7 baris Pemilih orang: `hall/CandidateCard` tetap kartu milik `CalonPage` (pilihan "perbaiki doc")
- [x] M-1 `KeyHints.SealCorner` keluar dari KeyHints; kodenya disimpan di `screen/ui/SealCorner.java.deactivation`
- [x] M-2 `JadeCoin.askingWhen` + medan `asking` + denyut dihapus
- [x] M-3 dihapus: `Eased.inOut`, `KeyHints.hasToken`, `JadeSlider.stepValue`/`isHovered`, `TeksTempat.bertandaWarna/batuDiTas/persen/samar`,
      `PanelTempat.gambar/bingkai/awan/tekstur`, `IkonTempat.lentera`, `IkonTempat.AWAN` (yatim setelah `awan`)
  - lewati: tekstur `textures/gui/tempat/panel_*.png` TIDAK dibuang: masih dipakai `tempat/ui/TeksDunia.java:280`
    (panel teks dunia) dan `PapanHidupTeksRenderer`. `awan.png` dibiarkan (keluaran `tools/gen_tempat_ui.py`, tanpa pemakai).
- [x] G-5 (bagian irisan) `TeksTempat.angka/singkat/desimal1/nb/bertanda/rincianBatu/waktu/hari` kini hanya meneruskan ke `Brush`
      (satu implementasi, kunci lang sama `kultivator.tempat_ui.*`); pemanggil pindah ke `Brush` = needs
- [x] M-4 50 kunci `kultivator.hub.card.*` x 2 bahasa (100 baris) dibuang dari 21 fragmen lang-src (hub.* sudah dibuang irisan inti);
      JSON divalidasi, nilai lain tak berubah. Generator `tools/gen_*_lang.py` masih menulisnya bila dijalankan ulang.

## Butuh berkas irisan lain (needs)
- [ ] G-1 kit 38 kedua: `isi`/`isiGabung` 16 lembar ke `screen/ui/Lukis` (sekte/gabung/ui/layar/*, tempat/ui/layar/*, tempat/ui/tema/LayarBukuDagang);
      sesudahnya `.deactivation` 10 kembar + mesin LTD (`zonaTerakhir`, `daftarTerakhir`, `pratinjauTerakhir`, `ditekan`, `diseret`, `diputar`)
- [ ] G-2 `TombolAksi` -> `TalismanButton.gambar` (ikut G-1: tanda tangan butuh `screen/ui/Lukis`)
- [ ] G-3 `KotakCari`: LayarPaviliun -> `Saring`/`InkField.cari`, lalu cabang LTD dan kelasnya
- [ ] G-4 `TabLentera.Tab` -> `TabBar.Tab` di 36 lembar
- [ ] G-5 sisa: pemanggil TeksTempat/TempatWarna.Gaya/IkonTempat/PanelTempat
- [ ] G-6 TemaUi overload kit 38; G-7 Diplomasi DaftarSekte; G-8 Bilah tangan + PetaUi; G-9 Mandiri; G-10 LabelHarga;
      G-11 CodexGrid; G-12 Kembali ganda; B-2 format waktu; K-1 TitahScreen `enabledWhen`; K-2 Saring; K-4 Lelang; T-2 Yamen; T-3 HUD
