# m2-FIX-sekte (fixer "sekte")

Audits: sekte-hidup, mandiri, gabung, + Jelmaan.perbarui (sekte-aula #5).

## sekte-hidup
- [x] 1 FeriAwanEntity.shouldBeSaved true while a DiscipleEntity rides (super.shouldBeSaved keeps the player-RootVehicle rule)
- [x] 2 Kepulangan: removeSearchable only after MISSION_RETURNED if YangPergi no longer lists the soul
- [x] 3 YangPergi.add prune: non-recoverable (not MINGGAT/MENGEMBARA) entries go first
- [-] 4 langit.feriMisi unread: documented unbuilt feature (langit-finish.md), not a defect; skipped
- [x] 5 Makam.tick: bury only on isDeadOrDying or KILLED removal
- [x] 6 Tangga: ENTITY_LOAD recover hook now in registerContent (unconditional)
- [x] 7 Jelmaan.tunjukkan: no-op when Perjalanan not running (Mandiri off)
- [x] 8 Upacara.clear() from Hubungan SERVER_STOPPED
- [-] 9 bolehPergi design question: leaving is not death; needs user decision

## mandiri
- [x] 1 Perencana.nilai: a running PERJALANAN is never re-judged (like acara)
- [x] 2 Perencana.baruDimuat: KG_MULAI = now before the reload-end akhiri (no breath XP for unloaded time; KejarKetinggalan pays it)
- [x] 3 Jelmaan: tunjukkan gated on Perjalanan.berjalan() (no leak while Mandiri off; feature stays a Mandiri sub-feature); jelmaanPerKota<=0 no longer blocks non-town stages
- [x] 4 PelaksanaJalan.berangkat: leader that did not leave is teleported back to Adegan.asal
- [x] 5 MandiriConfig.bolehGugur doc corrected (doc only)

## gabung
- [x] 1 Promosi registers GabungLayar.daftarAksi(PohonPangkat OP_AJUKAN) -> Promosi.ajukan; LembarPangkat.alasanAjukan opens the button when the trial is the only unmet line and Promosi.mulaiUjian(tujuan); `naik` command left (now opens the tree)
- [x] 2 clip release: BalaiHukum.lepas/lepasKurungan, PemburuSekte.lepasKurung (+ serah later 80), Keluar.upacara later 80, PilihShifu later 60 (GabungPayloads.lepasKlipNanti)
- [-] 3 ward disciples benefits: unbuilt design feature spanning pools/compound defence/Mandiri travel; needs decision (see report)
- [x] 4 GabungPayloads START_TRACKING sends the empty entry too
- [x] 5 TugasKotaMurid.tugaskan: no record unless masihPergi after storeAway

## sekte-aula #5 Jelmaan.perbarui
- [x] done with mandiri #3
