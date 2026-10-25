# m2-F43-END — finish gelombang 43-P (build B3, docs)

Lingkup 43.16.5 F43-END: terapkan "Butuh dari modul lain" P1-P8; build B3 sampai hijau; grep 43.16.5 bersih;
docs (DESIGN.md `## 43.` LF, PANDUAN bab "Gulungan Utama" CRLF, README satu baris CRLF, docs/uji/FASE-43.md dari 43.18);
perbarui centang 43-satu-gui.md. Tidak commit, tidak menyalin jar ke .minecraft. Tidak menyentuh 42-G2.

## Daftar centang
- [x] Baca progres P1-P8 + Butuh
- [x] HelpPage.TOPICS jurnal/peta_kota/tema_dagang + teks sekte_kas/sekte_paviliun: sudah ada (P8)
- [x] Lang sekte_hidup jalur 'Murid › Daftar' / 'Disciples › Roster' (ensiklopedia_sambung, P4)
- [x] daftarBawaan sudah tanpa SettingsPage(null); javadoc Page.helpTopic diperbaiki (P8)
- [x] HubNav.tujuanAula PAGE_JENJANG -> AnggotaPage.sumberAula() (P4)
- [x] Fokus.zonaFokus() publik (P1/P3/P4); pemakaian di Tumpuk/GulirKolom/GulirIsi belum (gelombang berikut)
- [x] Lukis kit-38 min 1.0 + LayarGabungDasar teks kecil: sudah oleh P1 (P5/P6)
- [x] Build B3 hijau (percobaan 1, tanpa rintisan 42-G2, tanpa kunci lang ganda; paritas id/en semua fragmen OK)
- [x] grep 43.16.5 bersih: HubCards.register 0, extends lama 0, setScreen(new hanya ChatScreen/KeyBindsScreen,
      extends Screen hanya CultivationScreen; pose().scale sisa = ikon barang/gambar/HUD, bukan teks < 1.0
- [x] EOL: HubNav/Page/Fokus/ensiklopedia_sambung/DESIGN LF; PANDUAN + README CRLF
- [x] DESIGN.md `## 43.` (LF)
- [x] PANDUAN bab 74 "Gulungan Utama" + daftar isi (CRLF); README satu baris (CRLF); docs/uji/FASE-43.md (LF)
- [x] Centang 43.18: Page/5 basis, jalan masuk -> Tujuan, lang (lainnya butuh uji game atau belum selesai)
- [x] jar -> %TEMP%\kultivator-F43-END.jar; build-log dihapus, tanpa javac.*.args

## Butuh dari modul lain (untuk gelombang berikut)
- Kit 38: LayarGabungDasar (+11 lembar 41), LayarTemaDasar/LayarBukuDagang, LayarBukuKas, LayarJurnal, LayarPetaKota
  dan 42-G2 (LayarKavling, SketsaKota) masih menggambar dengan tempat/ui/kit/Lukis; lalu TemaUi overload kit-38,
  Pratinjau3D/PemilihJumlah/LabelHarga kit 38, PetaUi, TemaUi -> .deactivation. LayarInti/TemaHalaman memakai
  TempatWarna.Gaya + TabLentera.Tab.
- Fokus.zonaFokus() kini publik; Tumpuk (P3), GulirKolom (P4), GulirIsi (P5) belum memakainya untuk gulir-ikut-Tab;
  zona terpotong belum terdaftar fokus saja (Tab tak mencapai tombol di luar layar; PgDn dulu).
- Keputusan pengguna: lembar 41 dari hub lihatSaja (Sewa/Perpanjang Gua Sekte di Diri › Harta mati, P2);
  `uji ui skala` untuk halaman lembar kit tunggal (P7); Forge/Ring tidak muat skala 4.
- Opsional: JadeCoin.gambar(Lukis, ..) untuk BuildPage.koin/FormasiPage.coin; PetaPainter.mini (G58); penawar Lelang K.
- Kunci lang tak terpakai dibiarkan: kultivator.sekte.mandiri.cerita.papan.tampil.kebijakan dan .kas (P3).
- MEGA/42: lembar KotaLembar + topik bantuannya; Dunia › Kota; bagian 42 Diri › Harta.
- Semua: uji 43.18 di game skala 2/3/4 (belum ada runClient).

## Langkah berikutnya
Selesai. Orkestrator: commit + salin jar; uji game 43.18.
