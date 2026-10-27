# m2-43B-gabung (porter "gabung": sekte/gabung/ui/layar -> kit tunggal screen/ui)

Berkas: `src/client/java/dev/kultivator/client/sekte/gabung/ui/layar` (13 berkas Java). Tanpa Gradle (agen finish membangun);
hanya ditulis dan ditinjau, belum dikompilasi. Akhir baris LF dipertahankan (semua berkas LF).

## Keputusan
- Tidak ada lembar yang tetap `LayarTempatDasar` bermesin kit 38. Dasar bersama `LayarGabungDasar` kini turunan `LayarInti`
  (pembantu lembar kit tunggal milik `tempat/ui/layar`, bukan basis kedua): lembar server, tab sebagai sub, strip info, kunci layar,
  masukan (`fokusRing`, Tab, Enter, angka) datang dari sana; lembar 41 hanya menggambar `isiGabung(Lukis, x0, y0, x1, y1)` dengan
  `screen/ui`. Dipasang lewat `UiTempatClient.daftar` / `HubPages` / `DiSini` / `HubNav` persis seperti sebelumnya (konstruktor
  `(int npcId, CompoundTag lembar)` dan kunci layar tak berubah).
- Pembantu tangan yang kembar dengan kit tunggal dibuang: `GulirKolom` -> `GulirIsi`; `batang` -> `Bilah.gambar`; `segmen` (lima kotak)
  -> `meter` di atas `Bilah`; `warnaSikap` -> `DaftarSekte.warnaSikap`; `kepalaSekte` (dua salinan, kit 38 dan kit tunggal) -> satu;
  `tombolYakin` -> `LayarInti.yakin`; tiga salinan masukan fokus (Direktori, Shifu, Putusan) -> `LayarInti`.
- `LayarPaviliun`: `KotakCari` -> `Saring` (kolom cari `InkField.cari`, `/` memulai, `Ketik.fokus`, kursor, seleksi, Ctrl+A/C/V/X, tombol x).
- `LayarDirektori`: `TombolKembali` di halaman dibuang (audit G-12): kembali dari rinci sempit = kepala Gulungan + Esc (`rinci()`/`mundur()`).
  Baris subjudul sendiri dibuang: strip dasar (`stripInfo`) menggambarnya; kunci layar memakai `KeadaanTerkunci` dasar.
- `LayarPolitik`: grafik 7 hari -> `Bilah.grafik7` (label "hari ini" tetap); batang tarik-tambang Perang -> `Bilah.gambar` + skor bertanda.
- `GulirKolom.java` -> `GulirKolom.java.deactivation`.

## Daftar periksa
- [x] LayarGabungDasar (dasar: LayarInti; Lukis/DaftarGulir/Glyphs/GulirIsi/Bilah; tanpa TombolAksi/TabLentera/IkonTempat/PanelTempat)
- [x] LayarGabung2 (`pk`: Glyphs.Atlas, Palette, Lukis tunggal; `u` tak berubah, dipakai `LayarYamen`)
- [x] GulirKolom (retired, `.deactivation`; ganti GulirIsi)
- [x] LayarDirektori (isiGabung + gulirAktif; Back ganda dibuang)
- [x] LayarKartu (GulirIsi untuk gulir Kartu/Shifu/Murid; `lompat` lewat `kolomSiap`)
- [x] LayarKediaman (GulirIsi untuk kolom tumpuk Gua)
- [x] LayarLamaran
- [x] LayarPangkat
- [x] LayarPapan
- [x] LayarPaviliun (Saring)
- [x] LayarPolitik
- [x] LayarPutusan (lukis -> isiGabung; masukan sendiri dibuang)
- [x] LayarShifu (lukis -> isiGabung; masukan sendiri dibuang)

## Dicek: tidak ada yang hilang (per berkas, tombol / daftar / teks / tooltip / konfirmasi / pintasan)
- Semua tombol aksi (`tombolOp`, `tombolYakin`, tombol lokal "pindah", "kePilih", "hal0/hal1", "arah*", "ikut", "sekte_dunia", "lamar", "kartu",
  "tandai") tetap ada dengan kunci fokus yang sama; konfirmasi sedang/ringan/berat tetap `SegelKonfirmasi` (lewat `LayarInti.yakin`, Politik
  memakai `konfirmasi` langsung seperti dulu).
- Semua daftar tetap `DaftarGulir` (kembar tunggal API sama): panah, PgUp/PgDn/Home/End, Enter / klik kedua `pakai`, seret batang, roda.
- Tooltip tetap (`l.tip`); teks terpotong tetap "..." + tooltip penuh; semua teks 1.0.
- Papan ketik: Tab/Shift+Tab, Enter, Spasi, kiri/kanan, angka (Shifu 1-3 lewat `DaftarPilihan`), `/` cari (Paviliun), Esc; panah/halaman menggulir
  kolom Kartu/Kediaman/Direktori lewat `tombolLain` setelah daftar.
- Beda yang disengaja: label gulir 5 px di kanan (GulirIsi di luar kotaknya), batang kemajuan 4 px (Bilah), meter ikatan tak lagi lima kotak,
  tooltip batang grafik Kas = "Hari ini / N hari lalu +jumlah" (kunci lang `kultivator.gabung.ui2.politik.kas.hari` tak terpakai lagi).
- Shifu dan Putusan kini juga mendapat strip info, kunci layar dan sapuan merah aliran iblis dari dasar (dulu dilewati).

## Impor kit 38 yang tersisa di folder
- `tempat.ui.kit.Lembar` (pembaca lembar; semua lembar kit 38 memakainya; agen finish menukar impor `Lembar` bersama lembar lain,
  lihat needs porter "lain").
- `tempat.ui.kit.TempatWarna.IBLIS` di `LayarGabungDasar` (satu warna #6A1420 tanpa kembar di `Palette`; needs).
- Tidak ada: Lukis, DaftarGulir, TombolAksi, TabLentera, KotakCari, PanelTempat, IkonTempat, KeadaanKosong, LabelHarga, CincinWaktu,
  LencanaPangkat, PemilihJumlah, Potret, Pratinjau3D kit 38. `LayarTempatDasar` hanya lewat `LayarInti` (bukan impor di folder ini).

## needs (untuk agen finish)
1. **Wajib agar terkompilasi** `tempat/ui/layar/LayarInti.java` (milik porter tempat), tambah di bawah `tab(String, String, int)`:
   ```java
   /** A tab with a ready-made label (sheets whose texts live outside {@code kultivator.tempat_ui.*}). */
   protected static Tab tab(String id, Component label, int ikon) {
   	return new Tab(id, label, ikon);
   }
   ```
   (`new Tab(...)` dari subkelas di paket lain tidak boleh: rekor `protected`, konstruktornya `protected`.) Dipakai 8 lembar gabung (`daftarTab`).
2. `screen/ui/Palette.java`: `public static final int IBLIS = 0xFF6A1420;` lalu di `LayarGabungDasar` ganti `TempatWarna.IBLIS` dengan
   `Palette.IBLIS` dan hapus impor `TempatWarna` (menutup impor kit 38 terakhir selain `Lembar`).
3. Setelah `Lembar` pindah ke `screen/ui`: ganti impor `dev.kultivator.client.tempat.ui.kit.Lembar` -> `dev.kultivator.client.screen.ui.Lembar` di 12 berkas folder ini.
4. Opsional (G10): `LabelHarga.pkHarga(Lukis l, long harga, long punya, float x, float y, boolean kanan, int warna)` (harga PK tak bertanda,
   merah bila kurang, tooltip harga + kekurangan) lalu `LayarGabung2.pk(...)` cukup meneruskannya; sekarang `pk` tetap di `LayarGabung2` karena
   `LabelHarga.pk` hanya jumlah bertanda untuk buku kas.
5. Sesudah P1 menghapus mesin LTD: `LayarGabungDasar` tidak perlu diubah selain konstruktor `LayarInti` bila `LayarInti` berganti dasar.
