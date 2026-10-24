# F3 finish of wave W3 (38/39/40) — checkpoint

Schedule: docs/progres/jadwal-38-39-40.md section 4 (F3). Record file: this one.

## Checklist
- [x] 1. Read W3 module reports (16, passed in the F3 prompt)
- [x] 2. 22 generators changed since F2 re-run (scratchpad/f3/gen.sh, logs f3/*.log), all exit 0; md5 of 9035 files in
      resources + lang-src before/after: 0 differences (W3 outputs on disk match their generators). kompat_bioma 53/71
- [x] 3. cek_tempat 717 tpl / 82 tata_letak / 105 sets, 0 errors (only known canal-edge warnings); gzip gua 694 KB of
      760 KB, situs 128 of 240; cek_sosok 351 sheets 0/0
- [x] 4. Wire needsFromOthers
  - [x] DuelSeteru.selesai: live home-duel loser gets MandiriApi.lukai(TERLUKA, 1 day, "seteru") (D-39/D-39F)
  - [x] cek_tempat FOLDERS gua 760_000 / situs 240_000 (D-GU, 38.C.10 shared 1 MB)
  - [x] DuniaCommands.CHIPS += "ekonomi simulasi semua <hari>" (D-EK)
  - [x] Dagang.bea "giok": free only with Bea.suratSah (expired passes pay) (D-TK2); TemaItems import dropped
  - [x] checked, nothing to do: tanding_klan/tanding_sekte used by D-ST dialogs; GerobakEntity.setMuatan called by
        KafilahJalan; oasis towns have no GerbangKotaEntity (BadaiPasir closes Gerbang Giok itself, JamMalam hook moot);
        capital boards: model front -z, renderer 180-yaw, yaw 90*putar = canonical south rotated cw -> faces into hall
  - [x] review fix: JamMalam GENDERANG 22000 -> 16000 (22.00 on the tick-0 = 06.00 clock of Jadwal/KotaBesarKalender;
        22000 was 04.00, so gates shut only 04.07-07.00 while NPC drummers beat at 22.00), TUTUP 16120; docs in
        GerbangKotaEntity/KotaBesarConfig. NEEDS REBUILD (B3 attempt 2)
  - [ ] left for next wave: RakSenjata smithy sync (needs new S2C payload with rack positions), CandidateSpec name
        override, tamu_sekte advancement, LembarKunci.TAB pasar, Glyphs crossed swords, SAHABAT_LUAR, larang
- [x] 5. Build B3 green attempt 1 (4m50s), mergeLang no dups, lang fragments 0 key mismatches; attempt 2 after the
      JamMalam fix and wiring: BUILD SUCCESSFUL 3m19s
- [x] 6. Harness (logs scratchpad/f3/run_*.log): run_38.sh TplCheck 717/0 (all 8 folders 0), WgCheck38 157 structures /
      105 sets ALL OK, PLAN OK, TEMA PLAN OK (3 seeds); run_all.sh TplCheck 1087/0, ALL OK; langitG run_fin.sh 7/0,
      ALL OK, 2 palaces x 4 seeds
- [x] 7. Hard rules: block writers only WorldBreaker (7) + BuildPlacer (8), none in src/client; LightningBolt only as
      instanceof imports (Hidup, RuangUdara); no weather/daytime/tick-rate; src/main imports no client class; no static
      ItemStack fields; 336 tracked modified files keep their EOL (only binary false hits), untracked text files all LF
- [x] 8. Docs ([x] DESIGN.md 38/39/40 summaries appended LF; [x] rancangan/40 40.5.7 waist points + TEGAK; [x] PANDUAN 69-72 + TOC + 62 table (21 bab/198 langkah) + bab refs
      renumbered (CRLF kept); [x] README guide line + 4 feature bullets + status rows 38/39/40 (CRLF); [x] FASE-38/39/40 final
      (W3 items + Belum lists); [x] mega-voices.md 159 lines (all registered keys w/o clip have a gen_voices line; client
      hides missing captions via Language.has); [x] schedule checklist): DESIGN.md 38/39/40 (LF), PANDUAN.md chapters (CRLF), README line, FASE-38/39/40 final, mega-voices.md,
      schedule checklist
- [x] 9. Cleanup: no javac.*.args; build-log.txt deleted; edited files keep EOL (PANDUAN/README CRLF, rest LF)

## Status
DONE. B3 green (attempt 2). Nothing committed (orchestrator decides; all of textures/sosok/ is still untracked).

## For the next wave
- Voices: run gen_voices.py with VOICEVOX for the 159 lines in mega-voices.md (needs user OK for the download).
- Open wiring: RakSenjata smithy sync (new S2C payload + rack anchors), CandidateSpec name override (eloping lover),
  tamu_sekte advancement JSON + grant, LembarKunci.TAB 'pasar' in DagangLayar.bukaPasar, KebijakanAula.larang,
  Glyphs crossed swords, SAHABAT_LUAR tokoh API, themed-city roles without dialog, kompleks menara/dermaga_awan detail,
  40K drape for extras/head cloth/footwear, black-hair shine (Python + RupaResolver parity), lari_riang.
- Nothing tested in game (no runClient): board facing, festival act spacing, compass needle, seated props, GUI scales.

## Open problems
