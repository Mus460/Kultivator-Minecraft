# sekte:design progress (LEAD DESIGNER, DESIGN 36 "Sekte yang Hidup")

Run 1 (2026-10-22): audit + SekteIds/SekteConfig/SekteCommands. Run 2 (2026-10-22): everything else. STATUS: DONE.

## Checklist
- [x] 1 Audit (DESIGN 36.1 table + per-idea status)
- [x] 2 Package dev.kultivator.sekte: SekteContent (umum config, on(module), 10 commented module lines),
      SekteIds (+EXTRA_G/T/MEMORY), SekteConfig, SekteCommands, Ingatan (memory ring), SekteLog; client
      client/sekte/SekteContentClient (10 commented lines), SektePoses (act 13-24 pose/prop hook)
- [x] 3 API sekte/api: SekteApi (10 services, NONE defaults, liveModules), SekteBus (19 events), enums Sifat Ikatan Jabatan
      Sikap Perlakuan Kepergian AksiSekte, records Lambang Kenangan MisiHasil TurnamenHasil, 10 *Service interfaces
- [x] 3b Hooks in existing files (lead is sole owner): Kultivator.java (LF, 1 line), KultivatorClient.java (CRLF kept,
      1 line), CheatHelp (group perguruan), GuideChapters (CHAPTER_COUNT 16, ch15/16 x10 steps adv:sekte/*),
      DiscipleEntity (act 13-20 hold/save, only 13 sits), DutyPoses + DiscipleHeldItemLayer (SektePoses),
      DiscipleScreen.addExtraTab (max 2), TemplateSpec.Builder (template(), footprintBounds(), zoneSoFar(), hasComponent(),
      build() runs TemplateExtensions), new sect/build/blueprint/TemplateExtensions, EpicIds EXTRA_SEKTE_FIRST/LAST 100-199
      + maxBeat(EXTRA)=199, SceneCatalogCombat.extra default -> new client/epic/SekteScenes (Recipe API)
- [x] 3c Lang: lang-src/sekte.{id_id,en_us}.json (83 keys), sekte_panduan.* (44 keys), generator tools/gen_sekte_lang.py;
      no duplicate keys with other fragments
- [x] 4 DESIGN.md appended "## 36. Sekte yang Hidup" (lines ~22648-23420, LF): 36.0 rules, 36.1 audit, 36.2 ten designs,
      36.3 shared core, 36.4 exactly 10 modules P R T G A E X S D W, 36.5 Sumber, 36.6 voices, 36.7 final tests
- [x] 5 Self check: all touched files keep line endings (only KultivatorClient CRLF); javac 25 clean for every touched
      source (scratchpad/sektejc/jc.sh, logs log..log4.txt, 0 errors); no Gradle run

## Module file owners of existing files (DESIGN 36.4)
T: sect/event/Tournament.java, duel/session/DuelResults.java (+new duel/api/DuelEndListeners). G: sect/hall/Candidates.java.
E: disciple/path/Heir.java, sect/hall/SectRanks.java. X: sect/event/Expeditions.java, client/sectevent/MissionPage.java.
S: client/disciple/DiscipleRobeLayer.java. D: musuh/SectRaids.java (CRLF). W: sect/event/Duties.java. P R A: none.

## Sumber notes
Verified (run 1 web): BTTH Han Feng, JttW ch.2 Subhuti, Mt Tai 18 Bends ~1,600 steps, Sweeper Monk (DGSD), Huineng robe+bowl,
RMJ Seven Sects vs Devil Dao, BTTH 3-year agreement, RI Li Muwan. Run 2 NOT verified (not cited): Shaolin Discipline Hall,
Mount Hua plum emblem, spirit-stone-mine plots, RMHS "Hyun Young". Used instead: Baizhang Qinggui, Eight Banners colours,
Nuwa stones (Huainanzi 6), Shanhaijing jade mountains, lei tai, Qingming.

## Open problems / for modules
- Plot rules per module in DESIGN 36.3.5; modules must verify with uji sekte audit/pamer in the finish step.
- Guide texts are lead-owned (sekte_panduan); modules request text changes in their reports.

## Next step
None (job finished). Finish step: Gradle compileJava/compileClientJava/jar.
