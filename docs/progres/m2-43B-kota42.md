# m2-43B-kota42: porter "kota42" (dunia/kota42/ui/layar, fx, render, LayarBukuDagang)

Tugas: nol impor `tempat.ui.kit.*` di folder ini (kecuali bagian yang boleh tetap). Tanpa Gradle. Hanya kompilasi belum diuji.

## Temuan awal
- 16 berkas `dunia/kota42/ui/layar` SUDAH memakai kit tunggal (`LayarKotaDasar` -> `LayarInti` -> kit tunggal `screen/ui.Lukis`,
  `TalismanButton`, `DaftarGulir`, `GulirIsi`, `Bilah`, `InkField` ...). Satu-satunya impor kit 38 yang tersisa di sana adalah
  `tempat.ui.kit.Lembar` (pembaca lembar, hanya data, tanpa gambar).
- `Lembar` kini punya kembar `screen/ui/Lembar.java` (dibuat porter "lain", API sama). Menukar impor di 16 berkas ini tanpa
  menukar bidang `lembar` milik `LayarTempatDasar` (bukan di folder ini) membuat tipe tak cocok, jadi penukaran dilakukan sekali
  oleh agen finish (lihat needs).

## Daftar berkas
- [x] `dunia/kota42/render/RakDagangRenderer.java` (LF): `TeksTempat.singkat` -> `Brush.singkat` (penerus yang sama). Tak ada perubahan perilaku.
- [x] `dunia/kota42/fx/PapanTitik.java`: tetap `TempatWarna.Gaya.BATU/KAYU` (papan teks DUNIA, bukan GUI; `TeksDunia.papan` milik
      `tempat/ui` meminta `TempatWarna.Gaya`). Lihat needs.
- [x] `ui/layar/{DialogKota, KotaGambar, LayarBukuKota, LayarCabang, LayarGudang, LayarInvestasi, LayarJabatan, LayarKantor,
      LayarKavling, LayarPakta, LayarPerkara, LayarRumah, LayarToko, LayarUpacara, LayarWarga, SketsaKota}`: diperiksa; hanya
      impor `Lembar` (tetap, lihat needs). `IkonKota`, `KotaHud`, `KotaUi2Client`, `LayarKotaDasar`: tanpa impor kit 38.
- [x] `tempat/ui/tema/LayarBukuDagang.java` (LF): turunan `TemaHalaman` (porter "tema" sudah mengubahnya bersamaan; hasil akhir
      di bawah). Impor tersisa: `Lembar` (lihat needs) dan `TempatWarna` (tipe konstruktor basis).

## LayarBukuDagang: isi dan pemeriksaan "tidak ada yang hilang"
Tab Pasar / Harga / Laba / Kontrak (Pasar hanya bila lembar membawa blok pasar), semua daftar (`DaftarGulir` kit tunggal), tooltip
sel/kolom/legenda/rute, panah halaman kolom, bilah tujuh hari + tooltip, pita Infami/Gentar, kartu kontrak, tiga buku Beli/Jual/Segel,
pratinjau 3D, pemilih jumlah, tombol beli/jual/segel (op `beli:ID:N`, `jual:ID:N`, `segel:ID`), keadaan kosong bergambar gulungan,
Enter pada daftar pasar, petunjuk Spasi/Enter di strip: semua tetap.
Beda yang disengaja:
- Panah halaman: `TalismanButton` 30x12 ("‹" "›") di kolom nama kepala tabel (talisman 16 px tak punya ruang untuk glyph), tidak lagi
  memotong kolom data. Zona `tabel` (kepala) kini jadi perhentian Tab, jadi <- / -> benar-benar bisa mengganti halaman (dulu zona
  tanpa fokus, jadi pintasan itu tak terjangkau papan ketik).
- Tiga segmen Beli/Jual/Segel: `TalismanButton` bertanda "terpilih" (susunan pilihan kit tunggal); ikon dekoratif segmen dibuang,
  tooltip `*_tip` tetap. Angka jumlah di label tetap.
- Angka laba mingguan (dulu skala 1.5 lewat `LabelHarga` kit 38) kini 1.0 (`LabelHarga` tunggal tak punya parameter ukuran; lihat needs).
- Pemilih jumlah: lebar dari `PemilihJumlah.lebar` (dulu 50 px tetap yang melebihi kotak pada kit tunggal).

## Impor kit 38 yang sengaja tetap di folder ini
- `tempat.ui.kit.Lembar` (16 berkas kota42 + SketsaKota + LayarBukuDagang): pembaca lembar tanpa gambar, tanpa kembar yang berlaku
  sampai bidang `lembar` LTD ikut berubah. Kembarnya `screen/ui/Lembar` sudah ada (dibuat porter "lain").
- `tempat.ui.kit.TempatWarna` di `kota42/fx/PapanTitik` (teks dunia, bukan GUI) dan di konstruktor `LayarBukuDagang`: tipe
  `TeksDunia.papan` dan basis `LayarTempatDasar`.

## needs (untuk agen finish)
1. Tukar impor `Lembar` setelah LTD memakai `screen/ui/Lembar` (satu perintah, idempoten):
   `grep -rl "tempat.ui.kit.Lembar" src | xargs sed -i 's/dev\.kultivator\.client\.tempat\.ui\.kit\.Lembar/dev.kultivator.client.screen.ui.Lembar/g'`
   (sed tidak mengubah akhir baris; berkas dalam `kota42` semuanya LF; cek `git ls-files --eol` untuk berkas CRLF lain).
2. `LayarBukuDagang.tabs()` mengembalikan `List<TabBar.Tab>` (seperti 19 saudara tema); `LayarTempatDasar.tabs()` harus ikut
   `protected abstract List<TabBar.Tab> tabs();` (dan `LayarTempatDasar.tablet` berbasis `TabBar.Tab`).
3. `TeksDunia.panel/papan` ke `Palette.Gaya`, lalu di `kota42/fx/PapanTitik.java` ganti
   `import ...kit.TempatWarna;` -> `import dev.kultivator.client.screen.ui.Palette;` dan
   `TempatWarna.Gaya.BATU : TempatWarna.Gaya.KAYU` -> `Palette.Gaya.BATU : Palette.Gaya.KAYU`. Lalu konstruktor `LayarBukuDagang`:
   `TempatWarna.Gaya.KERTAS` -> `Palette.Gaya.KERTAS` (+ impor) setelah konstruktor `LayarTempatDasar` menerima `Palette.Gaya`.
4. (Opsional) `LabelHarga.gambar(Lukis l, long harga, long uang, float x, float y, boolean kanan, boolean pendek, float ukuran)`
   dengan `ukuran` 1.0 atau 1.5 (ikon `Math.round(10 * ukuran)`, teks `Brush.text(..., ukuran, ...)`) bila angka laba mingguan
   Buku Dagang diminta kembali besar; panggilan: `LabelHarga.gambar(l, minggu, -1L, x, y + 2, false, false, 1.5F)`.
