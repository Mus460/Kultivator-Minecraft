# langit:finish — final pass DESIGN 37 (Sekte Langit)

Inputs: docs/progres/langit-{design,37B,37M,37V,37G}.md; docs/rancangan/37-sekte-langit.md; audit-integrasi.md sec 4.

## Checklist
- [x] 1 Build #1 (Gradle /low variant): BUILD SUCCESSFUL 2m03s first try, lang merge clean
- [x] 2 Wire needsFromOthers (37B events/HallBridge/anchors, 37M init/advancements/feri/uji/guest rule, 37V Pemandangan
      button/onSaw halo/test ops/sengsara fx, 37G), lang conflicts/missing keys, JSON/NBT validity
- [x] 3 Worldgen harness scratchpad langitG/wg/run_fin.sh (Gradle classes): TplCheck 7/0, WgCheckL ALL OK (biomes=56,
      2 sets), PlanCheckL 2 palaces/seed, restrict ok
- [x] 4 Self-review (BuildPlacer-only, fall protection, ground options, save compat, tickets, block grep, no
      LightningBolt/weather/time, render nulls, lazy ItemStacks, payload sizes, budgets) + rebuild
- [x] 5 Docs: DESIGN.md sec 37 appended (LF) + 37.12 perubahan saat implementasi; PANDUAN.md ch 68 Sekte Langit
      (CRLF kept) + TOC + hub row + ch 62 table (17 bab/164 langkah, Sekte Langit = bab 13); README feature line + row 37
- [x] 6 docs/progres/langit-voices.md (7 lines: heaven.langit_naik + 6 sekte_langit.*)
- [x] 7 Cleanup: build-log.txt deleted, no javac.*.args present

## Done in 2
- LangitClient: 37M LangitGerakClient.init() line was still commented (ferry had no renderer) -> enabled
- LangitTests: + `langit_sekte patroli` / `tangga` (GerakUji) + chips
- LangitSyncServer: anchor cap deterministic (priority order then id) instead of HashMap order
- Verified wired: LangitEvents fired by BuildEngine/LangitBangun; HallBridge defers to LangitTangga.arrival; onSaw halo;
  test ops 100/101; FX_SENGSARA_BAWAH; FX_PILAR (LangitAngkat); addGuestRule; feri command; Pemandangan button; 5 37M advs
- lang: no missing literal/dynamic keys; 53 JSON valid; advancement translate keys exist

## Done in 4 (self-review)
- block/weather/time/tick grep clean (RuangUdara only makes vanilla bolts visual-only); ItemStacks only local; no client
  refs in main; mixins in right json; Site NBT short keys with defaults; ground path unchanged (validAny); tickets only
  DuniaTickets.touch; Tantang via HeavenlyWrath.challenge (javadoc moved back above challenge, CRLF kept)
- LangitJatuh: CAUGHT/LAST_GROUND pruned every 6000 ticks; catch never kills (slow fall + grace, no spot -> slow fall)
- line endings verified with python (all modified = HEAD mode, all new LF)

- Rebuild #2: BUILD SUCCESSFUL 1m23s

## Next step
None. Final pass DONE (no source change after rebuild #2; jar build/libs/kultivator-0.1.0.jar 2026-10-23 03:49).

## Open problems
- Nothing tested in game (37.11 list open). Not built: pijakan awan collider, feriMisi, TanggaService path hook (36 G),
  36 elder greeting sky guests. Voice clips pending (langit-voices.md).
