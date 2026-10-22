# fix:M1 "Satu Jalan Panduan" - progres

Rencana: docs/progres/audit-integrasi.md bagian 3, M1 (T1, T2, T8-langkah, S5, S34, R1, R2, R4, R6, R29).
CRLF (pertahankan): README.md, docs/PANDUAN.md, lang-src/goals.id_id.json, lang-src/jalan8.*, client/goals/GuideTracker.java.
Lainnya LF. Cek akhir baris dengan `git ls-files --eol <berkas>` (harus w/crlf atau w/lf, bukan mixed).

## Keputusan desain (sudah ditetapkan, jangan diubah lagi)
- GuideChapters.Step dapat `optional`; `core()` = !manual && !optional. Chapter dapat minRealm, pastRealm, requires (cek id).
- DISPLAY_ORDER (indeks simpan 0-based): 0,1,12,2,3,4,14,5,15,6,7,8,13,9,10,11. Bab 17 (37) = indeks 16, sisipkan setelah 8.
- pastRealm = heuristik: setelah ranah itu bab tidak lagi menahan "Bab sekarang" (langkah tetap terbuka). Server kirim
  `current` = indeks simpan; klien (GuideBook) memetakan ke posisi tampil. "Bab N" = posisi tampil.
- Pindah langkah (id baru, cek tetap): c14.menara->c13.menara, c13.kapal->c14.kapal, c14.evolusi->c7.evolusi,
  c14.kota_abadi->c11.kota_abadi, c14.puncak->c11.puncak (optional). Bab 13 = 10, bab 14 = 10, bab 7 = 10, bab 11 = 10.
- Pelacak: mode abadi (TI Puncak saja) > world (Langkah bab 1-4 + Fondasi) > Langkah 1-5 (lewati sect/disciple/expedition
  selama bit sect belum) > Kitab (langkah inti berikut). Segel diberi label Langkah/Kitab, offset :190 diperbaiki.
- S5: Guide.currentStepAbadi: selama a_realm belum, langkah = a_calamity (bila belum) lalu a_realm.
- R4: tukar barang khusus bab 8/9 (bab 8 = Tinggi 1 + Tomb Sigil, bab 9 = Agung 1 + Darah Dewa Kuno); tangga batu tetap.

## Checklist
- [x] 1 GuideChapters: Step.optional, Chapter gates, DISPLAY_ORDER, pindah langkah, currentChapter murni (guide/GuideChapters.java)
- [x] 2 GuideProgress: currentChapter(done, player, data) pakai gerbang ranah/aula; uji panduan info pakai number()
- [x] 3 Klien: GuideBook (urutan tampil, posisi, optional, positionOf), GuideClient (current -> posisi, nextStep), GuideScreen (firstOpen inti, tooltip pilihan/ranah/aula; kunci baru guide_client chapter.realm, chapter.hall, mark.optional ditulis di 6)
- [x] 4 Guide.java: sect = sect_gate_open|first_recruit_hall, currentStepAbadi, hermitSkip/SECT_MASK, currentStepAny/ChapterAny, R4 (case 7/8)
- [x] 5 GuideTracker (CRLF): mode NONE/LANGKAH/WORLD/ABADI/KITAB, record View, label segel, nomor segel = judul. Kunci baru goals: tracker.chapter_langkah, chapter_kitab, label_langkah, label_kitab (ditulis di 6)
- [x] 6 Lang: guide_client (chapter.realm/hall, mark.optional), dunia_panduan (5 id dipindah + desc 13/14), goals (step.sect(.how), steps.tip, help.jalan, tracker.chapter dihapus -> chapter_langkah/kitab, label_langkah/kitab), jalan8 (reward 8/9 + help), jalan9 (help urutan S5), guide (R1 c7.strengthen, R2+R29 c11.stars, R6 168, c1.hud, c12.help). Paritas kunci OK; 153 langkah punya teks (duel di duel_ui). Alat: scratchpad/langedit.py lineedit (menjaga baris kosong/CRLF)
- [x] 7 PANDUAN (CRLF; alat scratchpad/docedit.py rep(): ganti persis, LF-normal, tulis ulang CRLF) + README
  - [x] 7a bab 62 (16 bab urutan tampil, 153/112 otomatis/94 inti/28 pilihan, gerbang ranah/aula, Bab sekarang)
  - [x] 7b 2.1 hub (kartu 34-36, sub-koin Aula/Perguruan) + tabel tombol (Peta, Tangkis, Kamera duel)
  - [x] 7c Cincin Segel -> Rangkai Segel (244,1034,1059,1076,1843,6044,6491, README 58/133); kalimat :221 (U9)
  - [x] 7d 42.1 pelacak (mode, sekte dilewati, Kitab), langkah sect; 55 (6912,7247) bab 8/9 hadiah tukar; 56.10 urutan S5
  - [x] 7e 44.2 R1, 44.4 R2/R29 (+ bencana pemain ada), 169/168 (R6)
  - [x] 7f 52 tabel 5 berkas config + kultivator-duel.json + kunci klien epic/voice/duel; README Konfigurasi
  - [x] 7g bab 67 "Perubahan keseimbangan (audit integrasi)" dari catatan M2/M3/M4/M6 (+R4 M1) + TOC 66/67; README bullet
- [x] 8 Swa-cek: paritas kunci 7 fragmen OK, 153 langkah + 16 bab punya teks id/en, akhir baris = HEAD, server tanpa kelas klien, API (Language.getOrDefault, MutableComponent.append) dicek di mcsrc, tautan anchor PANDUAN/README OK, uji terima ditelusuri di kode
- [x] 9 R7 (permintaan M4): GuideBook.body() menambah kultivator.kekuatan.guide.ia8 di bawah kartu c10.layer9 (GuideScreen memakai GuideBook.body)

## Langkah berikut
SELESAI. Tidak menjalankan Gradle (finish agent build). PANDUAN 67 = catatan keseimbangan M1-M6 (diverifikasi dari kode
di disk saat ini; modul yang masih berjalan bisa menambah angka -> finish agent/M1 menambah di 67). Lang ekstra sudah: goals daily (hadiah per ranah M3), guide c8.seat/c10.layer5 (M4 T6/S6/S7/R7), c11.calamity (R3), c3.quality (S3).

## Masalah terbuka
- dunia_peta.id_id.json:179 "Bab 13 dan 14 Kitab Panduan" (bukan milik M1) -> needsFromOthers.
- screen.id_id.json:170 sudah diperbaiki M5 (tidak perlu lagi).
- Payloads.GuideSync javadoc `current` masih "first unfinished step" (hanya komentar; berkas bukan milik M1).
