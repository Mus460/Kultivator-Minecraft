# audit:sekte progress (READ-ONLY audit, area SECT & DISCIPLES + DESIGN 36/37)

## Checklist
- [x] 1 Peta kode: paket sect/**, disciple/**, sekte/**, musuh/SectRaids, villages, formations
- [x] 2 DESIGN 36 + 37 baca, cocokkan dengan kode
- [~] 3 Progression (unlock realm vs fitur)
- [~] 4 Duplikat (turnamen, lelang, toko, mata uang, teleport, misi)
- [ ] 5 Orphans (item/command/screen/guide)
- [ ] 6 Flow/feedback, save/compat
- [ ] 7 Tulis laporan docs/progres/audit-sekte-laporan.md

## Next step
Langkah 1.

## Temuan sementara (catatan kerja)
- Hub SEKTE: 8-9 kartu bawaan + 8 kartu sekte_* = 16-17, grid tanpa scroll (HubCardsPage.java:234-256), di 320x240 meluap; kartu sekte_* lock=null (tanpa aula)
- Panduan: bab 15/16 (sekte hidup) diletakkan setelah bab 13/14 (Ujung Dunia, end-game) padahal isinya awal (aula lv1-3)
- Semua 20 adv:sekte/* diberikan kode (ok)
- D TANTANG/KETUA -> dua prompt (Utusan D + chat T), expire D = -5 walau diterima via T (Utusan.java:191-228,269; AntarSekte.java:42-97; Simulasi.java:406-413)
- D event mulai hari 1 aula lv1 (Simulasi.java:209-253), data battle <lv3 (Perang.java:242), SectRaids MIN lv3
- Takhta butuh 9 bintang (Stars = setelah True Immortal Peak) vs syarat ranah Nascent Soul (Generasi.java:60-62,183)
- Turnamen Elit juara tamu memakai 8 nama rival_sect statis (Rivals.java:27-45), bukan sekte NPC D
- Overflow Lumbung tidak seragam (ForbiddenGround:160, PrimordialArk:132 hilang saat offline)
- /sect create "Ketua Sekte" vs aula vs Generasi Ketua; "undang seorang murid" = pemain
- 3 sistem utusan: Envoys (perekrut), D Utusan, X template UTUSAN
- Ensiklopedia: tak ada topik 36; help.sects "Dirikan sekte dengan /sekte create" vs help.aula prasasti; help.turnamen/calon basi
- Aktivitas 13-20 dipulihkan dari save tanpa cek modul (DiscipleEntity.java:1499-1505) -> SULK/MINE macet saat modul mati
- Pakta sekutu: diskon 10% Contribution (DESIGN) tidak ada
- Kepergian: bound() hanya SoulSeal+Heir; Ketua Generasi Kedua / Hu Fa bisa pergi
- Old events (Expeditions/Envoys/ForbiddenGround/VoidRealm) tidak cek defiant; Misi/Kebun cek
