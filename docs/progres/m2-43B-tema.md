# m2-43B-tema (porter "tema": tempat/ui/tema -> kit tunggal screen/ui)

Berkas: `src/client/java/dev/kultivator/client/tempat/ui/tema` (23 berkas Java: 20 lembar tema, `TemaHalaman`, `LayarTemaDasar`, `TemaUi`, `TemaUiClient`).
Tidak ada Gradle di sini (agen finish yang membangun). Akhir baris LF dipertahankan (semua berkas LF). Hanya folder ini yang disentuh.

## Keputusan
- Lembar tema tetap turunan `LayarTempatDasar` lewat `LayarTemaDasar` (pembantu model-lembar sesuai 43.6: lembar server, tab, op, strip, kunci;
  bukan widget). Semua gambar lewat `TemaHalaman` -> `screen/ui.Lukis` + `Page.fokusRing`. Tidak ada lagi `Lukis`, `TombolAksi`,
  `KotakCari`, `TabLentera`, `DaftarGulir`, `PemilihJumlah`, `LabelHarga`, `KeadaanKosong`, `Pratinjau3D`, `IkonTempat`, `PanelTempat`,
  `TeksTempat` kit 38 di folder ini.
- `LayarBukuDagang` (satu-satunya yang menggambar dengan Lukis kit 38) kini `extends TemaHalaman`; `gambar` -> `lukisTema`.
- `TabLentera.Tab` -> `TabBar.Tab`. Ikon slot tab tidak pernah tergambar (`subs()` hanya memakai id/label/kunci), jadi
  `tabTema(id, kunciLabel)` tanpa parameter ikon.
- `TempatWarna.BAHAYA/OK/PERINGATAN/EMAS_*/terkunci` -> `Palette.*`. `TempatWarna.Gaya` tinggal hanya sebagai tipe konstruktor/bidang
  `LayarTempatDasar` (lihat needs).
- `tt()` (teks `kultivator.tempat_ui.*`) pindah dari `TemaHalaman` ke `LayarTemaDasar` (pengganti `TeksTempat.k`).
- `gambarKosong()` (tipe kit 38) diganti `kosongGambar()` (tipe `screen/ui.KeadaanKosong.Gambar`, di `TemaHalaman`; Mural + BukuDagang menimpa).
- Jembatan ke mesin LTD dibuang: `LayarTemaDasar.isi(kit Lukis)` / `gambar(kit Lukis)`, `TemaHalaman.gambar(kit Lukis)`; `drawBehind` kini
  memanggil `olah()` (hook `lembarBaru`/`tabGanti`) langsung. Overload kit 38 `TemaUi.meter/chip/lambang`, `LayarTemaDasar.tengah/pita`
  (kit 38) dan `TemaUi.tintaSumur` (tanpa pemanggil) dibuang.

## Daftar periksa
- [x] LayarTemaDasar
- [x] TemaHalaman
- [x] TemaUi
- [x] LayarBukuDagang (lihat "Tidak ada yang hilang" di bawah)
- [x] LayarBait, LayarDokumen, LayarGalangan, LayarHerba, LayarIrama, LayarKapal, LayarKitabTerlarang, LayarKontrak, LayarLift,
      LayarMural, LayarPasarGelap, LayarRacik, LayarSutra, LayarTaruhan, LayarTawanan, LayarTawar, LayarTeh, LayarZiarah
      (hanya tab/impor; isinya sudah di kit tunggal lewat TemaHalaman)
- [x] TemaUiClient (tanpa perubahan)

## Tidak ada yang hilang (LayarBukuDagang; sisanya tidak berubah perilaku)
Tab Pasar/Harga/Laba/Kontrak (Pasar hanya bila ada blok pasar), tooltip strip per tab, kertas lebar 620, keadaan kosong (gulungan).
Harga: kepala kota (lambang, nama, tooltip nama/tema/jarak), panah halaman kolom (kini `TalismanButton` "<" ">" 16x12, di cincin Tab),
daftar barang dengan sel harga (penanda asal/terbaik, abu = tua, merah = jenuh, tooltip sel), legenda + tooltip, baris rute, kolom nama
yang menggeser kolom dengan Kiri/Kanan (zona kini ikut cincin Tab; sebelumnya `fokus=false` sehingga tombol itu tidak pernah terjangkau).
Laba: label NB mingguan, "dari batas", bilah (`Bilah`), pita "lewat", uraian, laba seumur hidup + bilah + tooltip Saudagar atau chip,
tujuh batang hari + tooltip, kartu dunia gelap (Infami, bilah, uraian, Gentar). Kontrak: daftar, kartu rincian, chip kapal, uraian.
Pasar: tenda + papan nama, pita lewat, tiga segmen Beli/Jual/Segel dengan jumlah dan tooltip (kini `TalismanButton` terpilih),
daftar, pratinjau 3D, `PemilihJumlah`, tombol Beli/Jual/Segel (`tombolOp`), baris uang, Enter pada daftar = op baris.
Perubahan rupa kecil yang disengaja: ikon kecil di tombol segmen hilang (TalismanButton tidak memuat ikon); segmen kosong tidak
dipucatkan; `LabelHarga` mingguan 1.5 -> 1.0 (kit tunggal hanya 1.0).

## Sisa dari kit 38 di folder ini (sengaja)
- `tempat.ui.kit.LayarTempatDasar` (hanya `LayarTemaDasar`): basis lembar yang dipertahankan 43.6 (pembantu `Page`, bukan basis kedua);
  mesin Lukis-nya yang harus pergi (lihat needs).
- `tempat.ui.kit.Lembar` (19 berkas): pembaca lembar; 43.7 menyebutnya tinggal di samping LTD (bukan widget).
- `tempat.ui.kit.TempatWarna` (hanya `.Gaya` pada konstruktor lembar + bidang `gaya`): mengikuti tanda tangan LTD; kembar `Palette.Gaya`.

## Needs (di luar folder; kode persis)
1. `tempat/ui/kit/LayarTempatDasar.java` (agen finish, saat mesin kit 38 dipensiunkan). Folder tema mengandalkan kontrak ini:
   - Tab memakai `TabBar.Tab` (hapus `TabLentera`):
     ```java
     import dev.kultivator.client.screen.ui.TabBar;
     protected List<TabBar.Tab> tablet = new ArrayList<>();                 // ganti `protected final TabLentera tablet`
     protected abstract List<TabBar.Tab> tabs();
     // terima(): tablet = saringTab(tabs());      batasTab(): tablet = saringTab(tabs()); pastikanTab();
     private @Nullable TabBar.Tab tabDari(String id) { for (TabBar.Tab t : tablet) { if (t.id().equals(id)) { return t; } } return null; }
     private void pastikanTab() { if (tab.isEmpty() || tabDari(tab) == null) { gantiTab(tablet.isEmpty() ? "" : tablet.get(0).id(), false); } }
     private boolean tabBoleh(String id) { TabBar.Tab t = tabDari(id); return t != null && t.kunci() == null; }
     // saringTab: out.add(alasan == null ? t : t.terkunci(alasan));  (tipe List<TabBar.Tab>)
     // subs(): for (TabBar.Tab t : tablet) subs.add(new Sub(t.id(), t.label(), t.kunci()));  (tablet.size() < 2 -> List.of())
     protected TabBar.Tab tabBaru(String id, String labelKunci) { return TabBar.Tab.of(id, TeksTempat.k(labelKunci)); } // bila lembar lain masih memakainya
     ```
   - Hapus (tema tidak memakainya lagi): `protected abstract void isi(kit Lukis, ...)` dan seluruh isi `drawBehind` kit 38 (turunan menimpa
     `Page.drawBehind`: `TemaHalaman`, `LayarInti`, dst.), bidang `tombol` (TombolAksi), `cari` (KotakCari), `zonaTerakhir`, `daftarTerakhir`,
     `pratinjauTerakhir`, `daftarFokus`, `ditekan`, `diseret`, `diputar`, `fokus`, `gambarKosong()`, `kosong(kit Lukis...)`,
     `subjudul/barisNilai/sumur(kit Lukis...)`, `tombolOp(kit Lukis...)`, `gambarStrip`, `jalankan`, `geserFokus`, dan semua `mouse*/key*/charTyped/fokusTab`
     kit 38 (turunan memakai `Page` + `fokusRing`; `TemaHalaman` menimpa semuanya). `mundur()`/`mauTombol()` tanpa `cari`.
   - Pertahankan persis (dipakai folder tema): konstruktor `(String layar, int npcId, CompoundTag lembar, Component judul, TempatWarna.Gaya gaya)`,
     bidang `layar, npcId, lembar, gaya, tab, title, diterima, tata`, `tabSekarang()`, `setelahLembar(boolean)`, `tabBerubah()`, `tabAda(String)`,
     `spasi()`, `tombolLain(KeyEvent)`, `angka(int)`, `panelMaks(Tata)`, `petunjuk()`, `judulBawaan()`, `kirim(String)`, `tulisBoleh()`, `hanyaBaca()`,
     `alasanBaca()`, `uang()`, `tampilPesan`, `rinci()`, `mundur()` (tanpa `cari`), `record Kotak`, `bagi(...)`, `stripInfo(screen.ui.Lukis, ...)`,
     `kunciLayar(screen.ui.Lukis, ...)`, `adaStrip()`.
   - Opsional (kembar `Gaya`): bila `LayarTempatDasar` pindah ke `Palette.Gaya` (`protected Palette.Gaya gaya`, konstruktor menerima `Palette.Gaya`,
     `terima()`: `gaya = Palette.Gaya.dari(lembar.s(LembarKunci.GAYA), gaya)`), jalankan di folder tema:
     `sed -i 's/TempatWarna\.Gaya/Palette.Gaya/g' *.java` lalu hapus `import dev.kultivator.client.tempat.ui.kit.TempatWarna;` dan pastikan
     `import dev.kultivator.client.screen.ui.Palette;` ada di tiap berkas yang memakai `Palette.Gaya` (konstruktor 20 lembar + `LayarTemaDasar`).
     `Palette.Gaya` punya `naik()/turun()`: `LayarTemaDasar.delta` bisa memakainya.
2. `tempat/ui/layar/LayarKafilah.java` (porter lain): `tabs()` kini harus `List<TabBar.Tab>`; `LayarTemaDasar.tabTema(id, kunciLabel)` tidak lagi
   menerima ikon; `tengah/pita/strip/kosong/subjudul/toko` tidak berubah (semuanya `screen/ui.Lukis`). Bila menimpa `gambarKosong()`, ganti
   `kosongGambar()` (tipe `screen/ui.KeadaanKosong.Gambar`).
3. `kit/KotakCari`, `TombolAksi`, `TabLentera`, 10 kembar kit 38: tidak ada lagi pengimpor dari folder tema (siap `.deactivation` dari sisi tema).
4. Tidak dikerjakan (di luar folder): `uji ui skala` untuk lembar tema (`TemaHalaman.drawBehind` tidak menerapkan `skala` seperti mesin LTD lama;
   butir disini #12 yang sudah tercatat).

## Belum diuji
Hanya penulisan; tidak dikompilasi dan tidak dijalankan di game. Risiko utama: kontrak `LayarTempatDasar` di atas (tanpa itu folder tema tidak
terkompilasi: `tabs()` kini `List<TabBar.Tab>`, `isi` tidak lagi ditimpa), lebar label segmen Pasar di skala GUI 4.
