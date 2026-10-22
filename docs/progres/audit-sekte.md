# audit:sekte progress (READ-ONLY audit, area SECT & DISCIPLES + DESIGN 36/37)

Catatan run 2: DESIGN 36 sudah di-commit (40d06e1, sekte-finish DONE); 37 belum ada kode. Suara 36 sedang dibuat di working tree (sounds.json, gen_voices.py belum commit).

## Checklist
- [x] 1 Peta kode: paket sect/**, disciple/**, sekte/**, musuh/SectRaids, villages, formations
- [x] 2 DESIGN 36 + 37 baca, cocokkan dengan kode
- [x] 3 Progression (unlock realm vs fitur) - diverifikasi ulang run 2
- [x] 4 Duplikat (turnamen, lelang, toko, mata uang, teleport, misi) - diverifikasi ulang run 2
- [x] 5 Orphans (item/command/screen/guide)
- [x] 6 Flow/feedback, save/compat
- [x] 7 Tulis laporan docs/progres/audit-sekte-laporan.md

## Next step
SELESAI. Laporan: docs/progres/audit-sekte-laporan.md (K1, T6, S12, R7 = 26 temuan).

## Temuan terverifikasi (run 2)
- Hub SEKTE 16-17 kartu, minimal tinggi 20 -> meluap di 320x240 (HubCardsPage.java:183-256); kartu sekte_* lock=null
- Panduan currentChapter = bab pertama yang belum selesai (GuideProgress.java:126); bab 15/16 sesudah bab 14; c15.penerus butuh 9 bintang murid -> bab 16 terkunci
- Takhta butuh HEIR_STARS 9 (Generasi.java:61) = setelah Dewa Abadi Puncak (Stars.java:37); syarat NASCENT_SOUL kosong
- TANTANG/KETUA -> Utusan offer + AksiSekte.TANTANGAN -> AntarSekte.challenge prompt kedua (Simulasi.java:406-413, Turnamen.java:126-129, Utusan.java:191-199,255-275)
- D peristiwa dari aula lv1 (Simulasi.java:210-253), pickSect semua sekte (307-328), TUNTUT tier*25
- Perang.serangan abaikan hasil callRaid (Perang.java:246-249; SectRaids.java:217-225)
- Elit: Rivals.champion 8 nama statis (Tournament.java:507-510) vs guests D (511-518)
- Storehouse.insert tanpa LumbungOverflow saat offline: ForbiddenGround:160-163, PrimordialArk:132-135
- Aktivitas 13-20 dipulihkan saat load tanpa cek modul (DiscipleEntity.java:1499-1505); Kepribadian tick20 return bila !on() (195-197)
- Defiant hanya dicek modul baru (Kebun 374, TimMisi 204, Tempat 180, Tetua 183, GiliranSosial 230); Expeditions/Envoys/ForbiddenGround/Tournament tidak
- Misi X "beside the existing expedition board" (Misi.java:7-8), 3 'utusan' (Envoys, Aksi UTUSAN D, TemplatMisi.UTUSAN)
- help.sects "/sekte create ... undang murid"; help.turnamen gelanggang prasasti 60 dtk; help.misi tanpa misi cerita; tidak ada topik ensiklopedia 36
- Template 1 tanpa komponen 36 (Kebun t<2, Paviliun/Tangga 2-9, Tribun 6-9)
- Pakta sekutu diskon Contribution 10% (DESIGN 23026) tidak ada di kode
- Suara baru 36 sedang dibuat, kode masih memakai kunci lama (sekte/** VoiceDisciples.line)
- 3 akar perintah: /sekte (Sects), /kultivasi aula, /kultivasi perguruan
