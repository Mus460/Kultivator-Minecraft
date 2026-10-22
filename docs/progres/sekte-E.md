# sekte:E — Tetua & Pewaris (DESIGN 36.2.6, modul E)

Status: SELESAI (run 1, 2026-10-22). javac 25 server+klien 0 error (scratchpad/sekteE/jc.sh, log4.txt). Tanpa Gradle.

## Checklist
- [x] 1. Audit inti bersama + Heir/SectRanks/Halls/Candidates/Storehouse/EpicHooks/SekteScenes/HubCards/FormModels
- [x] 2. Server sekte/tetua: TetuaConfig, TetuaData (seksi aula sekte_tetua + extra.jabatan), JabatanTetua (syarat, wakil,
      officeFactor, angkat/copot, meja per jangkar), Tetua (init, TetuaService, bus, tick20, server tick, status line aula,
      terimaOtomatis lewat Halls.handleAction OP_ACCEPT)
- [x] 3. UrusanHarian (6 urusan, 1/120 s/aula, giliran adil, baris "sepi" hanya ke laporan) + LaporanPagi (buffer SECT_LOG,
      LOYALTY_CHANGED, WORK_DONE; laporan <=12 baris/3 per modul; Tetua Agung/Ketua berjalan <=20 blok, beat 151)
- [x] 4. PenyerahanTakhta (90 s, baris <=barisanMaks, ACT_OFFICE + payload Ceremony utk pose, batal sebelum puncak bila
      bahaya/menjauh, puncak 60 s beat 150) + Generasi (pewaris dari HEIR_NAMED/tag lama, TIDAK_ADIL sekali, Ketua generasi
      ke-N nama emas, office +0.05, Leluhur Pendiri renown x1.10 via addRenown, +10 standing sekte NPC, silsilah)
- [x] 5. TetuaPayloads (Balai, Report, Ceremony, Act), BalaiTetua (view + aksi), TetuaCommands (uji perguruan tetua angkat/
      copot/laporan/urusan/takhta/lewati/generasi; perguruan tetua), advancement sekte/tetua_jabatan + tetua_penerus
- [x] 6. File lama: Heir.java (HEIR_NAMED + helper parseSoul), SectRanks.java (passedOver TIDAK_ADIL saat promosi
      >= Dalam, DIPERMALUKAN saat turun; dijaga SekteContent.on(E))
- [x] 7. Klien client/sekte/tetua: TetuaClient (receiver, hub SEKTE "Balai Tetua", HUD, cincin qi Normal/Maks),
      BalaiTetuaScreen (Jabatan/Laporan/Penerus), LaporanCard (HUD gulungan), TetuaPoses (17 meja/upacara, 23 hormat),
      adegan 150/151/152; aset tools/gen_sekte_tetua_assets.py (dicek Read)
- [x] 8. Lang sekte_tetua.{id_id,en_us}.json 163 kunci identik (tools/gen_sekte_tetua_lang.py)
- [x] 9. Wire satu baris di SekteContent dan SekteContentClient
- [x] 10. Cek: javac 0 error, server tanpa kelas klien, semua file LF (sama dgn aslinya), kunci lang lengkap

## Langkah berikut
Tidak ada (tugas modul selesai). Langkah finish: Gradle + uji DESIGN 36.7 butir 12.

## Untuk finish / modul lain
- Suara baru diusulkan: disciple.<v>.report (m1-m3/f1-f3) "Ketua, laporan pagi ini." 「宗主、今朝のご報告です。」
  (kode memakai bila ada, jika tidak greet).
- Ketua Generasi Kedua memimpin turnamen/serbuan saat pemain tak ada: T/D bisa membaca SekteApi.tetua().heir(owner)
  (setelah suksesi = Ketua); E hanya menerima calon (terimaOtomatis).
- Syarat bintang >= 9 mengikuti DESIGN (DisciplePaths.stars = Bintang Abadi, baru setelah True Immortal Peak) — sangat
  tinggi; uji pakai "uji perguruan tetua takhta <nama>".
- officeFactor rumus: Dao Union = 1.20 (komentar DESIGN menulis 1.25 untuk Dao Union; rumus menang).
