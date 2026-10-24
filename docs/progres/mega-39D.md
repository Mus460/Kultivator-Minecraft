# mega-39D (W2, DESIGN 39D Kerja, Kerajinan & Pergaulan)

Scope: jadwal-38-39-40.md W2 row 39D + section 5 note; design 39.4.2 rows LATIH_JURUS 40, TANDING 43, MERAMU 41,
JIMAT 42, KEBUN 16, TAMBANG 15, POS_TUGAS 1-4, JAGA_FORMASI 11, SOSIAL/TEH 18; 39.6.7 (NatalTreasure self), 39.6.8,
39.12 module 39D (C39.D1, C39.D2).
Owns old files: `sect/event/Duties.java`, `formasi/keeper/Keepers.java`, `disciple/power/NatalTreasure.java`.
New: `sekte/mandiri/kerja/**`, bridges `sekte/kebun/KebunMandiri.java`, `sekte/hubungan/HubunganMandiri.java`,
`lang-src/sekte_mandiri_kerja.*`. Must not touch other sekte 36 files. No Gradle.

## Checklist
- [x] Read schedule, design 39D parts, 39A/39B/39C progress, mandiri api + planner + PelaksanaDasar pattern
- [x] Explore old APIs: Duties, Keepers, NatalTreasure, Pertumbuhan/GiliranTambang, GiliranSosial, AlchemyRecipes,
      AlchemyFurnace, Talismans, Inscribe, data components registration pattern (all owned old files are LF in git and on disk)
- [x] Old-file additions: Duties (SELF_DUTY, selfDutyOf, selfPostOpen, assignSelf, releaseSelf; compute/release clear
      self_duty, no chat for self posts), Keepers (selfCore, assignSelf, selfKept; master assign clears `self`),
      NatalTreasure (refineBlockerSelf, refineSelf, foldPkSelf, enhanceBlockerSelf, enhanceSelf)
- [x] Bridges sekte/kebun/KebunMandiri, sekte/hubungan/HubunganMandiri, disciple/power/NatalMandiri (public door)
- [x] Data components kultivator:buatan_murid / beli_murid (KerjaKomponen, tooltips)
- [x] kerja/DataKerja (own section), KabarKerja (diary full keys + VARIAN counts, news, notices), Bahan (bag+jatah)
- [x] Executors: [x] LatihJurus [x] Tanding [x] AlkimiaMurid [x] JimatMurid [x] KerjaSekte [x] TehMandiri [x] NatalSendiri
- [x] Kerja.init wiring (executors, tick20 natal, tea tick, stop clear, 3 kejar providers, setDagangan, info)
- [x] KerjaCommands (uji perguruan mandiri kerja info|mulai|natal|bahan|jimat|alkimia)
- [x] javac 25 type-check (MC runtime javac, cp scratchpad/final/wg/cp.win, out scratchpad/m39d/out): 0 errors
- [x] lang sekte_mandiri_kerja (127 keys, identical, args checked, every code key covered; gen scratchpad/m39d/lang.py)
- [x] Self-review (2nd session): javac type-check of my 19 files + Pledge -> 0 errors (out scratchpad/m39d/out); all 47
      callers of Duties/Keepers/NatalTreasure/bridges -> only 4 errors, all in 39C's in-progress jalan/ files (PasarBawaan
      TEH/HADIAH switch vs String[] constant, Perjalanan.rencana missing), none in mine; all files LF; no client refs,
      no block writes, no static ItemStacks; voice lines used exist in sounds/voice/disciple/*

## Decisions
- Own state in `extra.mandiri_kerja` (DataKerja), not in 39A's schema; stats via MandiriApi.tambahStatistik.
- Diary entries use FULL keys of my fragment `kultivator.sekte.mandiri.kerja.harian.<x>.<n>` (variant picked server side).
- Self duty posts write `extra.activity.self_duty` (not `duty`) so W's work streak never counts voluntary work as forced;
  KebunMandiri clears W's `job` flag after a self assignment for the same reason.
- NatalTreasure is package-private: new public bridge `disciple/power/NatalMandiri.java` (same package rule 39.0.7).
- Components: `kultivator:buatan_murid` = record Karya(soul, nama) with tooltip, `kultivator:beli_murid` = UUID (39C parses
  UUID form); tooltips via Fabric ItemComponentTooltipProviderRegistry (common side).

## Langkah berikutnya
DONE. Nothing left for 39D; the W2 finish agent runs Gradle (mergeLang picks up lang-src/sekte_mandiri_kerja.* by glob).

## Butuh dari modul lain
- sekte_hubungan owner: add `kultivator.sekte.hubungan.sebab.mandiri_teh` / `sebab.mandiri_tanding` (and optionally
  `ingat.mandiri_teh` / `ingat.mandiri_tanding` + Hubungan.KNOWN); today they fall back to `sebab.lain` / `ingat.lain`.
- 39C: jalan/PasarBawaan.java:91/98 and Kepulangan/Tawanan (`Perjalanan.rencana`) did not compile at my check (their WIP).
- W2 finish: Gradle + in-game check of `/uji perguruan mandiri kerja ...` and the item tooltips.
