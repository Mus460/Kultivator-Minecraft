# mega-39B (W1, DESIGN 39B Tumbuh Sendiri)

Scope: jadwal-38-39-40.md W1 row 39B + section 5 note; design 39.5 (39.5.1-39.5.9), 39.12 module 39B, 39.4.2 rows owned
by 39B (TUTUP_DIRI 45, MERENUNG 53, SERAP_BATU 54, JAGA_TEROBOSAN 55, BELAJAR 13, MENGAJAR 44, TEROBOSAN).
Owns old files: `disciple/{DiscipleCultivation,DiscipleBreakthrough}.java`, `sect/hall/SectRanks.java`.
New: `sekte/mandiri/tumbuh/**`, `sect/hall/KontribusiMurid.java`, `sekte/paviliun/PaviliunMandiri.java`,
`lang-src/sekte_mandiri_tumbuh.*`, advancements `sekte/mandiri_tembus`, `sekte/mandiri_abadi`. No Gradle.

## Checklist
- [x] Read schedule, design 39.0-39.13 (39B parts), 39A-0/39A progress
- [x] Explore: mandiri api, DiscipleCultivation (CRLF!), DiscipleBreakthrough (LF), SectRanks (LF), DiscipleHooks,
      Contribution, RakKitab, BelajarMandiri, Halls (seat counts need online master), SibukMurid, MandiriCommands
- [x] Old-file additions: DiscipleCultivation (CRLF kept): meditationBaseTanpaMode, addXpKegiatan,
      breakthroughBlockerSelf(entity, faktorPenjaga), tribulationDoomedSelf, startBreakthroughSelf, feedSelf, chooseDaoSelf,
      chooseDewaSelf, teachBlockerSelf, learnSelf; DiscipleBreakthrough.start master @Nullable; SectRanks: offline
      elderTeaches (policy not Patuh, SekteLog + diary), requirementsSelf, promoteBlockerSelf, nextRank, promoteSelf,
      promoteByMaster, onlyMasterLinesSelf, unmetSelf
- [x] Bridges sect/hall/KontribusiMurid (own PK, old prices, 1 slip/day/disciple, into bag), sekte/paviliun/PaviliunMandiri
      (mintaBaca rack forced start; bacaTas/langkahTas bag reading pose 53; Setia deposits slip to rack; belajarKejar for the catch-up)
- [x] tumbuh/* classes: [x] DataTumbuh [x] KabarTumbuh [x] TempatTumbuh [x] LajuMandiri [x] HuFaSahabat
      [x] Persiapan [x] UjianHati [x] TerobosanSendiri [x] PilSendiri [x] TutupDiri [x] Merenung [x] SerapBatu
      [x] BelajarSendiri [x] AjarSenior [x] Pencerahan [x] DaoSendiri [x] DewaSendiri [x] JenjangSendiri [x] Proyeksi
      [x] TumbuhCommands [x] Tumbuh (init, tick20, butuh, Kebutuhan, kejar)
- [x] lang `lang-src/sekte_mandiri_tumbuh.{id_id,en_us}.json` (110 keys, identical, no duplicates; generator
      scratchpad/m39b/lang.py) + advancements `sekte/mandiri_tembus.json` (goal), `sekte/mandiri_abadi.json` (challenge)
- [x] Self-review: javac 25 type-check of all 39B sources + owned old files against B0 classes (cp.win,
      -sourcepath src/main/java, -implicit:none, out in scratchpad/m39b/out): 0 errors. LF kept (DiscipleCultivation
      CRLF). Sounds verified in mcsrc (BELL_RESONATE, ENCHANTMENT_TABLE_USE, AMETHYST_BLOCK_RESONATE, BEACON_POWER_SELECT);
      TranslatableContents.getKey verified. No Gradle run.

## Decisions
- Writes to 39B-owned schema keys (`tutup`, `tunggu`, `statistik`, `luka` read) go through `MandiriApi.bagianTulis`
  with `MandiriIds` keys (schema unchanged). Own state in a separate `extra.mandiri_tumbuh` (not inside the 39A schema).
- Bag reading (`PaviliunMandiri.bacaTas`) uses ACT_MERENUNG 53 (jade slip pressed to the forehead), own points keys;
  rack reading reuses `BelajarMandiri` (forced start).
- Self promotion: targets needing seat counts (Tetua, Tetua Agung) and the Luar->Dalam trial wait for the master online.
- Files: tumbuh/{Tumbuh, LajuMandiri, Proyeksi, Persiapan, TerobosanSendiri, UjianHati, HuFaSahabat, DaoSendiri,
  BelajarSendiri, Pencerahan, AjarSenior, PilSendiri, SerapBatu, TutupDiri, Merenung, JenjangSendiri, DewaSendiri,
  KabarTumbuh, TempatTumbuh, DataTumbuh, TumbuhCommands}.
- Executors do cheap checks; the per-second work runs from Tumbuh's DiscipleHooks.addTick20 (planner cadence unknown).
- Diary args convention: `t:<spec>`, `r:<realm>`, `d:<dao>`, `i:<item>`, `n:<number>`, plain = literal name.

## Langkah berikutnya
DONE (session 3). AjarSenior's junior listens in a planner MEDITASI block (56) at the seat in front of the teacher
(BELAJAR's catalogue act 13 would be cancelled by Perencana.harusBatal). Open: see "Butuh dari modul lain".

## Butuh dari modul lain
- 39A (blocker for bag reading): `Kegiatan.BELAJAR` act 13 -> 0 (executor chooses: 13 rack, 53 slip from the bag,
  56 listening), else `Perencana.harusBatal` ends a bag reading after 40 ticks (act 53 != 13).
- 39A or 39C: C39.B4 is `dev.kultivator.sekte.mandiri.tumbuh.Tumbuh.butuh(DiscipleEntity|DiscipleData[, registries])`
  -> `List<Tumbuh.Kebutuhan(item, jumlah, prio 1-3, sebab)>`; 39C calls it (or 39A adds a MandiriApi hook).
- 39E: diary core keys `kultivator.sekte.mandiri.harian.<kunci>.<n>` for 39B kunci: tembus.{siap,tergesa,berhasil,
  gagal,urung,batal_tempur,batal_lemah,tunda_belanja}, hati.{lulus,gagal}, hufa.jaga_berhasil, tutup.{masuk,keluar,
  terganggu}, renung.selesai, serap.mulai, belajar.{tas_mulai,tas_selesai,pencerahan,kejar}, ajar.{diajar,mengajar,
  tetua}, dao.{bimbang,upacara}, dewa.pilih, jenjang.{mohon,naik,disetujui_tetua,ditunda}, ambisi.{tanding,bencana},
  pil.{tas,lumbung}, beli.{slip,paviliun}. Args convention t:/r:/d:/i:/n:, plain = literal name.
- 39E voices (fallbacks in place): 39.8.4 ids used: tutup_masuk, tutup_keluar, hufa_minta, hufa_jaga, tembus_sendiri,
  hati_lulus, dao_pilih, belajar_sendiri, serap_batu; extra optional: ajar_panggil, ajar_mulai, ajar_terima, beli_slip,
  dewa_sendiri, jenjang_naik, jenjang_setuju, tanding_guru. Guide c21.tembus/abadi -> adv sekte/mandiri_tembus|abadi.
