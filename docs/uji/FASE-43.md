# Checklist uji Fase 43 — Satu Gulungan (F43-END)

Ditulis F43-END setelah gelombang poles 43-P (build B3 hijau). Sumber: DESIGN 43.18 (`docs/rancangan/43-satu-gui.md`).
**Belum ada yang diuji di game** (hanya dikompilasi). Uji pada 1366×768 di skala GUI 2, 3 dan 4. Panduan: bab 74
"Gulungan Utama". Bantuan: topik "Gulungan Utama" dan "Tablet Di Sini".

**Selalu periksa:** tidak ada error di log, tidak ada kunci bahasa mentah (id_id dan en_us), tidak ada teks di bawah 1.0,
tidak ada tumpang tindih atau guliran mendatar, setiap fitur lama masih bisa dicapai, Esc selalu mundur satu langkah.

## Sudah terbukti di luar game (F43-END)
- [x] Build B3 `compileJava compileClientJava jar` hijau; tanpa kunci lang ganda; kunci id_id/en_us identik di semua fragmen
- [x] `grep -rn "HubCards.register"` kosong; `extends HallPage|DuniaPage|CodexPage|DiscipleTab|KotaPage` kosong
- [x] `setScreen(new` di `src/client` hanya `ChatScreen` (GuideScreen) dan `KeyBindsScreen` (SettingsRows)
- [x] `extends Screen` hanya `CultivationScreen` (+ `ForgeScreen`/`RingScreen` lewat `AbstractContainerScreen`)
- [x] `pose().scale` yang tersisa hanya ikon barang, gambar peta/potret dan HUD, bukan teks < 1.0

## Celah yang diketahui (untuk gelombang berikut)
- Lembar kota 42 (`KotaLembar`) belum dipetakan (43.16.6); Dunia › Kota dan Diri › Harta bagian 42 menunggu MEGA.
- Kit 38 lama masih dipakai `LayarGabungDasar` (11 lembar 41), `LayarTemaDasar`/`LayarBukuDagang`, `LayarBukuKas`,
  `LayarJurnal`, `LayarPetaKota` dan berkas 42-G2; teksnya dipaksa ≥ 1.0, periksa tumpang tindih vertikal di skala 4.
- Tab tidak bisa mencapai tombol yang tergulir keluar layar (Kas, Pertahanan, Kebun, Langit, rinci Seteru/Diplomasi/
  Direktori, Tumpuk Murid, GulirKolom Sekte): tekan PgDn dulu.
- Forge 270×214 dan Ring 190×216 tidak muat 341×192 (skala 4).

**Uji terima:**
1. 1366×768 skala GUI 4: setiap bagian, setiap halaman, setiap sub tampil tanpa tumpang tindih, tanpa teks di bawah 1.0,
   tanpa guliran mendatar; juga skala 2 dan 3 dan `uji ui skala 2/3/4` untuk lembar.
2. Hanya dengan papan ketik: buka (V), pindah bagian (1-8), halaman (Q/E), fokus (Tab), sub (←/→), cari (`/`), beli di
   Toko PK dengan pemilih jumlah, konfirmasi (Enter tahan), mundur (Esc) sampai dunia.
3. Klik kanan murid → Murid › Murid murid itu; ← → mengganti murid; menjauh > 64 blok: sub terkunci dengan alasan,
   Gulungan tetap.
4. Klik prasasti aula → Sekte › Ikhtisar › Aula; Bangun › Templat "Lihat di dunia" memperlihatkan hologram dan Enter
   kembali.
5. Klik NPC toko senjata → tablet Di Sini "Toko Senjata · <nama>", beli berfungsi; menjauh → tablet hilang dengan pesan.
6. Klik warga modul K (Juru Lelang) → `LayarLelang` yang sama dengan balai lelang 38 dan Keabadian › Lelang.
7. Klik NPC dunia bawah → dialog yang sama dengan dialog warga 38 (palet violet).
8. Teka-teki menara: Esc tidak membuat layar kembali sendiri; tablet Di Sini menunggu; jawaban berfungsi.
9. Cincin dari Diri › Pusaka → layar wadah; Esc kembali ke Diri › Pusaka.
10. `/kultivasi duel layar` membuka Dunia › Duel; Catatan tampil di halaman; Menyerah meminta konfirmasi dan Gulungan tetap
    terbuka.
11. Diplomasi "Perang" meminta konfirmasi berat; Seteru aksi meminta konfirmasi.
12. Pengaturan › Tampilan berisi sakelar Langit Sekte; menyalakan efek dampak Penuh / kamera Spektakuler memunculkan
    konfirmasi di dalam Gulungan.
13. Pengaturan › Kontrol: ubah tombol di tempat; bentrok ditandai.
14. HUD: hanya satu gulungan sisi pada satu waktu; toast semua lewat Kabar; cincin bidik satu rupa.
15. `grep` 43.16.5 bersih; semua kelas di 43.12 punya tempat; tidak ada kartu peluncur.

**Daftar centang:**
- [x] `Page` publik superset; lima basis lama `.deactivation`
- [ ] Gulungan responsif 341×192 … 560×330; animasi hanya saat buka
- [ ] Satu kit `screen/ui`; kit 38 lebur kecuali `LayarTempatDasar`/`Lembar`/`GelembungDialog`
- [ ] 84 kelompok fungsi ganda (43.11) masing-masing satu yang bertahan
- [x] Semua jalan masuk 43.4.4 menuju `Tujuan`; tidak ada layar anak
- [ ] Di Sini: konteks, prompt, jembatan K/Abadi/Dimensi
- [ ] Pengaturan menyerap Langit, duel, peringatan; Kontrol rebind; K/N/J bawaan
- [ ] HUD lima slot, satu gaya
- [ ] Perbaikan R1-R33
- [ ] Server hanya S1-S3
- [x] Lang `gui43*` dua bahasa identik; tanpa kunci ganda
