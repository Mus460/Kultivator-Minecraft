# mega-39C (W2, DESIGN 39C Perjalanan & Kota + C39.C3 engine)

Scope: jadwal-38-39-40.md W2 row 39C + section 5 note; design 39.6 (all), 39.12 module 39C, 39.F.2/39.F.9 (C39.C3).
Owns: new `sekte/mandiri/jalan/**`, `lang-src/sekte_mandiri_jalan.*`, advancement `sekte/mandiri_belanja`, new
`data/kultivator/tempat/kabar/mandiri.json` (C39.K8 templates); old file only `dunia/DuniaMarkers.java` (one put).
Must not touch: `dunia/kota/**`, `dunia/tempat/**` code, `Disciples`, `Halls`, other 39 packages. No Gradle.

## Decisions
- Away storage: `Halls.storeAway(reason mandiri_jalan)`; the live record is moved like `MisiData.moveReturn`
  (`Halls.section(owner,"core").away[].return_at`) for early recall ([Ikut Guru pulang]), captivity and 39F stage
  length. Authoritative journey state in own SavedData `kultivator:mandiri_jalan` (`JalanData`), mirror for 39E in
  `MandiriData.jalan.<soul>` (`MandiriApi.aula`). Stages are lazy and seeded (`soul ^ berangkat ^ index`).
- Results applied in the return handler to the stored tablet data (purse via `ubahDompet`, bag via `masukTas`,
  wound), stacks made there (lazy, 39.6.11 rule 2); visible deliveries (errands, gifts, report) in the TIBA scene.
- Own schema keys of 39.2.1 (`belanja`, `perlengkapan`, `lencana`, `luka`) written with `MandiriApi.bagianTulis`
  (as 39B did); own state in `extra.mandiri_jalan`.
- Town/person names in stored lines as lang-key args: town = 2 args (`kultivator.kota.nama.N`, `...akhiran.M`), person
  = 2 args (marga, nama_diri_l/p); my sentences order them per language. Stage story strings: `key|arg|arg`.
- `beli_murid` mark: 39D's component looked up by id (`kultivator:beli_murid`), fallback custom_data.

## Checklist
- [x] Read schedule, design 39.0-39.2, 39.6, 39.9-39.12, 39.F.1-2/9, 39A-0/39A/39B progress, 39 api, old APIs, 38 APIs
- [x] DuniaMarkers: `put("mandiri/tawanan", 48, 0xFF4040, 0)` (LF kept)
- [x] JalanIds, JalanData (SavedData), Dompet (Kas, earnings cap, deposits), Tas (bag helpers, beli mark)
- [x] PasarBawaan (C39.K1), Tawar, GildaBawaan (C39.K4), Perlengkapan (damage hook, gear tiers)
- [x] Tujuan (destinations cache, locate once/day), DaftarBelanja (morning list from Tumbuh.butuh + needs), KontrakKota, Watak
- [x] Rencana, Tahap (engine + C39.C3), PeristiwaJalan, KunjunganKota, MasalahKota, Kesempatan, Rampasan
- [x] Rombongan (party), Tawanan (captives, live site, rescue/ransom), Hadiah (gifts)
- [x] Jelmaan (town/road/captive bodies) + JelmaanJalanGoal + JelmaanPoseGoal (goal installer prio 3/4)
- [x] Kepulangan (return handler, arrival, report), PelaksanaJalan (BERKEMAS/BERANGKAT/TIBA/PERJALANAN executors)
- [x] KontrakKota (38 wrappers), JalanCommands (uji kirim/percepat/jelmaan/perjalanan/tawan + player jelmaan 6 aksi);
      PelaksanaJalan.paksa (test choice); Tahap.namaSekte + misi.umum (story args always present)
- [x] Perjalanan: rencana, berangkat, majukanKe, sword cap, init wiring (pasar/gilda/kas/pergi/relawan, return handler,
      goal installer, UseEntityCallback early phase, tick20 arrival+morning, server tick engine/jelmaan/cleanup)
- [x] lang sekte_mandiri_jalan: scratchpad/m39c/{part1,part2,part3,lang.py} -> 495 keys, checks keys used by code,
      placeholders, identical sets; advancement sekte/mandiri_belanja; tempat/kabar/mandiri.json (copet/tolong/duel x3)
- [x] fixes: memory arg "" (was a seed number), beast core/slip item keys in kunciBarang, kabar args + duel rumour
- [x] Self-review: javac 25 type-check of the whole package against build/classes (scratchpad/m39c/tc.sh): 0 errors
      (fixed PasarBawaan TEH/HADIAH arrays shadowing PasarMurid constants); orphan clean-up keeps a leader record while a
      member is out; all files LF; no duplicate lang keys across fragments

## Langkah berikutnya
DONE (session 3). Only the F2 finish build and in-game checks remain (see below).

## Butuh dari modul lain
- F2: Gradle `compileJava compileClientJava jar` (39C type-checks clean against the B1 classes; other W2 changes unseen).
- F2/tester: in game `uji perguruan mandiri kirim <murid> belanja`, `percepat`, `jelmaan`, `tawan`, then the town body's
  chat buttons; nothing of 39C has run in a world yet.
- 39E: guide step `c21.belanja` can grant/read `adv:sekte/mandiri_belanja` (file now exists, parent sekte/mandiri_jalan).
- 39D: `kultivator:beli_murid` is looked up by id in `Tas.tandaiBeli` (custom_data fallback when absent).
- Note: `DaftarBelanja` reads 39B `Tumbuh.butuh` directly (contract C39.B4, guarded by try/catch LinkageError).
