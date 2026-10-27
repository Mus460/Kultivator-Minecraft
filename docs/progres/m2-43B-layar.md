# M2 43B "layar" - tempat/ui/layar dan tempat/ui/*.java ke kit tunggal (screen/ui)

Porter "layar". Folder: `src/client/java/dev/kultivator/client/tempat/ui/layar` (19 berkas + 1 `.deactivation`) dan
`tempat/ui/*.java` (UiHub, UiTempatClient, TeksDunia, PapanHidupTeksRenderer). Hanya kompilasi `javac` terhadap build
terakhir (tanpa Gradle); tidak diuji di game.

Catatan ruang lingkup: `LayarPaviliun` dan `LayarPapan` ada di `sekte/gabung/ui/layar` (bukan folder ini): `KotakCari` ->
`Saring`/`InkField.cari` dan imbalan PK `PapanMisi` (sekte #9) milik porter sekte, tidak disentuh di sini.

## Daftar berkas

- [x] LayarBukuKas: dari `LayarTempatDasar` + kit 38 (`Lukis`, `DaftarGulir`, `LabelHarga`, `PanelTempat`, `IkonTempat`,
      `TabLentera`, `TeksTempat`, `TempatWarna`) menjadi `LayarInti` + `screen/ui`. Tab 5 (Hari ini, 7 hari, Sumber,
      Lapak, Kafilah), kolom PK/Batu, garis bersih + sparkline, 18 slot lapak + editor harga +-10%, tarik barang, sewa,
      ambil kas, peta mini kafilah, perencana kirim (tujuan, hingga 3 pengawal, 2 muatan, centang lewat klik/Enter),
      keadaan kosong, baca-saja (`tombolOp`): semua tetap, tanpa kehilangan.
- [x] LayarJurnal: sama (`LayarInti`). Tab Aktif/Selesai/Alur, titik tahap, cincin tenggat, Lacak (Enter pada daftar juga),
      Tandai, bab tersegel.
- [x] LayarPetaKota: sama. Peta 3x3 + distrik luar (zona fokus per distrik), titik pemain berdenyut, daftar layanan
      distrik, Tandai di peta dunia; tab Layanan (tombol Tandai per baris), Tokoh, Acara (cincin hitung mundur).
      Tetap memakai `TempatWarna.distrik/GERBANG` (tidak punya kembar di `Palette`).
- [x] LayarInti: cabang mati `mesinLama()` (mesin lembar lama untuk mode 42 setengah jadi, tidak dipakai siapa pun) dihapus;
      semua lembar kini lewat `fokusRing`. Sisa impor kit 38 hanya tanda tangan basis (lihat bawah).
- [x] LayarAcara, LayarYamen: impor `TempatWarna`/`IkonTempat`/`PanelTempat` yang tak terpakai dibuang.
- [x] LayarBank, LayarDialog, LayarGilda, LayarGildaMisi, LayarJudiBatu, LayarLapakRekrut, LayarLayanan, LayarLelang,
      LayarPasarBinatang, LayarTagihan, LayarTeleport, LayarTokoSenjata: sudah `screen/ui`; hanya impor `Lembar` (pembaca
      lembar, tetap).
- [x] LayarKafilah: sudah `screen/ui`; sisa `TabLentera.Tab`/`TempatWarna.Gaya` hanya tanda tangan basis `TemaHalaman`/LTD.
- [x] SemuaLayar, LayarAcaraKota.java.deactivation: tidak ada impor kit 38 / tidak berubah.
- [x] PapanHidupTeksRenderer: `TeksTempat.k` -> `Component.translatable("kultivator.tempat_ui.papan.*")` langsung; sisa
      `TempatWarna.Gaya.KERTAS` (tanda tangan `TeksDunia.panel`).
- [x] TeksDunia: tetap `TempatWarna.Gaya` (dipakai `JalanPapan`, `kota42/fx/PapanTitik`, di luar folder).
- [x] UiTempatClient: tetap `LayarTempatDasar` (`lihatSaja`, `tabSekarang`) sampai basis dipindah.
- [x] UiHub: tidak ada impor kit 38.

## Tidak dikerjakan

- K-4 (tiga tombol Tawar +5/+10/+25 % di LayarLelang -> `PemilihJumlah`): langkah berasal dari server (`LANGKAH`), bukan
  jumlah bilangan bulat; butuh keputusan rupa. Tombol tetap ada, di kit tunggal (`TalismanButton`).
- Uji skala UI (`uji_skala`) tidak ada di `LayarInti` (butir disini #12 yang belum).
