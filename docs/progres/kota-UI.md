# Kit UI "Pasar Giok" (38UI) — API final W1

Dokumen ini adalah kontrak kit layar 38 (DESIGN 38.U) yang dipakai **hanya-baca** oleh 38TV-U (W2) dan modul lain.
Kit tidak boleh diubah oleh modul lain; kebutuhan baru dikirim ke pemilik 38UI. Semua kode klien
(`src/client/java/dev/kultivator/client/tempat/ui/**`), kecuali yang ditandai *server*.

## 1. Alur satu layar

1. *Server* membangun lembar (`CompoundTag`, kunci di `dunia/tempat/ui/LembarKunci`) dan memanggil
   `TempatLayar.buka(player, layar, npcId, lembar)` / `bukaDi` / `segarkan` (lihat `mega-38UI-0.md`).
2. Klien: `UiTempatClient` menerima payload, mencari `Pembuat` lewat `UiTempatClient.daftar(layar, Pembuat)` dan membuka
   layar (sebagai anak hub bila menjawab `lihatDariHub` dalam 3 s).
3. Layar adalah subkelas `kit.LayarTempatDasar` (yang mengimplementasikan `UiTempatClient.LayarTempat`): ia menggambar
   bingkai, tab, uang, cap nama baik, pesan, petunjuk tombol; subkelas hanya mengisi area isi.
4. Aksi: `kirim(op)` (op dibuat dengan `LembarKunci.op(OP, arg...)`) → server `Penangan`; hasil kembali sebagai
   lembar baru (`perbarui(lembar, false)` menjaga tab, gulir, pilihan, fokus) plus `pesan` satu baris di kaki.
5. `removed()` memanggil `UiTempatClient.ditutup` (sesi server berakhir). Jangan menimpa `removed()` tanpa `super`.

## 2. Menulis layar baru (pola 38TV-U)

```java
public final class LayarContoh extends LayarTempatDasar {
	public LayarContoh(int npcId, CompoundTag lembar) {
		super("contoh", npcId, lembar, TeksTempat.k("layar.contoh"), TempatWarna.Gaya.KAYU);
	}
	@Override protected List<TabLentera.Tab> tabs() {
		return List.of(tabBaru("a", "tab.a", IkonTempat.PETA), tabBaru("b", "tab.b", LembarKunci.Ikon.LONCENG));
	}
	@Override protected void isi(Lukis l, int x0, int y0, int x1, int y1) {
		Kotak[] k = bagi(x0, y0, x1, y1);              // 1, 2 atau 3 kolom sesuai Tata
		int y = k[0].y0() + subjudul(l, TeksTempat.k("contoh.judul"), k[0].x0(), k[0].y0(), k[0].x1());
		tombolOp(l, "beli", k[0].x0(), y, 90, 15, TeksTempat.k("toko.beli", "12"), true, null, "beli");
	}
}
// client init:  UiTempatClient.daftar("contoh", LayarContoh::new);
```

Id layar harus lolos `TempatLayar.idSah` (terdaftar di `LembarKunci.SEMUA_LAYAR` atau di daftar tema 38TV).
Teks kit memakai `TeksTempat.k(kunci)` = `kultivator.tempat_ui.<kunci>`; modul lain memakai fragmen bahasanya sendiri
(`Component.translatable`) dan boleh memakai ulang kunci kit yang ada (lihat `lang-src/tempat_ui.*`).

## 3. `LayarTempatDasar` (abstract)

| Anggota | Arti |
|---|---|
| `layar`, `npcId`, `lembar` (`Lembar`), `diterima` (ms saat lembar tiba) | keadaan; `lembar` diganti tiap `perbarui` |
| `tablet` (`TabLentera`), `tombol` (`TombolAksi`), `uangGulir` (`AngkaGulir`) | komponen bingkai |
| `tabs()` *abstract* | daftar tab; tab di `tab_sembunyi` disaring, `tab_kunci` memberi gembok + alasan |
| `isi(Lukis, x0, y0, x1, y1)` *abstract* | gambar area isi |
| `setelahLembar(buka)`, `tabBerubah()` | kait setelah lembar baru / tab berganti |
| `spasi()`, `angka(n)`, `tombolLain(KeyEvent)` | aksi cepat Spasi, tombol 1-9 (default: tab), tombol lain |
| `panelMaks(Tata)`, `panelTinggiMaks(Tata)`, `labelTab()`, `gambarKosong()`, `judulBawaan()`, `petunjuk()` | penyesuaian bingkai |
| `tabBaru(id, kunciLabel, ikon)`, `tabAda(id)`, `gantiTab(id, kirimOp)` | tab |
| `hanyaBaca()`, `alasanBaca()`, `tulisBoleh()` | layar hub (npcId -1): tombol tulis dinonaktifkan dengan alasan |
| `kirim(op)`, `tampilPesan(teks, warna)`, `uang()` | aksi, pesan lokal, uang di tangan (-1 = tidak diketahui) |
| `tombolOp(l, kunci, x, y, w, h, label, aktif, alasan, op)` | tombol yang mengirim `op` (mati bila hanya baca) |
| `bagi(x0,y0,x1,y1)` → `Kotak[]` | kolom sesuai `Tata` (SEMPIT < 320, SEDANG 320-479, LEBAR ≥ 480 lebar GUI) |
| `subjudul`, `barisNilai`, `sumur`, `kosong(l, cadangan, ...)` | blok umum: judul bagian, baris label-nilai, sumur, keadaan kosong |
| `UJI`, `UJI_SKALA` | lembar uji (pita "UJI"), `uji ui skala N` |

Masukan: zona klik dari frame terakhir, Tab/Shift+Tab fokus (cincin emas), Enter, Esc, 1-9, `/` cari, ←/→ jumlah,
↑↓ PgUp PgDn Home End daftar, Spasi. Animasi buka 200 ms. Tidak ada gambar di bawah skala 0,6.

## 4. Komponen kit (`client/tempat/ui/kit`)

| Kelas | API publik |
|---|---|
| `Lembar` | pembaca lembar aman-null: `ada, teks, teksAtau, s, i, l, f, b, ia, la, sl, sub, daftar, barang() (ItemStack, dibuat saat dibaca), keadaan, bisa, id, kosongSemua`; `Lembar.kosong()` |
| `Lukis` | konteks satu frame (`g`, `font`, `gaya`, `kini`, mouse): `zona(kunci, x0..y1, aksi, aktif, alasan, ...)`, `di`, `difokus`, `denyut`, `fokusCincin`, `tip(..)`, `klip/lepasKlip`, `nyata(v)` (kotak PiP × skala), teks: `teks, kanan, judul, keterangan, bungkus` |
| `TempatWarna` | token distrik/aliran/status, `Gaya` {KERTAS, KAYU, BATU, KAIN, GIOK} dengan `teks, teksLemah, teksSamar, kartu, kartuSorot, sumur, aksen, dasar, gelap(), teksSumur()`; `dari(id, cadangan)`, `distrik(id)`, `status(jenis)`, `mutu(m, gelap, polos)`, `terkunci(warna)` |
| `TeksTempat` | `k(kunci, args)`, `angka`, `singkat` (12,5 rb / 1,2 jt / 3,4 M), `desimal1`, `nb`, `bertanda`, `bertandaWarna`, `rincianBatu`, `batuDiTas`, `waktu(ticks)`, `hari(n)`, `persen`, `samar` |
| `PanelTempat` | `gambar(g, gaya, ...)` (bayangan + 9-slice + awan), `bingkai`, `awan`, `sumur`, `kartu(.., sorot, pilih)`, `garis`, `cincinFokus` |
| `IkonTempat` | atlas `ikon.png` (slot = `LembarKunci.Ikon.*`, tambahan 112-131 sebagai konstanta di kelas ini), `gambar(g, slot, x, y[, size, warna])`, `lentera`, `cahaya`, `bingkaiPotret`, `stempel(Stempel ..)`, `pita(emas, ..)` |
| `TombolAksi` | `gambar(l, kunci, x, y, w, h, label, aktif, alasan, aksi)` (tombol pernis dengan tekan, fokus, alasan di tooltip), `lebar(l, label, min, max)` |
| `TabLentera` | `Tab(id, label, ikon, kunci)`, `setTabs`, `tabs`, `tab(i)`, `cari(id)`, `tinggiPer`, `gambar(l, x, y, y1, label, aktif, pilih, ditolak)` |
| `DaftarGulir` | daftar dengan pilihan/gulir/seret: `gambar(l, kunci, x0..y1, jumlah, tinggiBaris, Baris, pakai, ...)`, `pilihan, setPilih, geser, pakai, roda, tekan, seretKe, lepas, mulaiUlang` |
| `KotakCari` | `gambar`, `teks`, `aktif`, `mulai/berhenti`, `cocok(nama)`, `kosong`, `tombol(KeyEvent)`, `ketik(CharacterEvent)` |
| `KartuBarang` | `gambar(l, e, x0..y1, sorot, pilih, uang, muncul)` (ikon, nama mutu, harga, stok, langka, keadaan), `cap(l, teks, cx, cy, lebar, t)` (cap "TERJUAL") |
| `LabelHarga` | `gambar(l, harga, uang, x, y, kanan, ukuran, pendek)` (merah bila kurang, tooltip rincian batu), `pk`, `nbBertanda` |
| `AngkaGulir` | `nilai(nilai, kini)`, `bergulir`, `arah` (angka uang bergulir) |
| `BandingStat` | `gambar(l, nama, nilai, banding, maks, x0, y, x1, desimal)` (bar + panah naik/turun vs barang di tangan) |
| `PemilihJumlah` | `nilai`, `set`, `gambar(l, kunci, x, y, maks)` (−/+, Shift ×10) |
| `Pratinjau3D` | `gambar(l, kunci, x0..y1, item, entitasId, ikon)`: barang berputar (pose 2D scale cos, 26.3 `item()` 2D) atau entitas PiP asli; seret untuk memutar |
| `Potret` | `gambar(l, x, y, size, entityId, ikon, takut, bicara)`, `ukuran(tersedia)` |
| `GelembungDialog` | `gambar(l, npc, teks, x0..y1)` (teks mengetik), `menulis`, `selesaikan` |
| `LencanaPangkat` | `warna(pangkat)`, `gambar(l, pangkat, x, y, size)` (E-S), `bintang`, `namaBaik(l, nilai, pangkat, xKanan, y)` |
| `CincinWaktu` | `sisa(sisaTick, diterima, kini)`, `gambar(l, cx, cy, r, sisa, total, diterima, teks)` |
| `PapanSkor` | `gambar(l, baris, nilaiKunci, x0, y0, x1, tinggi, maks, uang)` (baris bergeser saat peringkat berubah) |
| `KeadaanKosong` | `Gambar` (gambar kosong per jenis), `gambar(l, tombol, gambar, kosong, cadangan, x0..y1, ...)` |
| `KeadaanTerkunci` | `gambar(l, alasan, syarat, x0..y1, sejak)` (gembok lilin + rantai) |

Tekstur (`tools/gen_tempat_ui.py`): `textures/gui/tempat/panel_{kertas,kayu,batu,kain,giok}.png` (32 × 32, sudut 8),
`awan.png`, `ikon.png` (256 × 256, sel 16), `lentera.png`, `potret.png`, `stempel.png` (bulat, persegi, lilin, bintang,
pita abu/emas).

## 5. Layar inti 38.U.3 (`client/tempat/ui/layar`, didaftarkan `SemuaLayar.daftar()`)

`dialog` LayarDialog · `toko_senjata` LayarTokoSenjata · `lelang` LayarLelang · `gilda_misi` LayarGildaMisi ·
`bank` LayarBank · `buku_kas` LayarBukuKas · `tagihan` LayarTagihan · `peta_kota` LayarPetaKota ·
`teleport` / `dermaga` LayarTeleport · `pasar_binatang` LayarPasarBinatang · `judi_batu` LayarJudiBatu ·
`penginapan` / `rumah_makan` / `jasa` LayarLayanan · `yamen` LayarYamen · `acara` LayarAcara ·
`lapak_rekrut` LayarLapakRekrut · `jurnal` LayarJurnal · `gilda_alkemis` / `gilda_tempa` LayarGilda ·
`kafilah` LayarKafilah. Skema setiap lembar: javadoc kelas bersarang di `LembarKunci`.

Kartu hub (`UiHub`): DUNIA `kota_besar` (peta_kota), `catatan_perjalanan` (jurnal); SEKTE (Aula, kunci aula)
`buku_kas`, `lapak_kafilah` (buku_kas tab lapak). `UiHub.mintaTab/ambilTab` membuka tab tertentu.

## 6. Teks dunia (`TeksDunia`, 38.U.4)

Anggaran: `izin(kunci, jarakSq)` sekali per papan per frame (≤ 12 papan, 6 pada Minimal; ≤ 24 b, 16 pada Minimal;
peringkat terdekat frame lalu didahulukan; tidak ada di shadow pass Iris). `rinci(jarakSq)`: baris kecil hanya ≤ 12 b
(8 Minimal). Primitif di pose pemanggil (y ke bawah, `nz` = arah z ke penonton: +1 ruang name-tag, -1 ruang model
entitas): `panel(.., gaya, x0, y0, x1, y1, z, nz, sudut, light, warna)` (9-slice kit), `baris(.., seq|Component, x, y,
tinggi, Rata, nz, warna, light)`, `garis(..)`, `wajah(.., nama, x, y, ukuran, z, nz, light)` (kulit pemain bila di
daftar tab, selain itu sketsa sepia), `segel(.., Segel, ..)`; tata letak `bungkus(text, lebar[, maksBaris])`, `lebar`,
`hadapKamera(camera)`. Papan siap: `papan(pose, collector, camera, x, y, z, baris, gaya, tinggiBaris, light)` (menghadap
kamera) dan `papanHadap(.., Quaternionfc arah, ..)` (papan nama toko, tonggak jalan, Batu Catatan). Pemanggil dari
`LevelRenderEvents.COLLECT_SUBMITS` atau dari renderer entitasnya sendiri.

Papan Hidup: *server* `PapanHidup.pasang(level, kaki, yaw, jenis)` (idempoten, noSave) lalu
`PapanHidupEntity.setLembar(judul, baris)` (≤ 6), `setBuronan(nama, hadiah)`; jenis `kabar`, `buronan`, `misi`
(misi membuka `gilda_misi` lewat `TempatLayar.bukaDari`). Klien `PapanHidupTeksRenderer` (model 38MD + tulisan,
mengikuti kibaran dan tempelan kertas).

## 7. Uji

`/kultivasi uji ui layar <id> [isi|kosong|kunci|takut|donasi]` membuka lembar contoh (npcId -1, hanya operasi baca);
`/kultivasi uji ui skala <1-6> [id]` menata layar seperti skala GUI N pada jendela ini lalu memperbesarnya.
Contoh tambahan W3: `uji ui layar dialog panjang` (baris panjang + 6 pilihan), `uji ui layar acara istana`.

## 8. Pass detail W3 (D-UI)

API kit tidak berubah (hanya tambahan perilaku). Yang perlu diketahui pemakai kit:
- Teks panjang selalu dibatasi: `Lukis.bungkus` dengan tinggi maks memotong dengan ".." dan memberi teks penuh di tooltip;
  `kosong(..)`/`KeadaanKosong` juga dibatasi. Pakai versi terbatas untuk cerita/catatan di atas tombol.
- `TabLentera` mengecil sampai 14 px per lentera, jadi 9 tab muat pada tinggi isi 126 (1366x768 skala 4).
- `GelembungDialog` mengecilkan huruf (0,85 lalu 0,75) lalu memecah ke halaman (Spasi/klik = lanjut).
- `KotakCari` menelan '/' pertama yang membukanya.
- `LayarAcara` mode istana: lembar `acara=istana` tanpa `status` digambar sebagai balai audiensi (potret di bawah
  kanopi, catatan), `teka` berjudul audiensi/perintah, jadwal = perintah (`LembarKunci.Acara.ACARA_ISTANA`).
- Tekstur: cap persegi `stempel.png` kini berpita pola kunci + bingkai dalam (tengah tetap terang untuk teks kode),
  cap bulat bermanik; panel kertas berserat, kayu berkilau pernis dengan serpih emas. Ukuran/UV tidak berubah.
