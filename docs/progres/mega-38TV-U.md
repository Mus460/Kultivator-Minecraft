# 38TV-U (W2) — layar kota bertema 38.T.13.3 di kit "Pasar Giok"

Milik: `client/tempat/ui/tema/**` (+`TemaUiClient`), `tools/gen_tempat_tema_ui.py`, `textures/gui/tempat/tema/**`,
lang `lang-src/tempat_tema_ui.{id_id,en_us}.json`. Kit `client/tempat/ui/kit/**` hanya-baca. Tanpa Gradle.
Kontrak lembar: `dunia/tempat/tema/bersama/TemaLayar.java` (C38.T3, hanya-baca).

## Checklist

- [x] 0 Baca kit API (kota-UI.md, LembarKunci, LayarTempatDasar, Lukis, contoh layar inti)
- [x] 1 Generator `tools/gen_tempat_tema_ui.py` + tekstur `textures/gui/tempat/tema/**` (dicek dengan Read):
      ikon_tema 256x80 (72 ikon), mural 256x256 (16 panel), irama, jubah, menara, latar_gunung, kartu, tenda, surat, kandang
- [x] 2 Bersama: `TemaUi` (tekstur, slot ikon, warna tema, blok kecil), `LayarTemaDasar` (strip NPC, alasan keadaan)
- [x] 2b `TemaUiClient` daftar 20 layar (+peringatan bila id SEMUA tanpa layar)
- [x] 3 Layar oasis: LayarTawar, LayarKafilahTema, LayarMural
- [x] 4 Layar dagang/inti: LayarBukuDagang, LayarIrama
- [x] 5 Layar pelabuhan: LayarKapal, LayarGalangan, LayarDokumen, LayarRacik (+lang galangan/dokumen/racik)
- [x] 6 Layar air: LayarSutra, LayarTeh, LayarBait (+blok toko bersama `LayarTemaDasar.toko/Toko/DetailEkstra`, lang toko.*)
- [x] 7 Layar gunung: LayarHerba, LayarZiarah, LayarLift (+lang)
- [x] 8 Layar iblis: LayarPasarGelap, LayarKitabTerlarang, LayarKontrak, LayarTaruhan, LayarTawanan (+lang)
- [x] 9 Lang fragmen id_id + en_us: 474 kunci identik, jumlah %s sama, tanpa bentrok fragmen lain
- [x] 10 Tinjauan: javac 25 (bukan Gradle, keluaran scratchpad lalu dihapus) atas 21 berkas tema: 0 galat; semua klien, LF, tanpa ItemStack statis

## Langkah berikutnya

Selesai (dicek ulang 2026-10-24: 23 berkas Java, 10 tekstur, lang 474/474 identik, LF, TemaClient.init memanggil TemaUiClient.init). Sisa hanya build F2 (Gradle oleh agen finish) dan uji layar dengan lembar asli 38TK.
Lang: sumber scratchpad `tvu/lang.py` + `tvu/lang_layar.py` (`python lang.py <repo>`), cek `tvu/cek.py <dir tema>`.

## Masalah terbuka

- Belum diuji di layar (tanpa runClient); tata letak SEMPIT/SEDANG/LEBAR hanya dihitung.
- Tab klien saja (tidak di kontrak): `tema_galangan` lunas/layar mengirim op baca `tab:lunas|layar` (pembangun boleh mengabaikan).

## Butuh dari modul lain

- 38TK-P/A/I (pembangun lembar): kunci entri tanpa konstanta di TemaLayar dipakai sebagai literal sesuai javadoc C38.T3:
  `racik.bahan` punya/perlu, `pasar_gelap.barang_d` dan `kitab.gulungan` pita_min; `tema_kitab_terlarang` boleh menambah
  header `gentar` (opsional, untuk tooltip lantai terkunci).

- 38UI / D-UI: `LayarTempatDasar` memanggil `setelahLembar` dan `tabBerubah` dari konstruktornya; layar inti yang
  memakai field sendiri di sana (mis. `LayarTokoSenjata.tabBerubah` -> `daftar.mulaiUlang`) akan NPE saat dibuka
  (field subkelas belum terinisialisasi). Layar tema menghindarinya lewat `LayarTemaDasar` (flag, diolah frame pertama).
